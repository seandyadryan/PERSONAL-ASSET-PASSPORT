import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:personal_asset_passport/data/passport_store.dart';
import 'package:personal_asset_passport/domain/asset.dart';
import 'package:personal_asset_passport/services/backup_service.dart';

void main() {
  late Directory root;
  late PassportStore store;
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });
  setUp(() async {
    root = await Directory.systemTemp.createTemp('passport-test-');
    store = await PassportStore.open(root);
  });
  tearDown(() async {
    await store.db.close();
    await root.delete(recursive: true);
  });
  test(
    'create, edit, attach, remind, reopen and soft-delete are durable',
    () async {
      final a = Asset(name: 'Laptop', warrantyEnd: '2027-09-17');
      await store.save(a);
      final doc = File('${root.path}/receipt.txt');
      await doc.writeAsString('Receipt 100 USD');
      await store.addDocument(a.id, doc, 'receipt.txt');
      await store.addReminder(a.id, 'Return', DateTime(2027));
      await store.save(
        Asset(id: a.id, name: 'Laptop edited', createdAt: a.createdAt),
      );
      await store.db.close();
      store = await PassportStore.open(root);
      expect((await store.assets()).single.name, 'Laptop edited');
      expect((await store.related('events', a.id)).length, 4);
      final document = (await store.related('documents', a.id)).single;
      expect(
        await store.file(document['file'] as String).readAsString(),
        'Receipt 100 USD',
      );
      await store.delete(a.id);
      expect(await store.assets(), isEmpty);
      expect(await store.related('reminders', a.id), isEmpty);
    },
  );
  test(
    'free limit enforced transactionally and path traversal rejected',
    () async {
      for (var i = 0; i < 10; i++) {
        await store.save(Asset(name: 'Asset $i'));
      }
      await expectLater(
        store.save(Asset(name: 'Extra')),
        throwsA(isA<StoreException>()),
      );
      expect(() => store.file('../secret'), throwsA(isA<StoreException>()));
      expect(() => store.file('..\\secret'), throwsA(isA<StoreException>()));
    },
  );
  test(
    'encrypted backup round-trip includes files; wrong password preserves inventory',
    () async {
      final a = Asset(name: 'Camera');
      await store.save(a);
      final doc = File('${root.path}/receipt.txt');
      await doc.writeAsString('Private receipt');
      await store.addDocument(a.id, doc, 'receipt.txt');
      final backup = BackupService();
      final encrypted = await backup.export(store, 'correct horse battery');
      expect(
        String.fromCharCodes(encrypted),
        isNot(contains('Private receipt')),
      );
      await store.save(Asset(name: 'Keep me'));
      await expectLater(
        backup.restore(store, encrypted, 'wrong password'),
        throwsA(isA<StoreException>()),
      );
      expect((await store.assets()).length, 2);
      await backup.restore(store, encrypted, 'correct horse battery');
      expect((await store.assets()).single.name, 'Camera');
      final row = (await store.related('documents', a.id)).single;
      expect(
        await store.file(row['file'] as String).readAsString(),
        'Private receipt',
      );
      final tampered = List<int>.of(encrypted);
      tampered[tampered.length - 5] ^= 1;
      await expectLater(
        backup.restore(store, tampered, 'correct horse battery'),
        throwsA(isA<StoreException>()),
      );
      expect((await store.assets()).single.name, 'Camera');
    },
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
