import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../data/passport_store.dart';
import '../services/auth_service.dart';
import 'strings.dart';

const ink = Color(0xff173c36);
const paper = Color(0xfff7f8f4);
String money(BuildContext c, int minor, String currency) =>
    NumberFormat.currency(
      locale: Localizations.localeOf(c).toString(),
      name: currency,
      decimalDigits: currency == 'JPY' || currency == 'IDR' ? 0 : 2,
    ).format(minor / 100);
String dateLabel(BuildContext c, String? date) => date == null
    ? tr(c, 'unset')
    : DateFormat.yMMMd(
        Localizations.localeOf(c).toString(),
      ).format(DateTime.parse(date).toLocal());
void message(BuildContext c, String key) {
  if (c.mounted) {
    ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(tr(c, key))));
  }
}

String errorKey(Object e) => e is StoreException
    ? e.code
    : e is AuthFailure
    ? e.code
    : 'error';
Future<bool> confirm(BuildContext c, String title, String body) async =>
    await showDialog<bool>(
      context: c,
      builder: (context) => AlertDialog(
        title: Text(tr(c, title)),
        content: Text(tr(c, body)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(tr(c, 'cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(tr(c, 'confirm')),
          ),
        ],
      ),
    ) ??
    false;

class Section extends StatelessWidget {
  final String title;
  final Widget child;
  const Section(this.title, this.child, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 12),
        child,
      ],
    ),
  );
}

class DateField extends StatelessWidget {
  final String label;
  final String? value;
  final ValueChanged<String?> onChanged;
  const DateField({
    super.key,
    required this.label,
    this.value,
    required this.onChanged,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: InputDecorator(
      decoration: InputDecoration(labelText: tr(context, label)),
      child: Row(
        children: [
          Expanded(
            child: InkWell(
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: value == null
                      ? DateTime.now()
                      : DateTime.parse(value!),
                  firstDate: DateTime(1900),
                  lastDate: DateTime(2200),
                );
                if (picked != null) {
                  onChanged(DateFormat('yyyy-MM-dd').format(picked));
                }
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(dateLabel(context, value)),
              ),
            ),
          ),
          if (value != null)
            IconButton(
              tooltip: tr(context, 'clear'),
              onPressed: () => onChanged(null),
              icon: const Icon(Icons.close, size: 18),
            )
          else
            const Icon(Icons.calendar_today_outlined, size: 18),
        ],
      ),
    ),
  );
}
