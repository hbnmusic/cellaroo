package app.cellaroo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.cellaroo.MainViewModel
import app.cellaroo.R
import app.cellaroo.ai.currentYear
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.Readiness
import app.cellaroo.core.ReadinessCalc
import app.cellaroo.core.WineType
import app.cellaroo.data.BottleEntity
import app.cellaroo.data.LocationEntity
import app.cellaroo.data.WineRow

enum class CellarFilter(val label: String) {
    ALL("All"), AT_PEAK("At peak"), READY("Ready now"), HOLD("Hold"), PAST("Past peak"), NO_WINDOW("No window"),
    RED("Red"), WHITE("White"), SPARKLING("Sparkling"), OTHER_TYPES("Rosé & other")
}

enum class CellarSort(val label: String) {
    DRINK_SOONEST("Drink soonest"), TOP_RATED("Highest rated"), VINTAGE_OLD("Vintage: oldest"),
    VINTAGE_NEW("Vintage: newest"), VALUE("Estimated value"), RECENT("Recently added")
}

fun filterRows(rows: List<WineRow>, filter: CellarFilter, query: String, year: Int): List<WineRow> {
    val q = query.trim().lowercase()
    return rows.filter { r ->
        val ok = when (filter) {
            CellarFilter.ALL -> true
            CellarFilter.AT_PEAK -> r.readiness(year) == Readiness.AT_PEAK
            CellarFilter.READY -> r.readiness(year) == Readiness.READY
            CellarFilter.HOLD -> r.readiness(year) == Readiness.HOLD
            CellarFilter.PAST -> r.readiness(year) == Readiness.PAST_PEAK
            CellarFilter.NO_WINDOW -> r.readiness(year) == Readiness.UNKNOWN
            CellarFilter.RED -> r.wine.wineType == WineType.RED
            CellarFilter.WHITE -> r.wine.wineType == WineType.WHITE
            CellarFilter.SPARKLING -> r.wine.wineType == WineType.SPARKLING
            CellarFilter.OTHER_TYPES -> r.wine.wineType !in setOf(WineType.RED, WineType.WHITE, WineType.SPARKLING)
        }
        ok && (q.isEmpty() || listOf(
            r.wine.producer, r.wine.wineName, r.wine.appellationRegion, r.wine.country, r.wine.vintage?.toString() ?: "nv",
            r.grapes.joinToString(" "), r.wine.myNotes.orEmpty(), r.wine.referenceNotes.orEmpty(), r.wine.styleSummary.orEmpty(),
        ).any { it.lowercase().contains(q) })
    }
}

fun sortRows(rows: List<WineRow>, sort: CellarSort): List<WineRow> = when (sort) {
    CellarSort.DRINK_SOONEST -> rows.sortedWith(compareBy({ it.wine.drinkUntilYear ?: 9999 }, { it.wine.drinkFromYear ?: 9999 }))
    CellarSort.TOP_RATED -> rows.sortedByDescending { it.quality100 ?: 0.0 }
    CellarSort.VINTAGE_OLD -> rows.sortedBy { it.wine.vintage ?: 9999 }
    CellarSort.VINTAGE_NEW -> rows.sortedByDescending { it.wine.vintage ?: 0 }
    CellarSort.VALUE -> rows.sortedByDescending { it.wine.marketPriceUsd ?: 0.0 }
    CellarSort.RECENT -> rows.sortedByDescending { it.wine.createdAt }
}

@Composable
fun CellarScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CellarFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(CellarSort.DRINK_SOONEST) }
    var sortOpen by remember { mutableStateOf(false) }
    var consuming by remember { mutableStateOf<WineRow?>(null) }
    val year = currentYear()
    val all = state.inCellar
    val rows = remember(all, filter, query, sort) { sortRows(filterRows(all, filter, query, year), sort) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cellaroo") {
            Text("${all.sumOf { it.qty }} bottles", color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort") }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    for (s in CellarSort.entries) {
                        DropdownMenuItem(
                            text = { Text(if (s == sort) "✓ ${s.label}" else s.label) },
                            onClick = { sort = s; sortOpen = false },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search producer, wine, region, grape, notes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") } },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (f in CellarFilter.entries) {
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        when {
            !state.loaded -> Unit
            all.isEmpty() -> EmptyState(
                "Your cellar is empty",
                "Photograph your bottles and Cellaroo reads the labels. You can also paste text or add a wine by hand.",
                "Scan bottles",
            ) { nav.openTab("scan") }
            rows.isEmpty() -> EmptyState("Nothing matches", "No wine fits this search and filter.", "Show all") {
                query = ""; filter = CellarFilter.ALL
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.wine.id }) { r ->
                    WineCard(
                        row = r, year = year, vm = vm,
                        onClick = { nav.navigate("wine/${r.wine.id}") },
                        onMinus = { consuming = r },
                        onPlus = { vm.addBottle(r.wine.id, r.bottles.lastOrNull()?.locationId ?: state.locations.firstOrNull()?.id, null) },
                    )
                }
            }
        }
    }
    consuming?.let { r -> ConsumeDialog(vm, r, r.bottles.minByOrNull { it.addedAt }, onDismiss = { consuming = null }) }
}

@Composable
fun EmptyState(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_glass), contentDescription = null, tint = Palette.Outline)
        Gap(12.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Gap()
        Text(body, color = Palette.TextDim, textAlign = TextAlign.Center)
        Gap(16.dp)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun WineCard(row: WineRow, year: Int, vm: MainViewModel, onClick: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val w = row.wine
    val readiness = row.readiness(year)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WineThumb(vm.images.file(row.thumb), 52.dp, 76.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(w.vintage?.toString() ?: "NV", Palette.Gold)
                    Text(w.producer.ifBlank { w.wineName }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (w.producer.isNotBlank() && w.wineName.isNotBlank()) {
                    Text(w.wineName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOf(w.appellationRegion, w.wineType.label).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Palette.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadinessPill(readiness, ReadinessCalc.windowClosing(row.window, year))
                    if (!row.window.isEmpty) Text(row.window.display(), style = MaterialTheme.typography.bodySmall)
                    if (w.windowSource == app.cellaroo.core.Source.AI_ESTIMATE) Text("est.", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
                    row.avgScore?.let { Text("★ ${score(it)}", color = Palette.Gold, style = MaterialTheme.typography.bodySmall) }
                }
            }
            QtyStepper(row.qty, onMinus, onPlus)
        }
    }
}

/** Logs one bottle leaving the cellar, with an optional rating and notes. */
@Composable
fun ConsumeDialog(vm: MainViewModel, row: WineRow, bottle: BottleEntity?, onDismiss: () -> Unit) {
    if (bottle == null) {
        onDismiss()
        return
    }
    var outcome by remember { mutableStateOf(BottleStatus.CONSUMED) }
    var rating by remember { mutableStateOf<Float?>(null) }
    var paired by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove one bottle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((s, label) in listOf(BottleStatus.CONSUMED to "Drunk", BottleStatus.GIFTED to "Gifted", BottleStatus.SOLD to "Sold", BottleStatus.LOST to "Lost")) {
                        FilterChip(selected = outcome == s, onClick = { outcome = s }, label = { Text(label) })
                    }
                }
                if (outcome == BottleStatus.CONSUMED) {
                    StarRating(rating) { rating = it }
                    OutlinedTextField(paired, { paired = it }, label = { Text("Had it with (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.removeBottle(bottle.id, outcome, rating.takeIf { outcome == BottleStatus.CONSUMED }, notes, paired, row.title)
                onDismiss()
            }) { Text("Log it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Button that shows the chosen shelf and opens a menu of all shelves. */
@Composable
fun LocationPicker(locations: List<LocationEntity>, selected: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_shelf), contentDescription = null)
            Text("  " + (locations.firstOrNull { it.id == selected }?.name ?: "No shelf"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (l in locations) DropdownMenuItem(text = { Text(l.name) }, onClick = { onSelect(l.id); open = false })
            DropdownMenuItem(text = { Text("No shelf") }, onClick = { onSelect(null); open = false })
        }
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
