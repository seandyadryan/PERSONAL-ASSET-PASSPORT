import 'package:flutter_test/flutter_test.dart';
import 'package:personal_asset_passport/domain/asset.dart';

void main() {
  test('invalid dates and asset values are rejected before persistence', () {
    expect(validCalendarDate('2026-02-30'), false);
    expect(validCalendarDate('2028-02-29'), true);
    expect(Asset(name: '', priceMinor: -1).isValid, false);
    expect(
      Asset(
        name: 'Camera',
        purchaseDate: '2026-09-17',
        returnEnd: '2026-09-16',
      ).isValid,
      false,
    );
  });
  test('warranty and returns use calendar days including the deadline', () {
    final now = DateTime(2026, 9, 17, 23, 59);
    expect(warrantyStatus(null, now), 'Unknown');
    expect(warrantyStatus('2026-09-16', now), 'Expired');
    expect(warrantyStatus('2026-09-17', now), 'Expiring soon');
    expect(warrantyStatus('2026-10-17', now), 'Expiring soon');
    expect(warrantyStatus('2026-10-18', now), 'Active');
    expect(daysUntil('2026-09-18', now), 1);
    expect(daysUntil('2026-09-17', now), 0);
  });
  test('free entitlement allows ten assets, never eleven', () {
    expect(canAddAsset(9), true);
    expect(canAddAsset(10), false);
  });
  test('asset retains exact minor units and search identifiers', () {
    final a = Asset(
      name: 'Camera',
      priceMinor: 129999,
      serial: 'SN123',
      currency: 'EUR',
    );
    final loaded = Asset.fromMap(a.toMap());
    expect(loaded.priceMinor, 129999);
    expect(loaded.searchText, contains('sn123'));
    expect(loaded.id, a.id);
  });
}
