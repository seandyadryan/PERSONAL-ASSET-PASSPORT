import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

class ReminderService {
  final plugin = FlutterLocalNotificationsPlugin();
  Future<void> initialize() async {
    tzdata.initializeTimeZones();
    await plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('ic_notification'),
      ),
    );
  }

  Future<bool> requestPermission() async =>
      await plugin
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >()
          ?.requestNotificationsPermission() ??
      false;
  Future<void> schedule(int id, String title, DateTime due) async {
    if (!due.isAfter(DateTime.now())) return;
    await plugin.zonedSchedule(
      id: id,
      title: title,
      body: 'Personal Asset Passport',
      scheduledDate: tz.TZDateTime.from(due, tz.UTC),
      notificationDetails: const NotificationDetails(
        android: AndroidNotificationDetails(
          'asset_reminders',
          'Asset reminders',
          channelDescription: 'Warranty, returns and custom reminders',
          importance: Importance.high,
          priority: Priority.high,
        ),
      ),
      androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
    );
  }

  Future<void> cancel(int id) => plugin.cancel(id: id);
  Future<void> restore(List<Map<String, Object?>> rows) async {
    await plugin.cancelAll();
    for (final row in rows) {
      await schedule(
        row['id'] as int,
        row['title'] as String,
        DateTime.parse(row['due'] as String),
      );
    }
  }
}
