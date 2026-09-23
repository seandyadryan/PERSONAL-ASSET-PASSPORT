import 'package:uuid/uuid.dart';

const categories = [
  'Electronics',
  'Appliances',
  'Furniture',
  'Vehicles',
  'Photography',
  'Jewelry',
  'Tools',
  'Sports',
  'Other',
];
const currencies = ['USD', 'EUR', 'GBP', 'AUD', 'CAD', 'SGD', 'IDR', 'JPY'];
const freeAssetLimit = 10;
DateTime dateOnly(DateTime date) =>
    DateTime.utc(date.year, date.month, date.day);
int? daysUntil(String? value, DateTime today) => value == null
    ? null
    : dateOnly(DateTime.parse(value)).difference(dateOnly(today)).inDays;
String warrantyStatus(String? end, DateTime now) {
  final days = daysUntil(end, now);
  if (days == null) return 'Unknown';
  if (days < 0) return 'Expired';
  return days <= 30 ? 'Expiring soon' : 'Active';
}

bool canAddAsset(int count) => count < freeAssetLimit;

bool validCalendarDate(String value) {
  if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) return false;
  final date = DateTime.tryParse(value);
  return date != null &&
      date.year >= 1900 &&
      date.year <= 2200 &&
      date.toIso8601String().startsWith(value);
}

class Asset {
  final String id,
      name,
      category,
      brand,
      model,
      serial,
      vendor,
      currency,
      notes,
      createdAt,
      updatedAt;
  final int priceMinor;
  final String? purchaseDate, warrantyEnd, returnEnd, photo;
  Asset({
    String? id,
    required this.name,
    this.category = 'Electronics',
    this.brand = '',
    this.model = '',
    this.serial = '',
    this.vendor = '',
    this.currency = 'USD',
    this.priceMinor = 0,
    this.notes = '',
    this.purchaseDate,
    this.warrantyEnd,
    this.returnEnd,
    this.photo,
    String? createdAt,
    String? updatedAt,
  }) : id = id ?? const Uuid().v4(),
       createdAt = createdAt ?? DateTime.now().toUtc().toIso8601String(),
       updatedAt = updatedAt ?? DateTime.now().toUtc().toIso8601String();
  Map<String, Object?> toMap() => {
    'id': id,
    'name': name,
    'category': category,
    'brand': brand,
    'model': model,
    'serial': serial,
    'vendor': vendor,
    'currency': currency,
    'priceMinor': priceMinor,
    'notes': notes,
    'purchaseDate': purchaseDate,
    'warrantyEnd': warrantyEnd,
    'returnEnd': returnEnd,
    'photo': photo,
    'createdAt': createdAt,
    'updatedAt': updatedAt,
  };
  factory Asset.fromMap(Map<String, Object?> m) => Asset(
    id: m['id'] as String,
    name: m['name'] as String,
    category: m['category'] as String,
    brand: m['brand'] as String,
    model: m['model'] as String,
    serial: m['serial'] as String,
    vendor: m['vendor'] as String,
    currency: m['currency'] as String,
    priceMinor: m['priceMinor'] as int,
    notes: m['notes'] as String,
    purchaseDate: m['purchaseDate'] as String?,
    warrantyEnd: m['warrantyEnd'] as String?,
    returnEnd: m['returnEnd'] as String?,
    photo: m['photo'] as String?,
    createdAt: m['createdAt'] as String,
    updatedAt: m['updatedAt'] as String,
  );
  String get searchText =>
      '$name $category $brand $model $serial $vendor $notes'.toLowerCase();
  bool get isValid =>
      name.trim().isNotEmpty &&
      name.length <= 160 &&
      categories.contains(category) &&
      currencies.contains(currency) &&
      priceMinor >= 0 &&
      priceMinor <= 99999999999900 &&
      notes.length <= 4000 &&
      [brand, model, serial, vendor].every((s) => s.length <= 200) &&
      [
        purchaseDate,
        warrantyEnd,
        returnEnd,
      ].whereType<String>().every(validCalendarDate) &&
      (purchaseDate == null ||
          [warrantyEnd, returnEnd].whereType<String>().every(
            (d) => d.compareTo(purchaseDate!) >= 0,
          )) &&
      DateTime.tryParse(createdAt) != null &&
      DateTime.tryParse(updatedAt) != null;
}
