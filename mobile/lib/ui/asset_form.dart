import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import '../domain/asset.dart';
import '../state.dart';
import 'common.dart';
import 'strings.dart';

class AssetForm extends ConsumerStatefulWidget {
  final Asset? asset;
  const AssetForm({super.key, this.asset});
  @override
  ConsumerState<AssetForm> createState() => _AssetFormState();
}

class _AssetFormState extends ConsumerState<AssetForm> {
  final form = GlobalKey<FormState>();
  final fields = {
    for (final k in [
      'name',
      'brand',
      'model',
      'serial',
      'vendor',
      'price',
      'notes',
    ])
      k: TextEditingController(),
  };
  String category = 'Electronics', currency = 'USD';
  String? purchase, warranty, returns, photo;
  bool busy = false, initialized = false;
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (initialized) return;
    initialized = true;
    final a = widget.asset;
    if (a != null) {
      for (final k in ['name', 'brand', 'model', 'serial', 'vendor', 'notes']) {
        fields[k]!.text = a.toMap()[k] as String;
      }
      category = a.category;
      currency = a.currency;
      purchase = a.purchaseDate;
      warranty = a.warrantyEnd;
      returns = a.returnEnd;
      photo = a.photo;
      fields['price']!.text = NumberFormat(
        '0.00',
        Localizations.localeOf(context).toString(),
      ).format(a.priceMinor / 100);
    }
  }

  @override
  void dispose() {
    for (final c in fields.values) {
      c.dispose();
    }
    super.dispose();
  }

  int? parsePrice() {
    final text = fields['price']!.text.trim();
    if (text.isEmpty) return 0;
    final locale = Localizations.localeOf(context).languageCode;
    final pattern = locale == 'id'
        ? RegExp(r'^(\d+|\d{1,3}(\.\d{3})+)(,\d{1,2})?$')
        : RegExp(r'^(\d+|\d{1,3}(,\d{3})+)(\.\d{1,2})?$');
    if (!pattern.hasMatch(text)) return null;
    final normalized = locale == 'id'
        ? text.replaceAll('.', '').replaceAll(',', '.')
        : text.replaceAll(',', '');
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(normalized)) return null;
    final value = double.tryParse(normalized);
    if (value == null || !value.isFinite || value < 0 || value > 999999999999) {
      return null;
    }
    return (value * 100).round();
  }

  Future<void> save() async {
    if (!form.currentState!.validate()) return;
    if (purchase != null &&
        [
          warranty,
          returns,
        ].whereType<String>().any((d) => d.compareTo(purchase!) < 0)) {
      message(context, 'invalidDate');
      return;
    }
    setState(() => busy = true);
    try {
      final a = Asset(
        id: widget.asset?.id,
        name: fields['name']!.text.trim(),
        category: category,
        currency: currency,
        priceMinor: parsePrice()!,
        brand: fields['brand']!.text.trim(),
        model: fields['model']!.text.trim(),
        serial: fields['serial']!.text.trim(),
        vendor: fields['vendor']!.text.trim(),
        notes: fields['notes']!.text.trim(),
        purchaseDate: purchase,
        warrantyEnd: warranty,
        returnEnd: returns,
        photo: photo,
        createdAt: widget.asset?.createdAt,
      );
      await ref.read(storeProvider).save(a);
      ref.invalidate(assetsProvider);
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) message(context, errorKey(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> pickPhoto(ImageSource source) async {
    try {
      final image = await ImagePicker().pickImage(
        source: source,
        maxWidth: 1600,
        maxHeight: 1600,
        imageQuality: 82,
      );
      if (image == null) return;
      final name = await ref
          .read(storeProvider)
          .importFile(File(image.path), photo: true);
      if (mounted) setState(() => photo = name);
    } catch (e) {
      if (mounted) message(context, errorKey(e));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(tr(context, widget.asset == null ? 'add' : 'edit')),
    ),
    body: Form(
      key: form,
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (photo != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(20),
                child: Image.file(
                  ref.read(storeProvider).file(photo!),
                  height: 190,
                  fit: BoxFit.cover,
                  errorBuilder: (_, e, st) => Text(tr(context, 'photoMissing')),
                ),
              ),
            ),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: busy ? null : () => pickPhoto(ImageSource.camera),
                  icon: const Icon(Icons.photo_camera_outlined),
                  label: Text(tr(context, 'camera')),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: busy ? null : () => pickPhoto(ImageSource.gallery),
                  icon: const Icon(Icons.photo_library_outlined),
                  label: Text(tr(context, 'gallery')),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          field('name', required: true),
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: DropdownButtonFormField<String>(
              initialValue: category,
              decoration: InputDecoration(labelText: tr(context, 'category')),
              items: [
                for (final c in categories)
                  DropdownMenuItem(value: c, child: Text(tr(context, c))),
              ],
              onChanged: busy ? null : (v) => setState(() => category = v!),
            ),
          ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: field('price')),
              const SizedBox(width: 10),
              SizedBox(
                width: 112,
                child: DropdownButtonFormField<String>(
                  initialValue: currency,
                  decoration: InputDecoration(
                    labelText: tr(context, 'currency'),
                  ),
                  items: [
                    for (final c in currencies)
                      DropdownMenuItem(value: c, child: Text(c)),
                  ],
                  onChanged: busy ? null : (v) => setState(() => currency = v!),
                ),
              ),
            ],
          ),
          DateField(
            label: 'purchaseDate',
            value: purchase,
            onChanged: (v) => setState(() => purchase = v),
          ),
          ExpansionTile(
            title: Text(tr(context, 'overview')),
            initiallyExpanded: widget.asset != null,
            tilePadding: EdgeInsets.zero,
            children: [
              field('brand'),
              field('model'),
              field('serial'),
              field('vendor'),
            ],
          ),
          const SizedBox(height: 12),
          DateField(
            label: 'warrantyEnd',
            value: warranty,
            onChanged: (v) => setState(() => warranty = v),
          ),
          DateField(
            label: 'returnEnd',
            value: returns,
            onChanged: (v) => setState(() => returns = v),
          ),
          field('notes'),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: busy ? null : save,
            child: busy
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(tr(context, 'save')),
          ),
          const SizedBox(height: 30),
        ],
      ),
    ),
  );
  Widget field(String key, {bool required = false}) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: TextFormField(
      controller: fields[key],
      enabled: !busy,
      maxLength: key == 'notes'
          ? 4000
          : key == 'name'
          ? 160
          : key == 'price'
          ? 20
          : 200,
      maxLines: key == 'notes' ? 3 : 1,
      keyboardType: key == 'price'
          ? const TextInputType.numberWithOptions(decimal: true)
          : TextInputType.text,
      decoration: InputDecoration(labelText: tr(context, key), counterText: ''),
      validator: (v) => required && (v == null || v.trim().isEmpty)
          ? tr(context, 'required')
          : key == 'price' && parsePrice() == null
          ? tr(context, 'invalidPrice')
          : null,
    ),
  );
}
