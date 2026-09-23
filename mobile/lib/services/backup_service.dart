import 'dart:convert';
import 'dart:isolate';
import 'dart:typed_data';
import 'package:cryptography/cryptography.dart';
import 'package:uuid/uuid.dart';
import '../data/passport_store.dart';
import '../domain/asset.dart';

class BackupService {
  static const maxBytes = 64 * 1024 * 1024;
  static const iterations = 600000;
  static const tables = ['assets', 'documents', 'events', 'reminders'];
  final cipher = AesGcm.with256bits();
  Future<SecretKey> _key(String password, List<int> salt) async {
    // Password derivation is deliberately expensive; keep the UI responsive.
    final bytes = await Isolate.run(() async {
      final key = await Pbkdf2(
        macAlgorithm: Hmac.sha256(),
        iterations: iterations,
        bits: 256,
      ).deriveKey(secretKey: SecretKey(utf8.encode(password)), nonce: salt);
      return key.extractBytes();
    });
    return SecretKey(bytes);
  }

  Future<Uint8List> export(PassportStore store, String password) async {
    if (password.length < 12) throw const StoreException('passwordShort');
    final rows = await store.db.transaction(
      (txn) async => {
        for (final table in tables) table: await txn.query(table),
      },
    );
    final names = <String>{
      ...rows['assets']!.map((r) => r['photo']).whereType<String>(),
      ...rows['documents']!.map((r) => r['file'] as String),
    };
    final files = <String, String>{};
    var total = 0;
    for (final name in names) {
      final file = store.file(name);
      total += await file.length();
      if (total > 40 * 1024 * 1024) throw const StoreException('backupSize');
      files[name] = base64Encode(await file.readAsBytes());
    }
    final plain = utf8.encode(
      jsonEncode({'version': 1, 'rows': rows, 'files': files}),
    );
    if (plain.length > maxBytes) throw const StoreException('backupSize');
    final salt = SecretKeyData.random(length: 16).bytes;
    final box = await cipher.encrypt(
      plain,
      secretKey: await _key(password, salt),
      aad: utf8.encode('PAP:1'),
    );
    return Uint8List.fromList(
      utf8.encode(
        jsonEncode({
          'format': 'PAP',
          'version': 1,
          'salt': base64Encode(salt),
          'nonce': base64Encode(box.nonce),
          'mac': base64Encode(box.mac.bytes),
          'data': base64Encode(box.cipherText),
        }),
      ),
    );
  }

  Future<void> restore(
    PassportStore store,
    List<int> bytes,
    String password,
  ) async {
    if (bytes.length > maxBytes * 2) throw const StoreException('backupSize');
    final newFiles = <String>[];
    try {
      final envelope = jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>;
      if (envelope['format'] != 'PAP' || envelope['version'] != 1) {
        throw const FormatException();
      }
      final salt = base64Decode(envelope['salt'] as String);
      final nonce = base64Decode(envelope['nonce'] as String);
      final mac = base64Decode(envelope['mac'] as String);
      if (salt.length != 16 || nonce.length != 12 || mac.length != 16) {
        throw const FormatException();
      }
      final plain = await cipher.decrypt(
        SecretBox(
          base64Decode(envelope['data'] as String),
          nonce: nonce,
          mac: Mac(mac),
        ),
        secretKey: await _key(password, salt),
        aad: utf8.encode('PAP:1'),
      );
      if (plain.length > maxBytes) throw const FormatException();
      final payload = jsonDecode(utf8.decode(plain)) as Map<String, dynamic>;
      if (payload['version'] != 1) throw const FormatException();
      final rows = payload['rows'] as Map<String, dynamic>;
      final assets = (rows['assets'] as List).cast<Map<String, dynamic>>();
      if (assets.where((a) => a['deletedAt'] == null).length > freeAssetLimit ||
          assets.length > 10000) {
        throw const StoreException('limit');
      }
      for (final a in assets) {
        final asset = Asset.fromMap(a);
        if (!asset.isValid) {
          throw const FormatException();
        }
      }
      final mapping = <String, String>{};
      for (final entry in (payload['files'] as Map<String, dynamic>).entries) {
        store.file(
          entry.key,
        ); // Validate every archive name, even unused entries.
        final content = base64Decode(entry.value as String);
        if (content.length > 10 * 1024 * 1024 || detectMime(content) == null) {
          throw const FormatException();
        }
        final extension = entry.key.split('.').last;
        if (!['jpg', 'png', 'pdf', 'txt'].contains(extension)) {
          throw const FormatException();
        }
        final name = '${const Uuid().v4()}.$extension';
        newFiles.add(name);
        await store.file(name).writeAsBytes(content, flush: true);
        mapping[entry.key] = name;
      }
      for (final a in assets) {
        if (a['photo'] != null) {
          if (!mapping.containsKey(a['photo'])) throw const FormatException();
          a['photo'] = mapping[a['photo']];
        }
      }
      for (final doc in rows['documents'] as List) {
        if (!mapping.containsKey(doc['file'])) throw const FormatException();
        doc['file'] = mapping[doc['file']];
      }
      await store.db.transaction((txn) async {
        for (final table in tables.reversed) {
          await txn.delete(table);
        }
        for (final table in tables) {
          final entries = rows[table] as List;
          if (entries.length > 100000) throw const FormatException();
          for (final row in entries) {
            await txn.insert(table, Map<String, Object?>.from(row as Map));
          }
        }
      });
    } catch (e) {
      for (final name in newFiles) {
        final f = store.file(name);
        if (await f.exists()) await f.delete();
      }
      if (e is StoreException) rethrow;
      throw const StoreException('backupInvalid');
    }
  }
}
