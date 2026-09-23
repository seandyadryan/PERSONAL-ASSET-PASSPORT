import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'data/passport_store.dart';
import 'domain/asset.dart';
import 'services/auth_service.dart';
import 'services/notifications.dart';

final storeProvider = Provider<PassportStore>(
  (ref) => throw StateError('Store not initialized'),
);
final authProvider = Provider<AuthService>(
  (ref) => throw StateError('Auth not initialized'),
);
final remindersProvider = Provider<ReminderService>(
  (ref) => throw StateError('Notifications not initialized'),
);
final assetsProvider = FutureProvider<List<Asset>>(
  (ref) => ref.watch(storeProvider).assets(),
);
final localeProvider = StateProvider<String>((ref) => 'en');
