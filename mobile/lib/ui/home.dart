import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/asset.dart';
import '../state.dart';
import 'asset_screen.dart';
import 'asset_form.dart';
import 'settings.dart';
import 'common.dart';
import 'strings.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeState();
}

class _HomeState extends ConsumerState<HomeScreen> {
  int tab = 0;
  String search = '', category = 'All';
  bool highest = false;
  void add() => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => const AssetForm()),
  );
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'Passport',
        style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: -1),
      ),
      actions: [
        Padding(
          padding: const EdgeInsets.only(right: 16),
          child: Chip(
            label: Text(tr(context, 'free')),
            side: BorderSide.none,
            backgroundColor: const Color(0xffe6eddf),
          ),
        ),
      ],
    ),
    body: tab == 3
        ? const SettingsScreen()
        : ref
              .watch(assetsProvider)
              .when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, st) => Center(
                  child: TextButton(
                    onPressed: () => ref.invalidate(assetsProvider),
                    child: Text(tr(context, 'error')),
                  ),
                ),
                data: (assets) => tab == 2
                    ? ReminderList(
                        onAsset: (a) => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => AssetScreen(id: a.id),
                          ),
                        ),
                      )
                    : buildAssets(assets),
              ),
    floatingActionButton: tab <= 1
        ? FloatingActionButton.extended(
            onPressed: add,
            backgroundColor: ink,
            foregroundColor: Colors.white,
            icon: const Icon(Icons.add),
            label: Text(tr(context, 'add')),
          )
        : null,
    bottomNavigationBar: NavigationBar(
      selectedIndex: tab,
      onDestinationSelected: (v) => setState(() => tab = v),
      destinations: [
        for (final (key, icon) in [
          ('home', Icons.grid_view_rounded),
          ('assets', Icons.inventory_2_outlined),
          ('reminders', Icons.notifications_none),
          ('settings', Icons.tune),
        ])
          NavigationDestination(icon: Icon(icon), label: tr(context, key)),
      ],
    ),
  );
  Widget buildAssets(List<Asset> assets) {
    final visible = assets
        .where(
          (a) =>
              a.searchText.contains(search.toLowerCase()) &&
              (category == 'All' || a.category == category),
        )
        .toList();
    if (highest) {
      visible.sort((a, b) {
        final c = a.currency.compareTo(b.currency);
        return c != 0 ? c : b.priceMinor.compareTo(a.priceMinor);
      });
    }
    final now = DateTime.now();
    final totals = <String, int>{};
    for (final a in assets) {
      totals.update(
        a.currency,
        (v) => v + a.priceMinor,
        ifAbsent: () => a.priceMinor,
      );
    }
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(assetsProvider);
        await ref.read(assetsProvider.future);
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 100),
        children: [
          if (tab == 0) ...[
            Text(
              tr(context, 'hello'),
              style: const TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w700,
                color: ink,
                letterSpacing: -.7,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              tr(context, 'dashboardIntro'),
              style: const TextStyle(color: Color(0xff64756d)),
            ),
            const SizedBox(height: 24),
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: ink,
                borderRadius: BorderRadius.circular(24),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    tr(context, 'collection'),
                    style: const TextStyle(
                      color: Color(0xffc4d4be),
                      letterSpacing: 2,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 18),
                  Text(
                    '${assets.length.toString().padLeft(2, '0')} / 10',
                    style: const TextStyle(
                      fontSize: 46,
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Text(
                    tr(context, 'assetCount'),
                    style: const TextStyle(color: Color(0xffc4d4be)),
                  ),
                  const SizedBox(height: 22),
                  const Divider(color: Colors.white24),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      const Icon(
                        Icons.lock_outline,
                        size: 14,
                        color: Color(0xffc4d4be),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          tr(context, 'notCloud'),
                          style: const TextStyle(
                            color: Color(0xffc4d4be),
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: stat(
                    'expiring',
                    assets
                        .where(
                          (a) =>
                              warrantyStatus(a.warrantyEnd, now) ==
                              'Expiring soon',
                        )
                        .length,
                    Icons.verified_outlined,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: stat(
                    'returns',
                    assets.where((a) {
                      final d = daysUntil(a.returnEnd, now);
                      return d != null && d >= 0 && d <= 7;
                    }).length,
                    Icons.assignment_return_outlined,
                  ),
                ),
              ],
            ),
            if (totals.isNotEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      tr(context, 'purchaseTotal'),
                      style: const TextStyle(color: Color(0xff64756d)),
                    ),
                    const SizedBox(height: 6),
                    for (final total in totals.entries)
                      Text(
                        money(context, total.value, total.key),
                        style: const TextStyle(
                          fontSize: 25,
                          fontWeight: FontWeight.w600,
                          color: ink,
                        ),
                      ),
                  ],
                ),
              ),
            const SizedBox(height: 12),
            Text(
              tr(context, 'recent'),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 14),
          ] else ...[
            Text(
              tr(context, 'assets'),
              style: const TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.bold,
                color: ink,
              ),
            ),
            const SizedBox(height: 20),
            TextField(
              onChanged: (v) => setState(() => search = v),
              decoration: InputDecoration(
                hintText: tr(context, 'search'),
                prefixIcon: const Icon(Icons.search),
              ),
            ),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final c in ['All', ...categories])
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: FilterChip(
                        label: Text(tr(context, c == 'All' ? 'all' : c)),
                        selected: category == c,
                        onSelected: (_) => setState(() => category = c),
                      ),
                    ),
                ],
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: () => setState(() => highest = !highest),
                icon: const Icon(Icons.sort),
                label: Text(tr(context, highest ? 'highest' : 'newest')),
              ),
            ),
          ],
          if (assets.isEmpty)
            Container(
              padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 12),
              child: Column(
                children: [
                  const Icon(
                    Icons.inventory_2_outlined,
                    size: 48,
                    color: Color(0xff93a890),
                  ),
                  const SizedBox(height: 20),
                  Text(
                    tr(context, 'empty'),
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                      color: ink,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    tr(context, 'emptyBody'),
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      height: 1.5,
                      color: Color(0xff64756d),
                    ),
                  ),
                ],
              ),
            ),
          if (assets.isNotEmpty && visible.isEmpty)
            Text(tr(context, 'noResults')),
          for (final asset in tab == 0 ? assets.take(5) : visible)
            AssetTile(asset: asset),
        ],
      ),
    );
  }

  Widget stat(String title, int count, IconData icon) => Container(
    padding: const EdgeInsets.all(17),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
      border: Border.all(color: const Color(0xffe4e9e1)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: const Color(0xff8c744a)),
        const SizedBox(height: 14),
        Text(
          '$count',
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w600,
            color: ink,
          ),
        ),
        Text(
          tr(context, title),
          style: const TextStyle(fontSize: 12, color: Color(0xff64756d)),
        ),
      ],
    ),
  );
}

class AssetTile extends ConsumerWidget {
  final Asset asset;
  const AssetTile({super.key, required this.asset});
  @override
  Widget build(BuildContext context, WidgetRef ref) => Card(
    color: Colors.white,
    elevation: 0,
    margin: const EdgeInsets.only(bottom: 10),
    child: ListTile(
      contentPadding: const EdgeInsets.all(12),
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: asset.photo == null
            ? Container(
                width: 56,
                height: 56,
                color: const Color(0xffedf1e8),
                child: const Icon(Icons.devices_other, color: ink),
              )
            : Image.file(
                ref.read(storeProvider).file(asset.photo!),
                width: 56,
                height: 56,
                cacheWidth: 160,
                fit: BoxFit.cover,
                errorBuilder: (_, e, st) =>
                    const Icon(Icons.broken_image_outlined),
              ),
      ),
      title: Text(
        asset.name,
        style: const TextStyle(fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        '${tr(context, asset.category)} · ${money(context, asset.priceMinor, asset.currency)}',
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => AssetScreen(id: asset.id)),
      ),
    ),
  );
}

class ReminderList extends ConsumerWidget {
  final ValueChanged<Asset> onAsset;
  const ReminderList({super.key, required this.onAsset});
  @override
  Widget build(BuildContext context, WidgetRef ref) => FutureBuilder(
    future: ref
        .read(storeProvider)
        .db
        .rawQuery(
          'SELECT r.* FROM reminders r JOIN assets a ON a.id = r.assetId WHERE a.deletedAt IS NULL ORDER BY r.due',
        ),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Center(child: Text(tr(context, 'error')));
      if (!snapshot.hasData) {
        return const Center(child: CircularProgressIndicator());
      }
      if (snapshot.data!.isEmpty) {
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Text(
              tr(context, 'noReminders'),
              textAlign: TextAlign.center,
            ),
          ),
        );
      }
      return ListView(
        children: [
          for (final r in snapshot.data!)
            ListTile(
              leading: const Icon(Icons.notifications_active_outlined),
              title: Text(r['title'] as String),
              subtitle: Text(dateLabel(context, r['due'] as String)),
              onTap: () async {
                final assets = await ref.read(assetsProvider.future);
                final found = assets.where((a) => a.id == r['assetId']);
                if (found.isNotEmpty) onAsset(found.first);
              },
            ),
        ],
      );
    },
  );
}
