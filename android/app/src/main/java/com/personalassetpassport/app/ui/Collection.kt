package com.personalassetpassport.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.personalassetpassport.app.R
import com.personalassetpassport.app.data.*

@Composable
fun AssetCollection(
    assets: List<Asset>,
    vm: PassportViewModel,
    dashboard: Boolean,
    onAsset: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var priceSort by rememberSaveable { mutableStateOf(false) }
    val visible =
        remember(assets, query, category, priceSort) {
            assets
                .filter { it.matches(query) && (category.isEmpty() || it.category == category) }
                .let {
                    if (priceSort)
                        it.sortedWith(
                            compareBy<Asset> { a -> a.currency }
                                .thenByDescending { a -> a.priceMinor }
                        )
                    else it
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (dashboard) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        s(R.string.hello),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Text(s(R.string.subtitle), color = Color(0xFF64756D))
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ink)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        s(R.string.collection),
                        color = Sage,
                        letterSpacing = 2.sp,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${assets.size.toString().padStart(2, '0')} / 10",
                        fontSize = 46.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(s(R.string.protected_assets), color = Sage)
                    HorizontalDivider(color = Sage.copy(alpha = .2f))
                    Text(
                        s(R.string.device_only),
                        color = Sage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(
                        s(R.string.warranty_soon),
                        assets.count { warrantyStatus(it.warrantyEnd) == WarrantyStatus.EXPIRING },
                        Modifier.weight(1f),
                    )
                    Metric(
                        s(R.string.returns_soon),
                        assets.count { daysUntil(it.returnEnd)?.let { d -> d in 0..7 } == true },
                        Modifier.weight(1f),
                    )
                }
            }
            if (assets.isNotEmpty())
                item {
                    Section(R.string.purchase_value) {
                        assets
                            .groupBy { it.currency }
                            .forEach { (currency, values) ->
                                Text(
                                    context.money(values.sumOf { it.priceMinor }, currency),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Ink,
                                )
                            }
                    }
                }
            item {
                Text(s(R.string.recent), style = MaterialTheme.typography.titleLarge, color = Ink)
            }
        } else {
            item {
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(s(R.string.search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (listOf("") + CATEGORIES).forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = {
                                Text(if (c.isEmpty()) s(R.string.all) else context.category(c))
                            },
                        )
                    }
                }
            }
            item {
                TextButton(onClick = { priceSort = !priceSort }) {
                    Icon(Icons.AutoMirrored.Outlined.List, null)
                    Spacer(Modifier.width(8.dp))
                    Text(s(if (priceSort) R.string.price_sort else R.string.newest))
                }
            }
        }
        if (assets.isEmpty())
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.ShoppingCart,
                        null,
                        Modifier.size(48.dp),
                        tint = Color(0xFF93A890),
                    )
                    Text(s(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
                    Text(s(R.string.empty_body))
                }
            }
        else if (!dashboard && visible.isEmpty()) item { Text(s(R.string.no_results)) }
        items(if (dashboard) assets.take(5) else visible, key = { it.id }) { a ->
            AssetRow(a, vm) { onAsset(a.id) }
        }
    }
}

@Composable
private fun Metric(title: String, count: Int, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.DateRange, null, tint = Color(0xFF8C744A))
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AssetRow(a: Asset, vm: PassportViewModel, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick,
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (a.photo != null)
                AsyncImage(
                    vm.repo.file(a.photo),
                    null,
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            else
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Sage),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Build, null, tint = Ink)
                }
            Column(Modifier.weight(1f)) {
                Text(a.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${context.category(a.category)} · ${context.money(a.priceMinor, a.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Composable
fun ReminderList(reminders: List<Reminder>, vm: PassportViewModel, onAsset: (String) -> Unit) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        if (reminders.isEmpty())
            item { Text(s(R.string.no_reminders), Modifier.padding(vertical = 30.dp)) }
        items(reminders, key = { it.id }) { r ->
            ListItem(
                headlineContent = { Text(r.title) },
                supportingContent = {
                    Text(
                        "${context.instant(r.dueAt)} · ${s(if (r.delivered) R.string.delivered else R.string.pending)}"
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Notifications, null) },
                trailingContent = {
                    IconButton(onClick = { vm.removeReminder(r.id) }) {
                        Icon(Icons.Outlined.Close, s(R.string.cancel))
                    }
                },
                modifier = Modifier.clickable { onAsset(r.assetId) },
            )
        }
    }
}
