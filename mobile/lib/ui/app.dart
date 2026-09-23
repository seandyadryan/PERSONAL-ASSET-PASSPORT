import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../state.dart';
import 'common.dart';
import 'strings.dart';
import 'home.dart';

class PassportApp extends ConsumerWidget {
  final bool started;
  const PassportApp({super.key, this.started = false});
  @override
  Widget build(BuildContext context, WidgetRef ref) => MaterialApp(
    title: 'Personal Asset Passport',
    debugShowCheckedModeBanner: false,
    locale: Locale(ref.watch(localeProvider)),
    supportedLocales: const [Locale('en'), Locale('id')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: ink,
        primary: ink,
        surface: paper,
      ),
      scaffoldBackgroundColor: paper,
      appBarTheme: const AppBarTheme(
        backgroundColor: paper,
        foregroundColor: ink,
        centerTitle: false,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xffdce3dc)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xffdce3dc)),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    ),
    home: started ? const HomeScreen() : const WelcomeScreen(),
  );
}

class WelcomeScreen extends ConsumerStatefulWidget {
  const WelcomeScreen({super.key});
  @override
  ConsumerState<WelcomeScreen> createState() => _WelcomeState();
}

class _WelcomeState extends ConsumerState<WelcomeScreen> {
  bool busy = false;
  Future<void> proceed(bool google) async {
    setState(() => busy = true);
    try {
      if (google && !await ref.read(authProvider).signIn()) return;
      await ref.read(storeProvider).setSetting('started', 'true');
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const HomeScreen()),
        );
      }
    } catch (e) {
      if (mounted) message(context, errorKey(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: ListView(
            padding: const EdgeInsets.all(28),
            children: [
              Row(
                children: [
                  const Icon(Icons.shield_outlined, color: ink),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text(
                      'PERSONAL ASSET\nPASSPORT',
                      style: TextStyle(
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1.6,
                        fontSize: 11,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: () async {
                      final lang = ref.read(localeProvider) == 'en'
                          ? 'id'
                          : 'en';
                      ref.read(localeProvider.notifier).state = lang;
                      await ref.read(storeProvider).setSetting('locale', lang);
                    },
                    child: Text(
                      ref.watch(localeProvider) == 'en' ? 'ID' : 'EN',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 48),
              Container(
                height: 210,
                decoration: BoxDecoration(
                  color: ink,
                  borderRadius: BorderRadius.circular(28),
                ),
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    Positioned(
                      right: -20,
                      top: -45,
                      child: Icon(
                        Icons.fingerprint,
                        size: 220,
                        color: Colors.white.withValues(alpha: .08),
                      ),
                    ),
                    Transform.rotate(
                      angle: -.07,
                      child: Container(
                        width: 150,
                        height: 155,
                        padding: const EdgeInsets.all(20),
                        decoration: BoxDecoration(
                          color: const Color(0xffe2ebce),
                          borderRadius: BorderRadius.circular(18),
                        ),
                        child: const Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Icon(
                              Icons.verified_user_outlined,
                              color: ink,
                              size: 38,
                            ),
                            Text(
                              'ASSET\nPASSPORT',
                              style: TextStyle(
                                color: ink,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 2,
                              ),
                            ),
                            Divider(color: ink),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 30),
              Text(
                tr(context, 'tagline'),
                style: const TextStyle(
                  fontSize: 35,
                  height: 1.12,
                  color: ink,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -1.2,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                tr(context, 'intro'),
                style: const TextStyle(
                  fontSize: 16,
                  height: 1.6,
                  color: Color(0xff64756d),
                ),
              ),
              const SizedBox(height: 28),
              FilledButton.icon(
                onPressed: busy ? null : () => proceed(true),
                icon: busy
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.account_circle_outlined),
                label: Text(tr(context, 'google')),
              ),
              const SizedBox(height: 10),
              OutlinedButton(
                onPressed: busy ? null : () => proceed(false),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Text(tr(context, 'local')),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                tr(context, 'localNote'),
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 12,
                  height: 1.5,
                  color: Color(0xff64756d),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
