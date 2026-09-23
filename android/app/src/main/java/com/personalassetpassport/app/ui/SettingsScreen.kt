package com.personalassetpassport.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalassetpassport.app.R

@Composable
fun SettingsScreen(vm: PassportViewModel) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val email by vm.email.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(vm.prefs.getString("server", "") ?: "") }
    var backupMode by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf<CharArray?>(null) }
    DisposableEffect(Unit) { onDispose { secret?.fill('\u0000') } }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val password = secret
            secret = null
            if (uri != null && password != null)
                vm.run {
                    vm.app.backup.export(uri, password)
                    vm.notice("saved")
                }
            else password?.fill('\u0000')
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val password = secret
            secret = null
            if (uri != null && password != null)
                vm.run {
                    val old = vm.repo.dao.allReminders()
                    vm.app.backup.restore(uri, password)
                    try {
                        old.forEach { vm.app.scheduler.cancel(it.id) }
                        vm.app.scheduler.restore(vm.repo.dao.allReminders())
                        vm.notice("restored")
                    } catch (_: Exception) {
                        vm.notice("notification_failed")
                    }
                }
            else password?.fill('\u0000')
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted)
                vm.run {
                    vm.app.scheduler.restore(vm.repo.dao.allReminders())
                    vm.notice("saved")
                }
            else vm.notice("notification_denied")
        }
    if (backupMode != null)
        PasswordDialog(backupMode == "restore", onDismiss = { backupMode = null }) { password ->
            secret = password
            if (backupMode == "restore")
                restore.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
            else export.launch("passport-${System.currentTimeMillis()}.pap")
            backupMode = null
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(R.string.account) {
            Text(email ?: s(R.string.local_vault), style = MaterialTheme.typography.titleMedium)
            Text(s(R.string.local_note), style = MaterialTheme.typography.bodySmall)
            if (email == null)
                Button(enabled = !busy, onClick = { activity?.let(vm::signIn) }) {
                    Text(s(R.string.google))
                }
            else {
                OutlinedButton(enabled = !busy, onClick = { vm.run { vm.app.auth.signOut() } }) {
                    Text(s(R.string.sign_out))
                }
                OutlinedTextField(
                    url,
                    { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(s(R.string.server_url)) },
                    singleLine = true,
                    enabled = !busy,
                )
                TextButton(
                    enabled = !busy,
                    onClick = {
                        vm.run {
                            vm.app.auth.verifyServer(url)
                            vm.prefs.edit().putString("server", url).apply()
                            vm.notice("verified")
                        }
                    },
                ) {
                    Text(s(R.string.verify_server))
                }
            }
        }
        Section(R.string.language) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("en" to "English", "id" to "Indonesia").forEach { (code, name) ->
                    FilterChip(
                        enabled = !busy,
                        selected =
                            configuration.locales[0].language.let {
                                if (it == "in") "id" else it
                            } == code,
                        onClick = {
                            vm.prefs.edit().putString("language", code).commit()
                            activity?.recreate()
                        },
                        label = { Text(name) },
                    )
                }
            }
        }
        Section(R.string.backup) {
            Text(s(R.string.backup_body))
            OutlinedButton(enabled = !busy, onClick = { backupMode = "export" }) {
                Text(s(R.string.export_backup))
            }
            OutlinedButton(enabled = !busy, onClick = { backupMode = "restore" }) {
                Text(s(R.string.restore_backup))
            }
        }
        Section(R.string.reminders) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !vm.app.scheduler.allowed())
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else
                        vm.run {
                            vm.app.scheduler.restore(vm.repo.dao.allReminders())
                            vm.notice(
                                if (vm.app.scheduler.allowed()) "saved" else "notification_denied"
                            )
                        }
                },
            ) {
                Text(s(R.string.reschedule))
            }
        }
        Section(R.string.privacy) {
            Text(s(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
        }
        Text("Personal Asset Passport · Kotlin · 0.2.0", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordDialog(
    restoring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s(if (restoring) R.string.restore_backup else R.string.export_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (restoring) Text(s(R.string.restore_warning))
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(s(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (!restoring)
                    OutlinedTextField(
                        confirmation,
                        { confirmation = it },
                        label = { Text(s(R.string.password_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                error?.let { Text(s(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.length < 12) error = R.string.password_short
                    else if (!restoring && confirmation != password)
                        error = R.string.password_mismatch
                    else {
                        val chars = password.toCharArray()
                        password = ""
                        confirmation = ""
                        onConfirm(chars)
                    }
                }
            ) {
                Text(s(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    password = ""
                    confirmation = ""
                    onDismiss()
                }
            ) {
                Text(s(R.string.cancel))
            }
        },
    )
}
