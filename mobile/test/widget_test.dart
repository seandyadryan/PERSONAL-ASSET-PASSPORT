import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:personal_asset_passport/data/passport_store.dart';
import 'package:personal_asset_passport/services/auth_service.dart';
import 'package:personal_asset_passport/state.dart';
import 'package:personal_asset_passport/ui/app.dart';

void main() {
  testWidgets('skip sign-in, navigate, validate form and save first passport', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(430, 960);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final captureKey = GlobalKey();
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final root = (await tester.runAsync(
      () => Directory.systemTemp.createTemp('passport-widget-'),
    ))!;
    final store = (await tester.runAsync(() => PassportStore.open(root)))!;
    addTearDown(() async {
      await store.db.close();
      await root.delete(recursive: true);
    });
    await tester.pumpWidget(
      RepaintBoundary(
        key: captureKey,
        child: ProviderScope(
          overrides: [
            storeProvider.overrideWithValue(store),
            authProvider.overrideWithValue(AuthService()),
          ],
          child: const PassportApp(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (const bool.fromEnvironment('CAPTURE_UI')) {
      final boundary =
          captureKey.currentContext!.findRenderObject()
              as RenderRepaintBoundary;
      final image = await boundary.toImage();
      final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!;
      await tester.runAsync(() async {
        await Directory('../docs/screenshots').create(recursive: true);
        await File(
          '../docs/screenshots/welcome.png',
        ).writeAsBytes(bytes.buffer.asUint8List());
      });
      image.dispose();
    }
    await tester.scrollUntilVisible(find.text('Continue on this device'), 200);
    await tester.runAsync(() async {
      await tester.tap(find.text('Continue on this device'));
      await Future<void>.delayed(const Duration(milliseconds: 300));
    });
    await tester.pumpAndSettle();
    await tester.tap(find.text('Add asset'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Save passport'), 200);
    await tester.tap(find.text('Save passport'));
    await tester.pumpAndSettle();
    final name = find.widgetWithText(TextFormField, 'Asset name');
    await tester.scrollUntilVisible(name, -200);
    expect(find.text('This field is required.'), findsOneWidget);
    await tester.enterText(name, 'My camera');
    await tester.scrollUntilVisible(find.text('Save passport'), 200);
    await tester.runAsync(() async {
      await tester.tap(find.text('Save passport'));
      await Future<void>.delayed(const Duration(milliseconds: 300));
    });
    await tester.pumpAndSettle();
    final saved = await tester.runAsync(() => store.assets());
    expect(saved!.single.name, 'My camera');
  });
}
