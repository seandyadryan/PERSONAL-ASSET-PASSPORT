import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:open_filex/open_filex.dart';
import '../domain/asset.dart';
import '../state.dart';
import 'common.dart';
import 'strings.dart';
import 'asset_form.dart';

class AssetScreen extends ConsumerStatefulWidget {
  final String id;
  const AssetScreen({super.key, required this.id});
  @override
  ConsumerState<AssetScreen> createState() => _AssetScreenState();
}

class _AssetScreenState extends ConsumerState<AssetScreen> {
  bool busy = false;
  int revision = 0;
  Future<void> attach() async {
    setState(() => busy = true);
    try {
      final picked = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'pdf', 'txt'],
      );
      if (picked?.files.single.path != null) {
        await ref
            .read(storeProvider)
            .addDocument(
              widget.id,
              File(picked!.files.single.path!),
              picked.files.single.name,
            );
        if (mounted) setState(() => revision++);
      }
    } catch (e) {
      if (mounted) message(context, errorKey(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> reminder(Asset a) async {
    final controller = TextEditingController(text: a.name);
    String? due;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, update) => AlertDialog(
          title: Text(tr(ctx, 'newReminder')),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: controller,
                  maxLength: 160,
                  decoration: InputDecoration(labelText: tr(ctx, 'title')),
                ),
                const SizedBox(height: 16),
                DateField(
                  label: 'due',
                  value: due,
                  onChanged: (v) => update(() => due = v),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: Text(tr(ctx, 'cancel')),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(tr(ctx, 'confirm')),
            ),
          ],
        ),
      ),
    );
    final title = controller.text.trim();
    controller.dispose();
    if (ok != true || due == null || title.isEmpty || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 9, minute: 0),
    );
    if (time == null || !mounted) return;
    final date = DateTime.parse(
      due!,
    ).add(Duration(hours: time.hour, minutes: time.minute));
    if (!date.isAfter(DateTime.now())) {
      message(context, 'pastReminder');
      return;
    }
    try {
      final id = await ref.read(storeProvider).addReminder(a.id, title, date);
      if (mounted) setState(() => revision++);
      try {
        if (await ref.read(remindersProvider).requestPermission()) {
          await ref.read(remindersProvider).schedule(id, title, date);
          if (mounted) message(context, 'success');
        } else {
          if (mounted) message(context, 'notificationDenied');
        }
      } catch (_) {
        if (mounted) message(context, 'notificationFailed');
      }
    } catch (e) {
      if (mounted) message(context, errorKey(e));
    }
  }

  Future<void> remove() async {
    if (!await confirm(context, 'delete', 'deleteBody')) return;
    try {
      final rows = await ref
          .read(storeProvider)
          .related('reminders', widget.id);
      for (final r in rows) {
        await ref.read(remindersProvider).cancel(r['id'] as int);
      }
      await ref.read(storeProvider).delete(widget.id);
      ref.invalidate(assetsProvider);
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) message(context, errorKey(e));
    }
  }

  @override
  Widget build(BuildContext context) => ref
      .watch(assetsProvider)
      .when(
        loading: () =>
            const Scaffold(body: Center(child: CircularProgressIndicator())),
        error: (e, st) => Scaffold(
          appBar: AppBar(),
          body: Center(child: Text(tr(context, 'error'))),
        ),
        data: (assets) {
          final found = assets.where((a) => a.id == widget.id);
          if (found.isEmpty) {
            return Scaffold(
              appBar: AppBar(),
              body: Text(tr(context, 'noResults')),
            );
          }
          final a = found.first;
          return DefaultTabController(
            length: 3,
            child: Scaffold(
              appBar: AppBar(
                title: Text(a.name),
                actions: [
                  IconButton(
                    tooltip: tr(context, 'edit'),
                    icon: const Icon(Icons.edit_outlined),
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => AssetForm(asset: a)),
                    ),
                  ),
                  IconButton(
                    tooltip: tr(context, 'delete'),
                    icon: const Icon(Icons.delete_outline),
                    onPressed: remove,
                  ),
                ],
                bottom: TabBar(
                  tabs: [
                    for (final key in ['overview', 'documents', 'timeline'])
                      Tab(text: tr(context, key)),
                  ],
                ),
              ),
              body: TabBarView(
                children: [
                  ListView(
                    padding: const EdgeInsets.all(20),
                    children: [
                      if (a.photo != null)
                        ClipRRect(
                          borderRadius: BorderRadius.circular(22),
                          child: Image.file(
                            ref.read(storeProvider).file(a.photo!),
                            height: 230,
                            fit: BoxFit.cover,
                            errorBuilder: (_, e, st) =>
                                Text(tr(context, 'photoMissing')),
                          ),
                        ),
                      const SizedBox(height: 18),
                      Text(
                        tr(context, a.category).toUpperCase(),
                        style: const TextStyle(
                          fontSize: 11,
                          letterSpacing: 2,
                          color: Color(0xff75856d),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        a.name,
                        style: const TextStyle(
                          fontSize: 30,
                          color: ink,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        money(context, a.priceMinor, a.currency),
                        style: const TextStyle(fontSize: 23, color: ink),
                      ),
                      const SizedBox(height: 24),
                      info('purchaseDate', dateLabel(context, a.purchaseDate)),
                      info(
                        'warrantyEnd',
                        '${dateLabel(context, a.warrantyEnd)} · ${tr(context, warrantyStatus(a.warrantyEnd, DateTime.now()))}',
                      ),
                      info('returnEnd', dateLabel(context, a.returnEnd)),
                      for (final entry in {
                        'brand': a.brand,
                        'model': a.model,
                        'serial': a.serial,
                        'vendor': a.vendor,
                        'notes': a.notes,
                      }.entries)
                        if (entry.value.isNotEmpty)
                          info(entry.key, entry.value),
                      const SizedBox(height: 16),
                      OutlinedButton.icon(
                        onPressed: () => reminder(a),
                        icon: const Icon(Icons.notification_add_outlined),
                        label: Text(tr(context, 'newReminder')),
                      ),
                      const SizedBox(height: 18),
                      related('reminders'),
                    ],
                  ),
                  ListView(
                    padding: const EdgeInsets.all(20),
                    children: [
                      Text(tr(context, 'noDocuments')),
                      const SizedBox(height: 16),
                      FilledButton.icon(
                        onPressed: busy ? null : attach,
                        icon: const Icon(Icons.attach_file),
                        label: Text(tr(context, 'attach')),
                      ),
                      const SizedBox(height: 18),
                      related('documents'),
                    ],
                  ),
                  ListView(
                    padding: const EdgeInsets.all(20),
                    children: [related('events')],
                  ),
                ],
              ),
            ),
          );
        },
      );
  Widget info(String label, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 18),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          tr(context, label),
          style: const TextStyle(color: Color(0xff64756d), fontSize: 12),
        ),
        const SizedBox(height: 5),
        SelectableText(value, style: const TextStyle(fontSize: 16)),
      ],
    ),
  );
  Widget related(String table) => FutureBuilder(
    key: ValueKey('$table$revision'),
    future: ref.read(storeProvider).related(table, widget.id),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Text(tr(context, 'error'));
      if (!snapshot.hasData) return const LinearProgressIndicator();
      return Column(
        children: [
          for (final r in snapshot.data!)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(
                table == 'documents'
                    ? Icons.description_outlined
                    : table == 'events'
                    ? Icons.history
                    : Icons.notifications_none,
              ),
              title: Text(
                table == 'events'
                    ? tr(context, r['kind'] as String)
                    : r[table == 'documents' ? 'name' : 'title'] as String,
              ),
              subtitle: Text(
                dateLabel(
                  context,
                  r[table == 'reminders' ? 'due' : 'createdAt'] as String,
                ),
              ),
              onTap: table != 'documents'
                  ? null
                  : () async {
                      try {
                        final result = await OpenFilex.open(
                          ref
                              .read(storeProvider)
                              .file(r['file'] as String)
                              .path,
                        );
                        if (result.type != ResultType.done && context.mounted) {
                          message(context, 'openFailed');
                        }
                      } catch (e) {
                        if (context.mounted) message(context, errorKey(e));
                      }
                    },
              trailing: table == 'reminders'
                  ? IconButton(
                      tooltip: tr(context, 'cancel'),
                      icon: const Icon(Icons.close),
                      onPressed: () async {
                        try {
                          await ref
                              .read(remindersProvider)
                              .cancel(r['id'] as int);
                          await ref
                              .read(storeProvider)
                              .db
                              .delete(
                                'reminders',
                                where: 'id = ?',
                                whereArgs: [r['id']],
                              );
                          if (mounted) setState(() => revision++);
                        } catch (e) {
                          if (context.mounted) message(context, errorKey(e));
                        }
                      },
                    )
                  : null,
            ),
        ],
      );
    },
  );
}
