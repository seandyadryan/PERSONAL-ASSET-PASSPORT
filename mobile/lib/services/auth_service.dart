import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:http/http.dart' as http;

class AuthService {
  bool ready = false;
  String? configurationError;
  static const apiKey = String.fromEnvironment('FIREBASE_API_KEY');
  static const appId = String.fromEnvironment('FIREBASE_APP_ID');
  static const projectId = String.fromEnvironment('FIREBASE_PROJECT_ID');
  static const senderId = String.fromEnvironment(
    'FIREBASE_MESSAGING_SENDER_ID',
  );
  static const googleClientId = String.fromEnvironment('GOOGLE_WEB_CLIENT_ID');
  static const apiUrl = String.fromEnvironment('API_BASE_URL');
  Future<void> initialize() async {
    if ([
      apiKey,
      appId,
      projectId,
      senderId,
      googleClientId,
    ].any((s) => s.isEmpty)) {
      configurationError = 'firebaseMissing';
      return;
    }
    try {
      await Firebase.initializeApp(
        options: const FirebaseOptions(
          apiKey: apiKey,
          appId: appId,
          messagingSenderId: senderId,
          projectId: projectId,
        ),
      );
      await GoogleSignIn.instance.initialize(serverClientId: googleClientId);
      ready = true;
    } catch (_) {
      configurationError = 'firebaseFailed';
    }
  }

  User? get user => ready ? FirebaseAuth.instance.currentUser : null;
  Stream<User?> get changes =>
      ready ? FirebaseAuth.instance.authStateChanges() : Stream.value(null);
  Future<bool> signIn() async {
    if (!ready) throw AuthFailure(configurationError ?? 'firebaseMissing');
    try {
      final account = await GoogleSignIn.instance.authenticate();
      final token = account.authentication.idToken;
      if (token == null) throw const AuthFailure('loginFailed');
      await FirebaseAuth.instance.signInWithCredential(
        GoogleAuthProvider.credential(idToken: token),
      );
      return true;
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled) return false;
      throw const AuthFailure('loginFailed');
    } on FirebaseAuthException catch (e) {
      throw AuthFailure(
        e.code == 'network-request-failed' ? 'offline' : 'loginFailed',
      );
    }
  }

  Future<void> signOut() async {
    if (!ready) return;
    await FirebaseAuth.instance.signOut();
    await GoogleSignIn.instance.signOut();
  }

  Future<void> verifyBackend() async {
    final uri = Uri.tryParse(apiUrl);
    if (uri == null || uri.scheme != 'https' || uri.host.isEmpty) {
      throw const AuthFailure('apiMissing');
    }
    final token = await user?.getIdToken();
    if (token == null) throw const AuthFailure('loginFailed');
    final response = await http
        .get(
          uri.resolve('/api/me'),
          headers: {'Authorization': 'Bearer $token'},
        )
        .timeout(const Duration(seconds: 15));
    if (response.statusCode != 200) throw const AuthFailure('apiFailed');
  }
}

class AuthFailure implements Exception {
  final String code;
  const AuthFailure(this.code);
}
