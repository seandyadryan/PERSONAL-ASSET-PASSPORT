package com.personalassetpassport.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalassetpassport.app.R
import com.personalassetpassport.app.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun AssetForm(original: Asset?, vm: PassportViewModel, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val saver = remember {
        Saver<Asset, String>(
            save = { Json.encodeToString(it) },
            restore = { Json.decodeFromString(it) },
        )
    }
    var draft by
        rememberSaveable(original?.id, stateSaver = saver) {
            mutableStateOf(original ?: Asset(name = ""))
        }
    var price by
        rememberSaveable(original?.id) {
            mutableStateOf(
                original?.let {
                    java.math.BigDecimal.valueOf(it.priceMinor, 2).stripTrailingZeros()
                        .toPlainString().replace('.', priceDecimal(it.currency))
                } ?: ""
            )
        }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(original != null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val photo =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.photo(it) { name -> draft = draft.copy(photo = name) } }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (draft.photo != null)
            AsyncImage(
                vm.repo.file(draft.photo!!),
                s(R.string.photo),
                Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop,
            )
        OutlinedButton(
            enabled = !busy,
            onClick = { photo.launch(arrayOf("image/jpeg", "image/png")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(10.dp))
            Text(s(R.string.photo))
        }
        OutlinedTextField(
            draft.name,
            { if (it.length <= 160) draft = draft.copy(name = it) },
            Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text(s(R.string.name)) },
            singleLine = true,
            isError = submitted && draft.name.isBlank(),
            supportingText = { if (submitted && draft.name.isBlank()) Text(s(R.string.required)) },
        )
        SelectionField(
            s(R.string.category),
            draft.category,
            CATEGORIES,
            !busy,
            { context.category(it) },
        ) {
            draft = draft.copy(category = it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                price,
                { normalizePriceInput(it, draft.currency)?.let { value -> price = value } },
                Modifier.weight(1f),
                enabled = !busy,
                label = { Text(s(R.string.price)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = remember(draft.currency) { PriceVisualTransformation(draft.currency) },
                isError =
                    submitted &&
                        parseMinor(
                            price,
                            usesDecimalComma(draft.currency),
                        ) == null,
            )
            Box(Modifier.width(112.dp)) {
                SelectionField(s(R.string.currency), draft.currency, CURRENCIES, !busy) {
                    price = price.replace(priceDecimal(draft.currency), priceDecimal(it))
                    draft = draft.copy(currency = it)
                }
            }
        }
        DateInput(R.string.purchase_date, draft.purchaseDate) {
            draft = draft.copy(purchaseDate = it)
        }
        TextButton(onClick = { expanded = !expanded }) { Text(s(R.string.more_details)) }
        if (expanded) {
            Field(R.string.brand, draft.brand, !busy) { draft = draft.copy(brand = it) }
            Field(R.string.model, draft.model, !busy) { draft = draft.copy(model = it) }
            Field(R.string.serial, draft.serial, !busy) { draft = draft.copy(serial = it) }
            Field(R.string.vendor, draft.vendor, !busy) { draft = draft.copy(vendor = it) }
        }
        DateInput(R.string.warranty_end, draft.warrantyEnd) { draft = draft.copy(warrantyEnd = it) }
        DateInput(R.string.return_end, draft.returnEnd) { draft = draft.copy(returnEnd = it) }
        OutlinedTextField(
            draft.notes,
            { if (it.length <= 4000) draft = draft.copy(notes = it) },
            Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text(s(R.string.notes)) },
            minLines = 3,
        )
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            onClick = {
                submitted = true
                val amount =
                    parseMinor(
                        price,
                        usesDecimalComma(draft.currency),
                    )
                if (draft.name.isBlank()) return@Button
                if (amount == null) {
                    vm.notice("invalid_price")
                    return@Button
                }
                vm.save(draft.copy(name = draft.name.trim(), priceMinor = amount)) {
                    onSaved(draft.id)
                }
            },
        ) {
            Text(s(R.string.save))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(label: Int, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        { if (it.length <= 200) onChange(it) },
        Modifier.fillMaxWidth(),
        label = { Text(s(label)) },
        enabled = enabled,
        singleLine = true,
    )
}

@Composable
fun SelectionField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    display: (String) -> String = { it },
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            enabled = enabled,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(display(value))
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        expanded = false
                        onChange(option)
                    },
                )
            }
        }
    }
}
