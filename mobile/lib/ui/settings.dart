import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/backup_service.dart';
import '../state.dart';
import 'common.dart';
import 'strings.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsState();
}

class _SettingsState extends ConsumerState<SettingsScreen> {
  bool busy = false;
  void notice(String key) {
    if (mounted) message(context, key);
  }

  Future<void> action(Future<void> Function() run) async {
    setState(() => busy = true);
    try {
      await run();
    } catch (e) {
      notice(errorKey(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<String?> password() async {
    final controller = TextEditingController();
    final value = await showDialog<String>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(tr(c, 'backup')),
        content: TextField(
          controller: controller,
          obscureText: true,
          autocorrect: false,
          enableSuggestions: false,
          decoration: InputDecoration(labelText: tr(c, 'password')),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: Text(tr(c, 'cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(c, controller.text),
            child: Text(tr(c, 'confirm')),
          ),
        ],
      ),
    );
    controller.dispose();
    return value;
  }

  Future<void> exportBackup() async {
    final secret = await password();
    if (secret == null) return;
    await action(() async {
      final bytes = await BackupService().export(
        ref.read(storeProvider),
        secret,
      );
      final saved = await FilePicker.platform.saveFile(
        dialogTitle: 'Personal Asset Passport',
        fileName: 'passport-${DateTime.now().millisecondsSinceEpoch}.pap',
        bytes: bytes,
        type: FileType.any,
      );
      if (mounted) {
        message(context, saved == null ? 'backupCancelled' : 'success');
      }
    });
  }

  Future<void> importBackup() async {
    if (!await confirm(context, 'import', 'replaceBody') || !mounted) return;
    final secret = await password();
    if (secret == null) return;
    await action(() async {
      final picked = await FilePicker.platform.pickFiles();
      if (picked?.files.single.path == null) return;
      final file = File(picked!.files.single.path!);
      if (await file.length() > BackupService.maxBytes * 2) {
        notice('backupSize');
        return;
      }
      await BackupService().restore(
        ref.read(storeProvider),
        await file.readAsBytes(),
        secret,
      );
      ref.invalidate(assetsProvider);
      notice('restored');
      try {
        await ref
            .read(remindersProvider)
            .restore(await ref.read(storeProvider).db.query('reminders'));
      } catch (_) {
        notice('notificationFailed');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);
    return AbsorbPointer(
      absorbing: busy,
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (busy) const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Section(
            tr(context, 'account'),
            StreamBuilder(
              stream: auth.changes,
              builder: (context, snapshot) => Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    auth.user?.email ?? tr(context, 'signedOut'),
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    tr(context, 'localNote'),
                    style: const TextStyle(
                      height: 1.5,
                      color: Color(0xff64756d),
                    ),
                  ),
                  const SizedBox(height: 14),
                  if (auth.user == null)
                    FilledButton(
                      onPressed: () => action(() async {
                        await auth.signIn();
                      }),
                      child: Text(tr(context, 'google')),
                    )
                  else ...[
                    OutlinedButton(
                      onPressed: () => action(auth.signOut),
                      child: Text(tr(context, 'signOut')),
                    ),
                    TextButton(
                      onPressed: () => action(() async {
                        await auth.verifyBackend();
                        notice('verified');
                      }),
                      child: Text(tr(context, 'verify')),
                    ),
                  ],
                ],
              ),
            ),
          ),
          Section(
            tr(context, 'language'),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'en', label: Text('English')),
                ButtonSegment(value: 'id', label: Text('Indonesia')),
              ],
              selected: {ref.watch(localeProvider)},
              onSelectionChanged: (value) => action(() async {
                await ref.read(storeProvider).setSetting('locale', value.first);
                ref.read(localeProvider.notifier).state = value.first;
              }),
            ),
          ),
          Section(
            tr(context, 'backup'),
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  tr(context, 'backupBody'),
                  style: const TextStyle(height: 1.5),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: exportBackup,
                  icon: const Icon(Icons.lock_outline),
                  label: Text(tr(context, 'export')),
                ),
                OutlinedButton.icon(
                  onPressed: importBackup,
                  icon: const Icon(Icons.restore),
                  label: Text(tr(context, 'import')),
                ),
              ],
            ),
          ),
          Section(
            tr(context, 'reminders'),
            OutlinedButton(
              onPressed: () => action(() async {
                final service = ref.read(remindersProvider);
                await service.initialize();
                if (!await service.requestPermission()) {
                  notice('notificationDenied');
                  return;
                }
                await service.restore(
                  await ref.read(storeProvider).db.query('reminders'),
                );
                notice('success');
              }),
              child: Text(tr(context, 'retryNotifications')),
            ),
          ),
          Section(
            tr(context, 'privacy'),
            Text(
              tr(context, 'privacyBody'),
              style: const TextStyle(height: 1.6, color: Color(0xff64756d)),
            ),
          ),
          const Text(
            'Personal Asset Passport · 0.1.0',
            style: TextStyle(fontSize: 12),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
