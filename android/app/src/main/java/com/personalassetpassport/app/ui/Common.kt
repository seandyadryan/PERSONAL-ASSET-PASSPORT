package com.personalassetpassport.app.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalassetpassport.app.R
import com.personalassetpassport.app.data.WarrantyStatus
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

val Ink = Color(0xFF173C36)
val Paper = Color(0xFFF7F8F4)
val Sage = Color(0xFFE2EBCE)

@Composable fun s(@StringRes id: Int) = stringResource(id)

fun Context.word(key: String): String =
    resources.getIdentifier(key, "string", packageName).takeIf { it != 0 }?.let(::getString)
        ?: getString(R.string.generic_error)

fun Context.category(value: String) = word("cat_${value.lowercase(Locale.ROOT)}")

fun Context.status(value: WarrantyStatus) = word("status_${value.name.lowercase(Locale.ROOT)}")

fun Context.money(minor: Long, currency: String): String =
    NumberFormat.getCurrencyInstance(resources.configuration.locales[0])
        .apply { this.currency = Currency.getInstance(currency) }
        .format(BigDecimal.valueOf(minor, 2))

fun Context.date(value: String?): String =
    value?.let {
        LocalDate.parse(it)
            .format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(resources.configuration.locales[0])
            )
    } ?: getString(R.string.not_set)

fun Context.instant(value: Long): String =
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(resources.configuration.locales[0])
        )

@Composable
fun PassportTheme(content: @Composable () -> Unit) =
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Ink,
                onPrimary = Color.White,
                secondaryContainer = Sage,
                surface = Paper,
                background = Paper,
            ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )

@Composable
fun Section(@StringRes label: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Text(s(label), style = MaterialTheme.typography.titleLarge, color = Ink)
        content()
    }
}

@Composable
fun DateInput(@StringRes label: Int, value: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                val date = value?.let(LocalDate::parse) ?: LocalDate.now()
                DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onChange(LocalDate.of(year, month + 1, day).toString())
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    )
                    .apply {
                        datePicker.minDate =
                            LocalDate.of(1900, 1, 1)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        datePicker.maxDate =
                            LocalDate.of(2200, 12, 31)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                    }
                    .show()
            },
        ) {
            Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s(label), style = MaterialTheme.typography.labelSmall)
                Text(context.date(value))
            }
        }
        if (value != null)
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Outlined.Close, s(R.string.clear))
            }
    }
}

@Composable
fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s(title)) },
        text = { Text(s(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(s(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s(R.string.cancel)) } },
    )
