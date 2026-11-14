package app.cellaroo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.cellaroo.MainViewModel
import app.cellaroo.R
import app.cellaroo.ai.currentYear
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.ReadinessCalc
import app.cellaroo.core.ScoreSources
import app.cellaroo.core.ServingMode
import app.cellaroo.core.Source
import app.cellaroo.core.WineType
import app.cellaroo.data.BottleEntity
import app.cellaroo.data.CellarState
import app.cellaroo.data.WineDraft
import app.cellaroo.data.WineRow
import app.cellaroo.data.encodeStrings

private const val STALE_MS = 365L * 24 * 3600 * 1000

@Composable
fun WineDetailScreen(vm: MainViewModel, nav: NavHostController, id: Long) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val row = state.row(id)
    var consuming by remember { mutableStateOf<BottleEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addScore by remember { mutableStateOf(false) }
    var priceFor by remember { mutableStateOf<BottleEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(row?.wine?.producer?.ifBlank { null } ?: "Wine", onBack = { nav.popBackStack() }) {
            if (row != null) {
                IconButton(onClick = { nav.navigate("edit/$id") }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
        if (row == null) {
            if (state.loaded) Text("This wine is no longer in the cellar.", Modifier.padding(16.dp), color = Palette.TextDim)
            return@Column
        }
        val w = row.wine
        val year = currentYear()
        val readiness = row.readiness(year)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WineThumb(vm.images.file(row.thumb), 96.dp, 140.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf(w.appellationRegion, w.country, w.wineType.label).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (row.grapes.isNotEmpty()) Text(row.grapes.joinToString(", "), color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReadinessPill(readiness, ReadinessCalc.windowClosing(row.window, year))
                        Text("${row.qty} in cellar", style = MaterialTheme.typography.bodyMedium)
                    }
                    w.stickerNote?.let { Text("Sticker on bottle: $it (source unknown)", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall) }
                }
            }

            SectionCard("Drink or keep") {
                Text(ReadinessCalc.verdict(row.window, year, row.qty), style = MaterialTheme.typography.bodyLarge)
                if (!row.window.isEmpty) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Window ${row.window.display()}", style = MaterialTheme.typography.bodyMedium)
                        if (w.peakStartYear != null) Text("peak ${w.peakStartYear}–${w.peakEndYear ?: w.drinkUntilYear ?: "?"}", color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
                        SourceTag(w.windowSource)
                    }
                }
                HorizontalDivider(color = Palette.Outline.copy(alpha = 0.4f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.servingMode(year).label, style = MaterialTheme.typography.bodyLarge)
                    SourceTag(w.servingSource ?: Source.HEURISTIC)
                }
                w.servingReason?.let { Text(it, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium) }
                val serve = listOfNotNull(
                    w.servingTempC?.let { "Serve at $it°C (${(it * 9 / 5) + 32}°F)" },
                    w.decantMinutes?.let { if (it > 0) "decant $it min" else "no decanting" },
                ).joinToString(", ")
                if (serve.isNotEmpty()) Text(serve, style = MaterialTheme.typography.bodyMedium)
                if (row.pairings.isNotEmpty()) {
                    HorizontalDivider(color = Palette.Outline.copy(alpha = 0.4f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Pairings", style = MaterialTheme.typography.bodyLarge)
                        SourceTag(w.pairingSource)
                    }
                    for (p in row.pairings) LabelValue(p.tag.label, p.dish ?: "works well")
                    w.pairingNotes?.let { Text(it, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall) }
                }
                w.styleSummary?.let { Text("Style: $it", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate("paste/$id") }) {
                        Icon(painterResource(R.drawable.ic_paste), contentDescription = null)
                        Text("  Paste stats")
                    }
                    if (settings.hasKey) {
                        OutlinedButton(onClick = { vm.estimate(id) }) {
                            Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null)
                            Text(if (w.windowSource == Source.AI_ESTIMATE) "  Re-estimate" else "  Estimate")
                        }
                    }
                }
                if (!settings.hasKey && row.window.isEmpty) {
                    Text("Add a Gemini key in Settings to get estimates, or paste a drinking window.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionCard("Prices") {
                val stale = w.marketPriceAsOf != null && System.currentTimeMillis() - w.marketPriceAsOf > STALE_MS
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Market ${money(w.marketPriceUsd)}", style = MaterialTheme.typography.bodyLarge)
                    if (w.marketPriceUsd != null) SourceTag(w.marketPriceSource)
                    if (stale) Tag("STALE", Palette.Crimson)
                }
                if (w.marketPriceUsd != null) Text("as of ${day(w.marketPriceAsOf)}", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                else Text("No market price yet. Paste one from Wine-Searcher or a shop, or enter it under Edit.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                if (row.pricedBottles > 0) {
                    val avg = row.costBasis / row.pricedBottles
                    LabelValue("You paid", "${money(avg)} avg (${row.pricedBottles} of ${row.qty} bottles priced)")
                    w.marketPriceUsd?.let { m ->
                        val diff = m - avg
                        LabelValue("Per bottle", (if (diff >= 0) "+" else "−") + money(kotlin.math.abs(diff)) + " vs. paid")
                    }
                }
            }

            SectionCard("Critic scores", trailing = { TextButton(onClick = { addScore = true }) { Text("Add") } }) {
                if (row.scores.isEmpty()) Text("None yet. Scores are only ever pasted or typed in, never guessed.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                for (s in row.scores) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.sourceLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(s.rawText, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                        Text("  ${score(s.score100.toDouble())}", color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.deleteScore(s.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete score", tint = Palette.Outline) }
                    }
                }
                row.avgScore?.takeIf { row.scores.size > 1 }?.let { LabelValue("Average", score(it)) }
            }

            SectionCard("Bottles (${row.qty})", trailing = {
                TextButton(onClick = { vm.addBottle(id, row.bottles.lastOrNull()?.locationId ?: state.locations.firstOrNull()?.id, null) }) { Text("Add one") }
            }) {
                if (row.bottles.isEmpty()) Text("None left.", color = Palette.TextDim)
                for (b in row.bottles) BottleLine(vm, state, b, onDrink = { consuming = b }, onPrice = { priceFor = b })
            }

            if (!w.myNotes.isNullOrBlank() || !w.referenceNotes.isNullOrBlank()) {
                SectionCard("Notes") {
                    w.myNotes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    w.referenceNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Pasted reference", color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (row.logs.isNotEmpty()) {
                SectionCard("History") {
                    for (l in row.logs) {
                        val what = when (l.outcome) {
                            BottleStatus.CONSUMED -> "Drunk"
                            BottleStatus.GIFTED -> "Gifted"
                            BottleStatus.SOLD -> "Sold"
                            else -> "Removed"
                        }
                        Text(
                            listOfNotNull(
                                "${day(l.consumedDate)}: $what", l.userRating?.let { "${it.toInt()}★" },
                                l.pairedWith?.let { "with $it" }, l.personalNotes,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    consuming?.let { b -> row?.let { ConsumeDialog(vm, it, b, onDismiss = { consuming = null }) } }
    priceFor?.let { b -> PriceDialog(b, onDismiss = { priceFor = null }, onSave = { vm.updateBottle(it); priceFor = null }) }
    if (addScore) AddScoreDialog(onDismiss = { addScore = false }) { src, raw, s100 -> vm.addScore(id, src, raw, s100); addScore = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this wine?") },
            text = { Text("This removes the wine, its bottles, scores and history. It cannot be undone.") },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.deleteWine(id); nav.popBackStack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BottleLine(vm: MainViewModel, state: CellarState, b: BottleEntity, onDrink: () -> Unit, onPrice: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.locationName(b.locationId), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    b.purchasePriceUsd?.let { "paid ${money(it)}" } ?: "no purchase price",
                    b.purchaseDate?.let { day(it) }, b.purchaseSource,
                ).joinToString(" · "),
                color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDrink) { Text("Open") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false; moving = false }) {
                if (!moving) {
                    DropdownMenuItem(text = { Text("Set purchase price") }, onClick = { menu = false; onPrice() })
                    DropdownMenuItem(text = { Text("Move to another shelf") }, onClick = { moving = true })
                    DropdownMenuItem(text = { Text("Delete (added by mistake)") }, onClick = { menu = false; vm.deleteBottle(b.id) })
                } else {
                    for (l in state.locations) {
                        DropdownMenuItem(text = { Text(l.name) }, onClick = { vm.updateBottle(b.copy(locationId = l.id)); menu = false; moving = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceDialog(b: BottleEntity, onDismiss: () -> Unit, onSave: (BottleEntity) -> Unit) {
    var price by remember { mutableStateOf(b.purchasePriceUsd?.toString() ?: "") }
    var where by remember { mutableStateOf(b.purchaseSource ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Purchase price") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(price, { price = it }, "Paid per bottle (USD)", Modifier.fillMaxWidth(), decimal = true)
                OutlinedTextField(where, { where = it }, label = { Text("Bought at (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = price.toDoubleOrNull()
                onSave(b.copy(purchasePriceUsd = p, purchaseSource = where.trim().ifBlank { null }, purchaseDate = b.purchaseDate ?: p?.let { System.currentTimeMillis() }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddScoreDialog(onDismiss: () -> Unit, onAdd: (String, String, Double) -> Unit) {
    var source by remember { mutableStateOf("WA") }
    var value by remember { mutableStateOf("") }
    val scale = when (source) { "JR" -> 20; "VIVINO" -> 5; else -> 100 }
    val parsed = value.toDoubleOrNull()
    val s100 = parsed?.let { ScoreSources.to100(it, scale) }?.takeIf { it in 50.0..100.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((code, label) in ScoreSources.labels) {
                        FilterChip(selected = source == code, onClick = { source = code }, label = { Text(label) })
                    }
                }
                NumberField(value, { value = it }, "Score out of $scale", Modifier.fillMaxWidth(), decimal = true)
            }
        },
        confirmButton = {
            Button(enabled = s100 != null, onClick = { if (s100 != null) onAdd(source, if (scale == 100) value else "$value/$scale", s100) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edits a wine, or creates one by hand when [id] is 0. */
@Composable
fun WineEditScreen(vm: MainViewModel, nav: NavHostController, id: Long) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    val existing: WineRow? = state.row(id)
    if (id != 0L && existing == null) {
        Column { ScreenHeader("Edit wine", onBack = { nav.popBackStack() }) }
        return
    }
    val w = existing?.wine
    var producer by remember(w?.id) { mutableStateOf(w?.producer ?: "") }
    var name by remember(w?.id) { mutableStateOf(w?.wineName ?: "") }
    var vintage by remember(w?.id) { mutableStateOf(w?.vintage?.toString() ?: "") }
    var region by remember(w?.id) { mutableStateOf(w?.appellationRegion ?: "") }
    var country by remember(w?.id) { mutableStateOf(w?.country ?: "") }
    var type by remember(w?.id) { mutableStateOf(w?.wineType ?: WineType.RED) }
    var grapes by remember(w?.id) { mutableStateOf(existing?.grapes?.joinToString(", ") ?: "") }
    var from by remember(w?.id) { mutableStateOf(w?.drinkFromYear?.toString() ?: "") }
    var peakStart by remember(w?.id) { mutableStateOf(w?.peakStartYear?.toString() ?: "") }
    var peakEnd by remember(w?.id) { mutableStateOf(w?.peakEndYear?.toString() ?: "") }
    var until by remember(w?.id) { mutableStateOf(w?.drinkUntilYear?.toString() ?: "") }
    var serving by remember(w?.id) { mutableStateOf(w?.servingMode) }
    var market by remember(w?.id) { mutableStateOf(w?.marketPriceUsd?.toString() ?: "") }
    var notes by remember(w?.id) { mutableStateOf(w?.myNotes ?: "") }
    var qty by remember { mutableStateOf("1") }
    var paid by remember { mutableStateOf("") }
    var location by remember(state.locations) { mutableStateOf(state.locations.firstOrNull()?.id) }
    val valid = producer.isNotBlank() || name.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (w == null) "Add a wine" else "Edit wine", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(producer, { producer = it }, label = { Text("Producer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Wine name / cuvée") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vintage, { vintage = it.take(4) }, "Vintage (blank = NV)", Modifier.weight(1f))
                OutlinedTextField(country, { country = it }, label = { Text("Country") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(region, { region = it }, label = { Text("Appellation / region") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(grapes, { grapes = it }, label = { Text("Grapes (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in WineType.entries) FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
            }
            if (w == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(qty, { qty = it.take(2) }, "Bottles", Modifier.weight(1f))
                    NumberField(paid, { paid = it }, "Paid each (USD)", Modifier.weight(1f), decimal = true)
                }
                LocationPicker(state.locations, location, { location = it })
            } else {
                Text("Drinking window (years)", color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(from, { from = it.take(4) }, "From", Modifier.weight(1f))
                    NumberField(peakStart, { peakStart = it.take(4) }, "Peak from", Modifier.weight(1f))
                    NumberField(peakEnd, { peakEnd = it.take(4) }, "Peak to", Modifier.weight(1f))
                    NumberField(until, { until = it.take(4) }, "Until", Modifier.weight(1f))
                }
                Text("Serving", color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = serving == null, onClick = { serving = null }, label = { Text("Not set") })
                    for (m in ServingMode.entries) FilterChip(selected = serving == m, onClick = { serving = m }, label = { Text(m.label) })
                }
                NumberField(market, { market = it }, "Market price per bottle (USD)", Modifier.fillMaxWidth(), decimal = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("My notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val grapeList = grapes.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    if (w == null) {
                        val draft = WineDraft(producer.trim(), name.trim(), region.trim(), country.trim(), vintage.toIntOrNull(), type, grapeList)
                        vm.addManualWine(draft, qty.toIntOrNull() ?: 1, paid.toDoubleOrNull(), location) { newId ->
                            nav.popBackStack()
                            nav.navigate("wine/$newId")
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        val window = listOf(from.toIntOrNull(), peakStart.toIntOrNull(), peakEnd.toIntOrNull(), until.toIntOrNull())
                        val windowChanged = window != listOf(w.drinkFromYear, w.peakStartYear, w.peakEndYear, w.drinkUntilYear)
                        val price = market.toDoubleOrNull()
                        val priceChanged = price != w.marketPriceUsd
                        vm.saveWine(
                            w.copy(
                                producer = producer.trim(), wineName = name.trim(), vintage = vintage.toIntOrNull(),
                                appellationRegion = region.trim(), country = country.trim(), wineType = type,
                                grapesJson = encodeStrings(grapeList),
                                drinkFromYear = window[0], peakStartYear = window[1], peakEndYear = window[2], drinkUntilYear = window[3],
                                windowSource = if (windowChanged) (if (window.all { it == null }) null else Source.MANUAL) else w.windowSource,
                                windowUpdatedAt = if (windowChanged) now else w.windowUpdatedAt,
                                servingMode = serving,
                                servingSource = if (serving != w.servingMode) (if (serving == null) null else Source.MANUAL) else w.servingSource,
                                servingReason = if (serving != w.servingMode) null else w.servingReason,
                                marketPriceUsd = price,
                                marketPriceAsOf = if (priceChanged) (if (price == null) null else now) else w.marketPriceAsOf,
                                marketPriceSource = if (priceChanged) (if (price == null) null else Source.MANUAL) else w.marketPriceSource,
                                myNotes = notes.trim().ifBlank { null },
                            )
                        )
                        nav.popBackStack()
                    }
                },
            ) { Text(if (w == null) "Add to cellar" else "Save") }
        }
    }
}
