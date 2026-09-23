import 'dart:io';
import 'dart:convert';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';
import '../domain/asset.dart';

class PassportStore {
  final Database db;
  final Directory files;
  PassportStore(this.db, this.files);
  static Future<PassportStore> open(Directory root) async {
    await root.create(recursive: true);
    final files = await Directory(
      p.join(root.path, 'files'),
    ).create(recursive: true);
    final db = await openDatabase(
      p.join(root.path, 'passport.db'),
      version: 1,
      onConfigure: (db) async {
        await db.execute('PRAGMA foreign_keys=ON');
      },
      onCreate: (db, version) async {
        await db.execute(
          'CREATE TABLE assets(id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, brand TEXT NOT NULL, model TEXT NOT NULL, serial TEXT NOT NULL, vendor TEXT NOT NULL, currency TEXT NOT NULL, priceMinor INTEGER NOT NULL CHECK(priceMinor >= 0), notes TEXT NOT NULL, purchaseDate TEXT, warrantyEnd TEXT, returnEnd TEXT, photo TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, deletedAt TEXT)',
        );
        await db.execute(
          'CREATE INDEX assets_active ON assets(deletedAt, createdAt)',
        );
        await db.execute(
          'CREATE TABLE documents(id TEXT PRIMARY KEY, assetId TEXT NOT NULL REFERENCES assets(id), name TEXT NOT NULL, file TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, createdAt TEXT NOT NULL)',
        );
        await db.execute(
          'CREATE TABLE events(id TEXT PRIMARY KEY, assetId TEXT NOT NULL REFERENCES assets(id), kind TEXT NOT NULL, createdAt TEXT NOT NULL)',
        );
        await db.execute(
          'CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT, assetId TEXT NOT NULL REFERENCES assets(id), title TEXT NOT NULL, due TEXT NOT NULL, createdAt TEXT NOT NULL)',
        );
        await db.execute(
          'CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)',
        );
      },
    );
    return PassportStore(db, files);
  }

  Future<List<Asset>> assets() async => (await db.query(
    'assets',
    where: 'deletedAt IS NULL',
    orderBy: 'createdAt DESC',
  )).map(Asset.fromMap).toList();
  Future<void> save(Asset asset) async {
    if (!asset.isValid) throw const StoreException('invalidAsset');
    await db.transaction((txn) async {
      final existing = await txn.query(
        'assets',
        where: 'id = ?',
        whereArgs: [asset.id],
      );
      if (existing.isEmpty) {
        final count = Sqflite.firstIntValue(
          await txn.rawQuery(
            'SELECT COUNT(*) FROM assets WHERE deletedAt IS NULL',
          ),
        )!;
        if (!canAddAsset(count)) throw const StoreException('limit');
        await txn.insert('assets', asset.toMap());
      } else {
        await txn.update(
          'assets',
          asset.toMap(),
          where: 'id = ? AND deletedAt IS NULL',
          whereArgs: [asset.id],
        );
      }
      await _event(txn, asset.id, existing.isEmpty ? 'created' : 'updated');
    });
  }

  Future<void> delete(String id) async => db.transaction((txn) async {
    await txn.update(
      'assets',
      {
        'deletedAt': DateTime.now().toUtc().toIso8601String(),
        'updatedAt': DateTime.now().toUtc().toIso8601String(),
      },
      where: 'id = ?',
      whereArgs: [id],
    );
    await txn.delete('reminders', where: 'assetId = ?', whereArgs: [id]);
    await _event(txn, id, 'deleted');
  });
  Future<void> _event(DatabaseExecutor txn, String id, String kind) =>
      txn.insert('events', {
        'id': const Uuid().v4(),
        'assetId': id,
        'kind': kind,
        'createdAt': DateTime.now().toUtc().toIso8601String(),
      });
  Future<List<Map<String, Object?>>> related(String table, String id) {
    if (!['documents', 'events', 'reminders'].contains(table)) {
      throw ArgumentError('Invalid table');
    }
    return db.query(
      table,
      where: 'assetId = ?',
      whereArgs: [id],
      orderBy: 'createdAt DESC',
    );
  }

  Future<String> importFile(File source, {bool photo = false}) async {
    if (await source.length() > 10 * 1024 * 1024) {
      throw const StoreException('fileSize');
    }
    final bytes = await source.readAsBytes();
    final mime = detectMime(bytes);
    if (mime == null || (photo && !mime.startsWith('image/'))) {
      throw const StoreException('fileType');
    }
    final extension = {
      'image/jpeg': '.jpg',
      'image/png': '.png',
      'application/pdf': '.pdf',
      'text/plain': '.txt',
    }[mime]!;
    final name = '${const Uuid().v4()}$extension';
    await File(p.join(files.path, name)).writeAsBytes(bytes, flush: true);
    return name;
  }

  File file(String name) {
    if (p.basename(name) != name ||
        name.contains('..') ||
        name.contains('\\') ||
        name.contains('/')) {
      throw const StoreException('fileType');
    }
    return File(p.join(files.path, name));
  }

  Future<void> addDocument(String assetId, File source, String name) async {
    final stored = await importFile(source);
    final f = file(stored);
    await db.transaction((txn) async {
      await txn.insert('documents', {
        'id': const Uuid().v4(),
        'assetId': assetId,
        'name': p.basename(name),
        'file': stored,
        'mime': detectMime(await f.readAsBytes()),
        'size': await f.length(),
        'createdAt': DateTime.now().toUtc().toIso8601String(),
      });
      await _event(txn, assetId, 'document');
    });
  }

  Future<int> addReminder(String assetId, String title, DateTime due) async =>
      db.transaction((txn) async {
        final id = await txn.insert('reminders', {
          'assetId': assetId,
          'title': title,
          'due': due.toUtc().toIso8601String(),
          'createdAt': DateTime.now().toUtc().toIso8601String(),
        });
        await _event(txn, assetId, 'reminder');
        return id;
      });
  Future<String?> setting(String key) async {
    final rows = await db.query('settings', where: 'key = ?', whereArgs: [key]);
    return rows.isEmpty ? null : rows.first['value'] as String;
  }

  Future<void> setSetting(String key, String value) async => db.insert(
    'settings',
    {'key': key, 'value': value},
    conflictAlgorithm: ConflictAlgorithm.replace,
  );
}

class StoreException implements Exception {
  final String code;
  const StoreException(this.code);
}

String? detectMime(List<int> b) {
  if (b.length >= 3 && b[0] == 0xff && b[1] == 0xd8 && b[2] == 0xff) {
    return 'image/jpeg';
  }
  if (b.length >= 8 && b.take(8).join(',') == '137,80,78,71,13,10,26,10') {
    return 'image/png';
  }
  if (b.length >= 5 && String.fromCharCodes(b.take(5)) == '%PDF-') {
    return 'application/pdf';
  }
  if (b.isNotEmpty &&
      b.every((x) => x == 9 || x == 10 || x == 13 || x >= 32) &&
      !b.contains(0)) {
    try {
      utf8.decode(b);
      return 'text/plain';
    } on FormatException {
      return null;
    }
  }
  return null;
}
