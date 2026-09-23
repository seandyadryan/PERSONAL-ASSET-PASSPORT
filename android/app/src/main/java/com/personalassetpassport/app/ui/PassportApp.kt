package com.personalassetpassport.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalassetpassport.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportApp(vm: PassportViewModel, initialAsset: String? = null) = PassportTheme {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val started by vm.started.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val failed by vm.storageFailed.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf(if (initialAsset == null) "main" else "detail") }
    var selected by rememberSaveable { mutableStateOf(initialAsset) }
    val asset = assets?.firstOrNull { it.id == selected }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.notices.collect { snackbar.showSnackbar(context.word(it)) } }
    BackHandler(page != "main" && !busy) {
        page = if (page == "form" && selected != null) "detail" else "main"
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (started)
                TopAppBar(
                    title = {
                        Text(
                            if (page == "main") "Passport"
                            else if (page == "form")
                                s(if (selected == null) R.string.add else R.string.edit)
                            else asset?.name ?: "Passport",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        if (page != "main")
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    page =
                                        if (page == "form" && selected != null) "detail" else "main"
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, s(R.string.back))
                            }
                    },
                    actions = {
                        if (page == "main")
                            SuggestionChip(
                                onClick = { vm.notice("limit") },
                                label = { Text(s(R.string.free_plan)) },
                                modifier = Modifier.padding(end = 16.dp),
                            )
                    },
                )
        },
        bottomBar = {
            if (started && page == "main")
                NavigationBar {
                    listOf(
                            R.string.home to Icons.Outlined.Home,
                            R.string.assets to Icons.Outlined.ShoppingCart,
                            R.string.reminders to Icons.Outlined.Notifications,
                            R.string.settings to Icons.Outlined.Settings,
                        )
                        .forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == index,
                                enabled = !busy,
                                onClick = { tab = index },
                                icon = { Icon(icon, null) },
                                label = { Text(s(label)) },
                            )
                        }
                }
        },
        floatingActionButton = {
            if (started && page == "main" && tab < 2 && assets != null)
                ExtendedFloatingActionButton(
                    onClick = {
                        selected = null
                        page = "form"
                    },
                    containerColor = Ink,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_asset_fab"),
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(s(R.string.add)) },
                )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                !started -> WelcomeContent(busy, { activity?.let(vm::signIn) }, vm::startLocal)
                failed ->
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s(R.string.storage_failed))
                    }
                assets == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                page == "form" ->
                    AssetForm(asset, vm) { savedId ->
                        selected = savedId
                        page = "detail"
                    }
                page == "detail" && asset != null ->
                    AssetDetails(
                        asset,
                        vm,
                        onEdit = { page = "form" },
                        onDeleted = {
                            page = "main"
                            selected = null
                        },
                    )
                page == "detail" ->
                    Box(Modifier.fillMaxSize().padding(24.dp)) { Text(s(R.string.no_results)) }
                tab == 3 -> SettingsScreen(vm)
                tab == 2 ->
                    ReminderList(
                        reminders,
                        vm,
                        onAsset = {
                            selected = it
                            page = "detail"
                        },
                    )
                else ->
                    AssetCollection(
                        assets.orEmpty(),
                        vm,
                        dashboard = tab == 0,
                        onAsset = {
                            selected = it
                            page = "detail"
                        },
                    )
            }
        }
    }
}

@Composable
fun WelcomeContent(busy: Boolean = false, onGoogle: () -> Unit, onLocal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = Ink)
            Spacer(Modifier.width(10.dp))
            Text(
                "PERSONAL ASSET\nPASSPORT",
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(28.dp)).background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                null,
                tint = Color.White.copy(alpha = .07f),
                modifier = Modifier.size(220.dp).align(Alignment.TopEnd),
            )
            Column(
                Modifier.rotate(-5f)
                    .size(145.dp, 160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Sage)
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Ink, modifier = Modifier.size(36.dp))
                Text(
                    "ASSET\nPASSPORT",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                HorizontalDivider(color = Ink)
            }
        }
        Text(
            s(R.string.tagline),
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
            color = Ink,
        )
        Text(
            s(R.string.intro),
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF64756D),
        )
        Button(
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) {
            Icon(Icons.Outlined.AccountCircle, null)
            Spacer(Modifier.width(10.dp))
            Text(s(R.string.google))
        }
        OutlinedButton(
            onClick = onLocal,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) {
            Text(s(R.string.local))
        }
        Text(
            s(R.string.local_note),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64756D),
        )
    }
}

@Preview(name = "Onboarding", showBackground = true, widthDp = 430, heightDp = 960)
@Composable
private fun WelcomePreview() {
    PassportTheme { WelcomeContent(onGoogle = {}, onLocal = {}) }
}
