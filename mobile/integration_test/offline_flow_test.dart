import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path_provider/path_provider.dart';
import 'package:personal_asset_passport/data/passport_store.dart';
import 'package:personal_asset_passport/domain/asset.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'local asset, receipt, deadline and reminder survive database reopen',
    (tester) async {
      final base = await getTemporaryDirectory();
      final directory = await Directory(
        '${base.path}/passport-integration-${DateTime.now().microsecondsSinceEpoch}',
      ).create();
      var store = await PassportStore.open(directory);
      try {
        final asset = Asset(
          name: 'Acceptance camera',
          warrantyEnd: '2027-09-17',
          returnEnd: '2026-10-01',
        );
        await store.save(asset);
        final receipt = await File(
          '${directory.path}/original.txt',
        ).writeAsString('Acceptance receipt');
        await store.addDocument(asset.id, receipt, 'receipt.txt');
        await store.addReminder(
          asset.id,
          'Return camera',
          DateTime(2027, 1, 1),
        );
        await receipt.delete();
        await store.db.close();
        store = await PassportStore.open(directory);
        expect((await store.assets()).single.warrantyEnd, '2027-09-17');
        expect((await store.related('reminders', asset.id)).length, 1);
        final doc = (await store.related('documents', asset.id)).single;
        expect(
          await store.file(doc['file'] as String).readAsString(),
          'Acceptance receipt',
        );
        expect((await store.related('events', asset.id)).length, 3);
        await tester.pumpWidget(
          const MaterialApp(
            home: Scaffold(body: Text('Offline persistence verified')),
          ),
        );
        expect(find.text('Offline persistence verified'), findsOneWidget);
      } finally {
        await store.db.close();
        await directory.delete(recursive: true);
      }
    },
  );
}
