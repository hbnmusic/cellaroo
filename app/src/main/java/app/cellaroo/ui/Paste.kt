package app.cellaroo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.cellaroo.MainViewModel
import app.cellaroo.PasteParsed

private data class MergeRow(val key: String, val label: String, val current: String, val proposed: String)

private fun mergeRows(p: PasteParsed, current: app.cellaroo.data.WineRow?): List<MergeRow> {
    val w = current?.wine
    val out = mutableListOf<MergeRow>()
    if (!p.window.isEmpty) {
        val peak = if (p.window.peakStart != null) ", peak ${p.window.peakStart}–${p.window.peakEnd ?: "?"}" else ""
        out += MergeRow("window", "Drinking window", current?.window?.display() ?: "—", p.window.display() + peak)
    }
    if (p.priceUsd != null) out += MergeRow("price", "Market price", money(w?.marketPriceUsd), money(p.priceUsd) + " per bottle")
    if (p.scores.isNotEmpty()) {
        out += MergeRow(
            "scores", "Scores",
            current?.scores?.joinToString(", ") { "${it.sourceLabel} ${score(it.score100.toDouble())}" }.orEmpty().ifBlank { "—" },
            p.scores.joinToString(", ") { "${it.sourceLabel} ${it.raw}" },
        )
    }
    if (p.notes != null) out += MergeRow("notes", "Tasting notes", w?.referenceNotes ?: "—", p.notes)
    if (p.pairings.isNotEmpty()) {
        out += MergeRow(
            "pairings", "Pairings",
            current?.pairings?.joinToString(", ") { it.tag.label }.orEmpty().ifBlank { "—" },
            p.pairings.joinToString(", ") { it.tag.label + (it.dish?.let { d -> " ($d)" } ?: "") },
        )
    }
    if (p.servingMode != null) out += MergeRow("serving", "Serving", w?.servingMode?.label ?: "—", p.servingMode.label)
    if (w != null && (p.region != null || p.country != null || p.grapes.isNotEmpty())) {
        val fills = listOfNotNull(
            p.region?.takeIf { w.appellationRegion.isBlank() }, p.country?.takeIf { w.country.isBlank() },
            p.grapes.joinToString("/").takeIf { it.isNotBlank() && w.grapesJson == "[]" },
        )
        if (fills.isNotEmpty()) out += MergeRow("identity", "Missing details", "blank", fills.joinToString(", "))
    }
    return out
}

/** Paste copied web text, preview what was extracted, and merge the chosen parts. [wineId] 0 creates a wine. */
@Composable
fun PasteScreen(vm: MainViewModel, nav: NavHostController, wineId: Long) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val parsed by vm.paste.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var unchecked by remember { mutableStateOf(setOf<String>()) }
    var location by remember(state.locations) { mutableStateOf(state.locations.firstOrNull()?.id) }
    val row = if (wineId != 0L) state.row(wineId) else null
    LaunchedEffect(wineId) { vm.clearPaste() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (wineId == 0L) "Quick paste" else "Paste stats", onBack = { vm.clearPaste(); nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row?.let { Text(it.title, style = MaterialTheme.typography.titleMedium) }
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Paste copied text") },
                placeholder = { Text("From CellarTracker, Wine-Searcher, Vivino, critic reviews or retailer emails") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste clipboard") }
                Button(onClick = { unchecked = emptySet(); vm.parsePaste(text) }, enabled = text.isNotBlank()) { Text("Parse") }
            }
            if (!settings.hasKey) {
                Text(
                    "No Gemini key set: only the built-in pattern matcher runs. It finds scores, windows and prices, but not names, notes or pairings.",
                    color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                )
            }
            val p = parsed
            if (p != null) {
                val rows = mergeRows(p, row)
                SectionCard("Found in the text") {
                    if (wineId == 0L) {
                        val name = listOf(p.vintage?.toString() ?: "NV", p.producer.orEmpty(), p.wineName.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
                        if (p.producer.isNullOrBlank() && p.wineName.isNullOrBlank()) {
                            Text("Could not tell which wine this is. Add the wine by hand first, then paste its stats from its page.", color = Palette.Amber)
                        } else {
                            LabelValue("New wine", name)
                            LabelValue("Details", listOfNotNull(p.region, p.country, p.type?.label).joinToString(" · ").ifBlank { "—" })
                        }
                    }
                    if (rows.isEmpty()) Text("Nothing usable found: no score, drinking window, price, notes or pairing.", color = Palette.TextDim)
                    for (r in rows) {
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(checked = r.key !in unchecked, onCheckedChange = { on -> unchecked = if (on) unchecked - r.key else unchecked + r.key })
                            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                                Text(r.label, style = MaterialTheme.typography.labelLarge, color = Palette.Gold)
                                if (wineId != 0L) Text("Now: ${r.current}", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                                Text(r.proposed, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    p.priceNote?.let { Text(it, color = Palette.Amber, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        when {
                            p.usedAi -> "Read by Gemini from your text, with the pattern matcher as backup. Check before merging."
                            p.aiError != null -> "Gemini was not available (${p.aiError}). Pattern matcher only."
                            else -> "Pattern matcher only."
                        },
                        color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (wineId == 0L) LocationPicker(state.locations, location, { location = it })
                val canApply = if (wineId == 0L) !(p.producer.isNullOrBlank() && p.wineName.isNullOrBlank()) else rows.any { it.key !in unchecked }
                Button(
                    enabled = canApply, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val selected = rows.map { it.key }.filter { it !in unchecked }.toSet() + "identity"
                        vm.applyPaste(wineId.takeIf { it != 0L }, selected, location) { id ->
                            nav.popBackStack()
                            if (wineId == 0L) nav.navigate("wine/$id")
                        }
                    },
                ) { Text(if (wineId == 0L) "Add wine and merge stats" else "Confirm and merge into bottle") }
            }
        }
    }
}
