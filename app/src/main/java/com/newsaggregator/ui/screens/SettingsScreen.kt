package com.newsaggregator.ui.screens

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsaggregator.NewsFlowApplication
import com.newsaggregator.ui.glass.NewsGlassCard
import com.newsaggregator.ui.glass.NewsGlassPill
import com.newsaggregator.ui.theme.LocalNewsColors
import com.newsaggregator.ui.theme.categoryColor
import com.newsaggregator.utils.DnsHelper
import com.newsaggregator.utils.RssParser
import com.newsaggregator.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.HashSet

// =====================================================================
// Settings (ports SettingsActivity)
// =====================================================================

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSources: () -> Unit) {
    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val readPrefs = remember { context.getSharedPreferences("read_articles", Context.MODE_PRIVATE) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var settingsVersion by remember { mutableStateOf(0) }

    val notifEnabled = remember(settingsVersion) {
        appPrefs.getBoolean("notifications_enabled", true)
    }
    val dataSaver = remember(settingsVersion) {
        appPrefs.getBoolean("data_saver", false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        // Top bar
        NewsGlassPill(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←", fontSize = 18.sp,
                    color = LocalNewsColors.current.textPrimary,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 10.dp)
                )
                Text("Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = LocalNewsColors.current.textPrimary)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp)
        ) {
            val label = @Composable { text: String ->
                Text(text, fontSize = 12.sp, color = LocalNewsColors.current.textSecondary)
            }

            NewsGlassCard(Modifier.fillMaxWidth()) {
                SettingsRow("Theme", themeLabel(context, settingsVersion)) { showThemeDialog = true }
                SettingsDivider()
                SettingsRow("Font Size", fontLabel(context, settingsVersion)) { showFontDialog = true }
                SettingsDivider()
                SettingsRow("DNS Resolver", DnsHelper.getDnsName(context)) { showDnsDialog = true }
            }

            Spacer(Modifier.height(10.dp))
            NewsGlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Breaking News Notifications", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = LocalNewsColors.current.textPrimary)
                        label("Push notification for breaking stories")
                    }
                    Switch(
                        checked = notifEnabled,
                        onCheckedChange = { checked ->
                            appPrefs.edit().putBoolean("notifications_enabled", checked).apply()
                            (context.applicationContext as NewsFlowApplication).scheduleNotifications()
                            Toast.makeText(
                                context,
                                if (checked) "Notifications enabled" else "Notifications disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = LocalNewsColors.current.accent)
                    )
                }
                SettingsDivider()
                SettingsRow(
                    "Check Interval",
                    intervalLabel(context, settingsVersion),
                    onClick = { showIntervalDialog = true }
                )
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Data Saver", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = LocalNewsColors.current.textPrimary)
                        label("Skip article images when possible")
                    }
                    Switch(
                        checked = dataSaver,
                        onCheckedChange = { checked ->
                            appPrefs.edit().putBoolean("data_saver", checked).apply()
                            Toast.makeText(
                                context,
                                if (checked) "Data saver on" else "Data saver off",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = LocalNewsColors.current.accent)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            NewsGlassCard(Modifier.fillMaxWidth()) {
                SettingsRow("Manage Sources", "Add or remove news sources") { onOpenSources() }
                SettingsDivider()
                SettingsRow("Clear Read History", "Reset all 'read' article marks") {
                    showClearDialog = true
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showThemeDialog) {
        OptionsDialog(
            "Theme", listOf("Dark", "Light"),
            onDismiss = { showThemeDialog = false }
        ) { which ->
            appPrefs.edit().putInt("theme_mode", which).apply()
            showThemeDialog = false
            (context as? Activity)?.recreate()
        }
    }
    if (showFontDialog) {
        OptionsDialog(
            "Article Font Size", listOf("Small (13sp)", "Medium (16sp)", "Large (20sp)"),
            onDismiss = { showFontDialog = false }
        ) { which ->
            val size = if (which == 0) 13 else if (which == 2) 20 else 16
            appPrefs.edit().putInt("font_size", size).apply()
            settingsVersion++
            showFontDialog = false
            Toast.makeText(context, "Font size set to ${size}sp", Toast.LENGTH_SHORT).show()
        }
    }
    if (showDnsDialog) {
        val options = listOf("System Default", "Google DNS (8.8.8.8)", "Cloudflare DNS (1.1.1.1)")
        OptionsDialog(
            "Choose DNS Resolver", options, initialSelection = DnsHelper.getDnsMode(context),
            onDismiss = { showDnsDialog = false }
        ) { which ->
            DnsHelper.setDnsMode(context, which)
            RssParser.initOkHttpClient(context)
            settingsVersion++
            showDnsDialog = false
            Toast.makeText(context, "DNS changed to ${options[which]}", Toast.LENGTH_SHORT).show()
        }
    }
    if (showIntervalDialog) {
        val options = listOf("15 minutes", "30 minutes", "1 hour", "2 hours", "4 hours")
        val values = listOf(15, 30, 60, 120, 240)
        val current = appPrefs.getInt("notification_interval", 120)
        val checked = values.indexOf(current).let { if (it >= 0) it else 3 }
        OptionsDialog(
            "Check Interval", options, initialSelection = checked,
            onDismiss = { showIntervalDialog = false }
        ) { which ->
            appPrefs.edit().putInt("notification_interval", values[which]).apply()
            settingsVersion++
            (context.applicationContext as NewsFlowApplication).scheduleNotifications()
            showIntervalDialog = false
            Toast.makeText(context, "Checking every ${options[which]}", Toast.LENGTH_SHORT).show()
        }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF15151F),
            title = { Text("Clear Read History", color = Color.White) },
            text = {
                Text(
                    "This will reset all 'read' article marks. Articles will no longer appear dimmed.",
                    color = Color(0xFFB8B8C8)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    readPrefs.edit().remove("read_urls").apply()
                    showClearDialog = false
                    Toast.makeText(context, "Read history cleared", Toast.LENGTH_SHORT).show()
                }) { Text("Clear", color = LocalNewsColors.current.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color(0xFF8C8C99))
                }
            }
        )
    }
}

private fun themeLabel(context: Context, version: Int): String =
    if (ThemeUtils.isLight(context)) "Light" else "Dark"

private fun fontLabel(context: Context, version: Int): String {
    val size = context
        .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getInt("font_size", 16)
    return when {
        size <= 13 -> "Small"
        size >= 20 -> "Large"
        else -> "Medium"
    } + " (${size}sp)"
}

private fun intervalLabel(context: Context, version: Int): String {
    val minutes = context
        .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getInt("notification_interval", 120)
    return when {
        minutes <= 15 -> "Every 15 minutes"
        minutes <= 30 -> "Every 30 minutes"
        minutes <= 60 -> "Every hour"
        minutes <= 120 -> "Every 2 hours"
        else -> "Every 4 hours"
    }
}

@Composable
fun SettingsRow(title: String, value: String?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = LocalNewsColors.current.textPrimary)
            if (value != null) {
                Text(value, fontSize = 12.sp, color = LocalNewsColors.current.textSecondary)
            }
        }
        Text("›", fontSize = 18.sp, color = LocalNewsColors.current.textTertiary)
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalNewsColors.current.divider)
    )
}

@Composable
fun OptionsDialog(
    title: String,
    options: List<String>,
    initialSelection: Int = -1,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelection) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15151F),
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                options.forEachIndexed { idx, opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = idx }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (selected == idx) LocalNewsColors.current.accent else Color.Transparent)
                                .border(1.5.dp, LocalNewsColors.current.textTertiary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {}
                        Spacer(Modifier.width(10.dp))
                        Text(opt, fontSize = 14.sp,
                            color = if (selected == idx) Color.White else Color(0xFFB8B8C8))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selected >= 0) onSelect(selected) },
                enabled = selected >= 0
            ) { Text("OK", color = LocalNewsColors.current.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8C8C99)) }
        }
    )
}

// =====================================================================
// Manage Sources (ports ManageSourcesActivity)
// =====================================================================

private class SourceItem(
    val name: String,
    val url: String,
    val category: String,
    val isBuiltIn: Boolean
)

@Composable
fun ManageSourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("news_sources", Context.MODE_PRIVATE) }
    var items by remember { mutableStateOf(loadSourceItems(prefs)) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun addCustomSource(name: String, url: String, category: String) {
        val urls = HashSet(prefs.getStringSet("custom_urls", emptySet()) ?: emptySet())
        val names = HashSet(prefs.getStringSet("custom_names", emptySet()) ?: emptySet())
        val cats = HashSet(prefs.getStringSet("custom_categories", emptySet()) ?: emptySet())
        urls.add(url)
        names.add(name)
        cats.add(category)
        prefs.edit()
            .putStringSet("custom_urls", urls)
            .putStringSet("custom_names", names)
            .putStringSet("custom_categories", cats)
            .apply()
        items = loadSourceItems(prefs)
        Toast.makeText(context, "Source added!", Toast.LENGTH_SHORT).show()
    }

    fun removeCustomSource(item: SourceItem) {
        if (item.isBuiltIn) return
        val urls = HashSet(prefs.getStringSet("custom_urls", emptySet()) ?: emptySet())
        val names = HashSet(prefs.getStringSet("custom_names", emptySet()) ?: emptySet())
        val cats = HashSet(prefs.getStringSet("custom_categories", emptySet()) ?: emptySet())
        urls.remove(item.url)
        names.remove(item.name)
        cats.remove(item.category)
        prefs.edit()
            .putStringSet("custom_urls", urls)
            .putStringSet("custom_names", names)
            .putStringSet("custom_categories", cats)
            .apply()
        items = loadSourceItems(prefs)
        Toast.makeText(context, "Source removed", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        NewsGlassPill(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←", fontSize = 18.sp,
                    color = LocalNewsColors.current.textPrimary,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 10.dp)
                )
                Text("Manage Sources", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = LocalNewsColors.current.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "+ Add", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = LocalNewsColors.current.accent,
                    modifier = Modifier.clickable { showAddDialog = true }
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp)
        ) {
            items.forEach { item ->
                NewsGlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), cornerRadius = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = LocalNewsColors.current.textPrimary)
                                Spacer(Modifier.width(6.dp))
                                if (item.isBuiltIn) {
                                    Text("BUILT-IN", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        color = LocalNewsColors.current.textTertiary)
                                }
                            }
                            Text(item.url, fontSize = 11.sp, color = LocalNewsColors.current.textSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(categoryColor(item.category), CircleShape)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(item.category, fontSize = 10.sp,
                                    color = LocalNewsColors.current.textTertiary)
                            }
                        }
                        if (!item.isBuiltIn) {
                            Text(
                                "🗑", fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable { removeCustomSource(item) }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, category ->
                showAddDialog = false
                Toast.makeText(context, "Finding feed...", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val resolved = withContext(Dispatchers.IO) {
                        try { RssParser.resolveRssUrl(url) } catch (_: Exception) { url }
                    }
                    addCustomSource(name, resolved, category)
                    Toast.makeText(context, "Feed found!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

private fun loadSourceItems(prefs: SharedPreferences): List<SourceItem> {
    val list = mutableListOf<SourceItem>()
    for (s in RssParser.getDefaultSources()) {
        list.add(SourceItem(s.name, s.rssUrl, s.category, true))
    }
    val urls = ArrayList(prefs.getStringSet("custom_urls", emptySet()) ?: emptySet())
    val names = ArrayList(prefs.getStringSet("custom_names", emptySet()) ?: emptySet())
    val cats = ArrayList(prefs.getStringSet("custom_categories", emptySet()) ?: emptySet())
    for (i in urls.indices) {
        val name = if (i < names.size) names[i] else "Custom Source"
        val cat = if (i < cats.size) cats[i] else "Custom"
        list.add(SourceItem(name, urls[i], cat, false))
    }
    return list
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15151F),
        title = { Text("Add News Source", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color(0xFF8C8C99)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RSS or site URL", color = Color(0xFF8C8C99)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)", color = Color(0xFF8C8C99)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var n = name.trim()
                var u = url.trim()
                var c = category.trim()
                if (n.isEmpty() || u.isEmpty()) {
                    onDismiss()
                    return@TextButton
                }
                if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                if (c.isEmpty()) c = "Custom"
                onAdd(n, u, c)
            }) { Text("Add", color = LocalNewsColors.current.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8C8C99)) }
        }
    )
}
