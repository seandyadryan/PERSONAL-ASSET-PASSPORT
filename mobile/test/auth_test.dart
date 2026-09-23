import 'package:flutter_test/flutter_test.dart';
import 'package:personal_asset_passport/services/auth_service.dart';

void main() {
  test(
    'missing Firebase configuration is explicit and local mode remains usable',
    () async {
      final auth = AuthService();
      await auth.initialize();
      expect(auth.ready, false);
      expect(auth.configurationError, 'firebaseMissing');
      expect(auth.user, null);
      expect(await auth.changes.first, null);
      await expectLater(
        auth.signIn(),
        throwsA(
          isA<AuthFailure>().having((e) => e.code, 'code', 'firebaseMissing'),
        ),
      );
      await auth.signOut();
    },
  );
}
