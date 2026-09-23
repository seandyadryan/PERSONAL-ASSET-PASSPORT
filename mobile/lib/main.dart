import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'data/passport_store.dart';
import 'services/auth_service.dart';
import 'services/notifications.dart';
import 'state.dart';
import 'ui/app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    final store = await PassportStore.open(
      await getApplicationSupportDirectory(),
    );
    final auth = AuthService();
    await auth.initialize();
    final reminders = ReminderService();
    try {
      await reminders.initialize();
    } catch (_) {
      await store.setSetting('notificationInitFailed', 'true');
    }
    final started = await store.setting('started') == 'true';
    final language =
        await store.setting('locale') ??
        WidgetsBinding.instance.platformDispatcher.locale.languageCode;
    runApp(
      ProviderScope(
        overrides: [
          storeProvider.overrideWithValue(store),
          authProvider.overrideWithValue(auth),
          remindersProvider.overrideWithValue(reminders),
          localeProvider.overrideWith(
            (ref) => ['en', 'id'].contains(language) ? language : 'en',
          ),
        ],
        child: PassportApp(started: started),
      ),
    );
  } catch (_) {
    runApp(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text(
                    'Unable to open your local vault.\nPenyimpanan lokal tidak dapat dibuka.\n\nPlease restart the app. Do not uninstall it if you have data to recover.\nMulai ulang aplikasi. Jangan hapus aplikasi jika ada data yang perlu dipulihkan.',
                    textAlign: TextAlign.center,
                  ),
                  TextButton(
                    onPressed: main,
                    child: const Text('Retry / Coba lagi'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
