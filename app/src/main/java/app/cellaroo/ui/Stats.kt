package app.cellaroo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.cellaroo.BuildConfig
import app.cellaroo.MainViewModel
import app.cellaroo.ai.currentYear
import app.cellaroo.core.Readiness
import app.cellaroo.core.ReadinessCalc
import app.cellaroo.data.LocationEntity
import app.cellaroo.data.Settings
import app.cellaroo.data.WineRow

data class CellarStats(
    val bottles: Int,
    val labels: Int,
    val costBasis: Double,
    val pricedBottles: Int,
    val marketValue: Double,
    val valuedBottles: Int,
    /** Market value minus cost for bottles that have both figures. */
    val gain: Double?,
    val avgScore: Double?,
    val byCountry: List<Pair<String, Int>>,
    val byType: List<Pair<String, Int>>,
    val byReadiness: List<Pair<Readiness, Int>>,
    val byDecade: List<Pair<String, Int>>,
    /** Bottles at peak in each of the next ten years. */
    val maturity: List<Pair<Int, Int>>,
)

fun computeStats(rows: List<WineRow>, year: Int): CellarStats {
    val live = rows.filter { it.qty > 0 }
    fun <K> tally(key: (WineRow) -> K): List<Pair<K, Int>> =
        live.groupBy(key).map { (k, v) -> k to v.sumOf { it.qty } }.sortedByDescending { it.second }
    var gain = 0.0
    var gainBottles = 0
    for (r in live) {
        val m = r.wine.marketPriceUsd ?: continue
        for (b in r.bottles) {
            val paid = b.purchasePriceUsd ?: continue
            gain += m - paid
            gainBottles++
        }
    }
    val scored = live.filter { it.avgScore != null }
    return CellarStats(
        bottles = live.sumOf { it.qty },
        labels = live.size,
        costBasis = live.sumOf { it.costBasis },
        pricedBottles = live.sumOf { it.pricedBottles },
        marketValue = live.sumOf { it.marketValue ?: 0.0 },
        valuedBottles = live.filter { it.wine.marketPriceUsd != null }.sumOf { it.qty },
        gain = gain.takeIf { gainBottles > 0 },
        avgScore = scored.takeIf { it.isNotEmpty() }?.let { s -> s.sumOf { (it.avgScore ?: 0.0) * it.qty } / s.sumOf { it.qty } },
        byCountry = tally { it.wine.country.ifBlank { "Unknown" } },
        byType = tally { it.wine.wineType.label },
        byReadiness = tally { it.readiness(year) },
        byDecade = tally { r -> r.wine.drinkUntilYear?.let { "${it / 10 * 10}s" } ?: "No window" }.sortedBy { it.first },
        maturity = (year until year + 10).map { y ->
            y to live.filter { ReadinessCalc.readiness(it.window, y) == Readiness.AT_PEAK }.sumOf { it.qty }
        },
    )
}

@Composable
fun StatsScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    val changes by vm.changeSets.collectAsStateWithLifecycle()
    val year = currentYear()
    val stats = remember(state.rows) { computeStats(state.rows, year) }
    var confirmRestore by remember { mutableStateOf(false) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.exportZip(uri) }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportJson(uri) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.exportCsv(uri) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restore(uri) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Stats & backup") {
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Bottles", stats.bottles.toString(), "${stats.labels} labels", Modifier.weight(1f))
                Kpi("Avg critic score", score(stats.avgScore), "weighted by bottles", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Paid", money(stats.costBasis), "${stats.pricedBottles} of ${stats.bottles} bottles priced", Modifier.weight(1f))
                Kpi("Market value", money(stats.marketValue), "${stats.valuedBottles} of ${stats.bottles} bottles valued", Modifier.weight(1f))
            }
            val g = stats.gain
            if (g != null) {
                Text(
                    "On bottles with both prices: ${if (g >= 0) "+" else "−"}${money(kotlin.math.abs(g))} against what you paid.",
                    color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (stats.bottles > 0) {
                SectionCard("Readiness") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Donut(stats.byReadiness.map { Palette.readiness(it.first) to it.second }, Modifier.size(110.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((r, n) in stats.byReadiness) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Dot(Palette.readiness(r))
                                    Text("${r.label.lowercase().replaceFirstChar { it.uppercase() }}: $n", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                SectionCard("At peak, next 10 years") { Columns(stats.maturity.map { it.first.toString().takeLast(2) to it.second }, Palette.Emerald) }
                SectionCard("By country") { Bars(stats.byCountry, Palette.Gold) }
                SectionCard("By type") { Bars(stats.byType, Palette.Rose) }
                SectionCard("Window closes in") { Bars(stats.byDecade, Palette.Teal) }
            }
            SectionCard("Backup") {
                Text("A backup ZIP holds the whole cellar and its photos. Restore replaces everything in the app.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { saveZip.launch("cellaroo-backup.zip") }) { Text("Export backup") }
                    OutlinedButton(onClick = { saveCsv.launch("cellaroo.csv") }) { Text("CSV") }
                    OutlinedButton(onClick = { saveJson.launch("cellaroo.json") }) { Text("JSON") }
                    OutlinedButton(onClick = { confirmRestore = true }) { Text("Restore") }
                }
            }
            SectionCard("Cellar setup") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate("locations") }) { Text("Shelves") }
                    OutlinedButton(onClick = { nav.navigate("settings") }) { Text("API key & settings") }
                    OutlinedButton(onClick = { vm.estimateAll() }) { Text("Estimate missing windows") }
                }
            }
            if (changes.isNotEmpty()) {
                SectionCard("Recent changes") {
                    for (c in changes) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.label, style = MaterialTheme.typography.bodyMedium)
                                Text(day(c.createdAt), color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.undo(c.id) }) { Text("Undo") }
                        }
                    }
                }
            }
            Text("Cellaroo ${BuildConfig.VERSION_NAME}", color = Palette.Outline, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = { Text("Everything currently in the app is replaced by the backup's contents. Export a backup first if you want to keep the current cellar.") },
            confirmButton = {
                Button(onClick = { confirmRestore = false; open.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Kpi(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Palette.Surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Palette.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Horizontal bars; one colour, since the bars differ only by length. */
@Composable
private fun <K> Bars(data: List<Pair<K, Int>>, color: Color) {
    val max = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    for ((k, n) in data.take(8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k.toString(), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f).height(14.dp)) {
                Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
            }
            Text(n.toString(), Modifier.width(34.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        }
    }
}

/** Vertical columns with the value above and a label below each one. */
@Composable
private fun Columns(data: List<Pair<String, Int>>, color: Color) {
    val max = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for ((label, n) in data) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(n.toString(), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                Box(
                    Modifier.fillMaxWidth().height((80 * n / max).coerceAtLeast(2).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color)
                )
                Text("'$label", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun Donut(parts: List<Pair<Color, Int>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Canvas(modifier) {
        val stroke = size.minDimension * 0.2f
        var start = -90f
        for ((color, n) in parts) {
            val sweep = 360f * n / total
            drawArc(
                color = color, startAngle = start, sweepAngle = (sweep - 2f).coerceAtLeast(1f), useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard("Gemini API key") {
                Text(
                    if (settings.hasKey) "A key is stored on this device, encrypted with the Android Keystore. It is not included in backups."
                    else "Paste the key from Google AI Studio. It stays on this device, encrypted, and is sent only to Google's Gemini API.",
                    color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it.trim() }, singleLine = true,
                    label = { Text(if (settings.hasKey) "Replace key" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setApiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
                    OutlinedButton(onClick = { vm.testKey() }, enabled = settings.hasKey) { Text("Test key") }
                    if (settings.hasKey) TextButton(onClick = { vm.setApiKey("") }) { Text("Remove") }
                }
            }
            SectionCard("Model") {
                Text("Used for reading labels, parsing pasted text and estimates. Test the key to list the models it can use.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(model, { model = it }, singleLine = true, label = { Text("Model id") }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setModel(model) }, enabled = model.isNotBlank() && model != settings.model) { Text("Use this model") }
                    TextButton(onClick = { vm.setModel(Settings.DEFAULT_MODEL) }) { Text("Reset to ${Settings.DEFAULT_MODEL}") }
                }
                val flash = settings.knownModels.filter { "flash" in it && "image" !in it && "tts" !in it && "live" !in it && "audio" !in it }
                if (flash.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in flash.take(12)) FilterChip(selected = m == settings.model, onClick = { vm.setModel(m) }, label = { Text(m) })
                    }
                }
                Text("If the chosen model is unavailable, ${Settings.FALLBACK_MODEL} is tried automatically.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("Estimates") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Estimate new wines automatically")
                        Text(
                            "After you add bottles, Gemini estimates a drinking window, serving style and pairings. They are marked ESTIMATE and never replace pasted or typed values. Prices and critic scores are never estimated.",
                            color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = settings.estimatesEnabled, onCheckedChange = { vm.setEstimatesEnabled(it) })
                }
            }
            SectionCard("What leaves this device") {
                Text(
                    "Your cellar lives only on this phone. Photos you scan, text you parse and the wine names you ask estimates for are sent to Google's Gemini API with your key. On Gemini's free tier, Google may use that content to improve its products.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun LocationsScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<LocationEntity?>(null) }
    var deleting by remember { mutableStateOf<LocationEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Shelves", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("A shelf is any place you can photograph in one go. Shelf checks compare a photo with the bottles assigned to it.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
            for (l in state.locations) {
                val n = state.rows.sumOf { r -> r.bottles.count { it.locationId == l.id } }
                Card(colors = CardDefaults.cardColors(containerColor = Palette.Surface), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text("$n bottles · last checked ${l.lastReconciledAt?.let { day(it) } ?: "never"}", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { renaming = l }) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
                        IconButton(onClick = { deleting = l }, enabled = state.locations.size > 1) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("New shelf name") }, modifier = Modifier.weight(1f))
                Button(onClick = { vm.addLocation(name); name = "" }, enabled = name.isNotBlank()) { Text("Add") }
            }
        }
    }
    val r = renaming
    if (r != null) {
        var newName by remember(r.id) { mutableStateOf(r.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename shelf") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true) },
            confirmButton = { Button(onClick = { vm.renameLocation(r, newName); renaming = null }, enabled = newName.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    val d = deleting
    if (d != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${d.name}”?") },
            text = { Text("Its bottles stay in the cellar but lose their shelf.") },
            confirmButton = { Button(onClick = { vm.deleteLocation(d.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
