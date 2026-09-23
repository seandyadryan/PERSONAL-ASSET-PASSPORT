package com.personalassetpassport.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalassetpassport.app.R
import com.personalassetpassport.app.data.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetails(asset: Asset, vm: PassportViewModel, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable(asset.id) { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var addingReminder by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val docs by
        remember(asset.id) { vm.documents(asset.id) }.collectAsStateWithLifecycle(emptyList())
    val events by
        remember(asset.id) { vm.events(asset.id) }.collectAsStateWithLifecycle(emptyList())
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.attach(asset.id, uri)
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) vm.notice("notification_denied")
        }
    if (deleting)
        ConfirmDialog(R.string.delete, R.string.delete_body, { deleting = false }) {
            deleting = false
            vm.delete(asset.id, onDeleted)
        }
    if (addingReminder) ReminderDialog(asset, vm) { addingReminder = false }
    Column {
        SecondaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.home, R.string.documents, R.string.timeline).forEachIndexed { i, label
                ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(s(label)) })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                0 -> {
                    if (asset.photo != null)
                        AsyncImage(
                            vm.repo.file(asset.photo),
                            null,
                            Modifier.fillMaxWidth().height(210.dp),
                            contentScale = ContentScale.Crop,
                        )
                    Text(
                        context.category(asset.category),
                        color = Ink,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Text(
                        context.money(asset.priceMinor, asset.currency),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                    )
                    Info(R.string.purchase_date, context.date(asset.purchaseDate))
                    Info(
                        R.string.warranty_end,
                        "${context.date(asset.warrantyEnd)} · ${context.status(warrantyStatus(asset.warrantyEnd))}",
                    )
                    Info(R.string.return_end, context.date(asset.returnEnd))
                    listOf(
                            R.string.brand to asset.brand,
                            R.string.model to asset.model,
                            R.string.serial to asset.serial,
                            R.string.vendor to asset.vendor,
                            R.string.notes to asset.notes,
                        )
                        .filter { it.second.isNotBlank() }
                        .forEach { (label, value) -> Info(label, value) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !vm.app.scheduler.allowed())
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            addingReminder = true
                        },
                    ) {
                        Icon(Icons.Outlined.Notifications, null)
                        Spacer(Modifier.width(8.dp))
                        Text(s(R.string.new_reminder))
                    }
                    reminders
                        .filter { it.assetId == asset.id }
                        .forEach { r ->
                            ListItem(
                                headlineContent = { Text(r.title) },
                                supportingContent = { Text(context.instant(r.dueAt)) },
                                trailingContent = {
                                    IconButton(
                                        enabled = !busy,
                                        onClick = { vm.removeReminder(r.id) },
                                    ) {
                                        Icon(Icons.Outlined.Close, s(R.string.cancel))
                                    }
                                },
                            )
                        }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = !busy, onClick = onEdit) { Text(s(R.string.edit)) }
                        TextButton(enabled = !busy, onClick = { deleting = true }) {
                            Text(s(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                1 -> {
                    Text(s(R.string.no_documents))
                    Button(
                        enabled = !busy,
                        onClick = {
                            picker.launch(
                                arrayOf("image/jpeg", "image/png", "application/pdf", "text/plain")
                            )
                        },
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Text(s(R.string.attach))
                    }
                    docs.forEach { doc ->
                        fun launch(share: Boolean) {
                            try {
                                val uri =
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.files",
                                        vm.repo.file(doc.file),
                                    )
                                val intent =
                                    if (share)
                                        Intent(Intent.ACTION_SEND)
                                            .setType(doc.mime)
                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, doc.mime)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.clipData = ClipData.newRawUri(doc.name, uri)
                                context.startActivity(Intent.createChooser(intent, doc.name))
                            } catch (_: android.content.ActivityNotFoundException) {
                                vm.notice("no_viewer")
                            } catch (_: Exception) {
                                vm.notice("invalid_file")
                            }
                        }
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(doc.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${doc.mime} · ${doc.size / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row {
                                    TextButton(onClick = { launch(false) }) {
                                        Text(s(R.string.open_document))
                                    }
                                    IconButton(onClick = { launch(true) }) {
                                        Icon(Icons.Outlined.Share, s(R.string.share_document))
                                    }
                                }
                            }
                        }
                    }
                }
                else ->
                    events.forEach { event ->
                        ListItem(
                            headlineContent = { Text(context.word(event.kind)) },
                            supportingContent = { Text(context.instant(event.createdAt)) },
                            leadingContent = { Icon(Icons.Outlined.DateRange, null) },
                        )
                    }
            }
        }
    }
}

@Composable
private fun Info(label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            s(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer { Text(value) }
    }
}

@Composable
private fun ReminderDialog(asset: Asset, vm: PassportViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(asset.name) }
    var day by rememberSaveable { mutableStateOf<String?>(LocalDate.now().plusDays(1).toString()) }
    var hour by rememberSaveable { mutableIntStateOf(9) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s(R.string.new_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { if (it.length <= 160) title = it },
                    label = { Text(s(R.string.reminder_title)) },
                )
                DateInput(R.string.due, day) { day = it }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                                context,
                                { _, h, m ->
                                    hour = h
                                    minute = m
                                },
                                hour,
                                minute,
                                android.text.format.DateFormat.is24HourFormat(context),
                            )
                            .show()
                    }
                ) {
                    Text(
                        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (day == null || title.isBlank()) {
                        vm.notice("required")
                        return@TextButton
                    }
                    val instant =
                        LocalDate.parse(day)
                            .atTime(hour, minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    vm.addReminder(
                        Reminder(assetId = asset.id, title = title.trim(), dueAt = instant),
                        onDismiss,
                    )
                }
            ) {
                Text(s(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s(R.string.cancel)) } },
    )
}
