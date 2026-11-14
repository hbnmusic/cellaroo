package app.cellaroo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.cellaroo.MainViewModel
import app.cellaroo.ai.currentYear
import app.cellaroo.core.PairingTag
import app.cellaroo.core.Readiness
import app.cellaroo.core.ReadinessCalc
import app.cellaroo.core.Source
import app.cellaroo.core.TonightInput
import app.cellaroo.core.TonightRanker
import app.cellaroo.data.WineRow

data class TonightPick(val row: WineRow, val score: Double, val fit: Double)

/** Ranked shortlist of wines that are in their window; with a chip, wines that do not suit it drop out. */
fun tonightPicks(rows: List<WineRow>, year: Int, chip: PairingTag?): List<TonightPick> = rows
    .filter { it.qty > 0 && TonightRanker.isCandidate(it.window, year) }
    .map { r ->
        val input = TonightInput(r.window, r.quality100, r.servingMode(year), r.pairings.map { it.tag }.toSet())
        TonightPick(r, TonightRanker.score(input, year, chip), TonightRanker.pairingFit(input, chip))
    }
    .filter { chip == null || it.fit > 0 }
    .sortedByDescending { it.score }

@Composable
fun TonightScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.cellar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var chip by rememberSaveable { mutableStateOf<PairingTag?>(null) }
    var opening by remember { mutableStateOf<WineRow?>(null) }
    val year = currentYear()
    val rows = state.inCellar
    val picks = remember(rows, chip) { tonightPicks(rows, year, chip) }
    val past = rows.filter { it.readiness(year) == Readiness.PAST_PEAK }
    val unknown = rows.count { it.readiness(year) == Readiness.UNKNOWN }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Open tonight")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = chip == null, onClick = { chip = null }, label = { Text("Anything") })
            for (t in listOf(PairingTag.SOLO) + PairingTag.entries.filter { it != PairingTag.SOLO }) {
                FilterChip(selected = chip == t, onClick = { chip = if (chip == t) null else t }, label = { Text(t.label) })
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (picks.isEmpty()) {
                item {
                    Text(
                        when {
                            rows.isEmpty() -> "Nothing in the cellar yet."
                            chip != null -> "No bottle in its drinking window is tagged for ${chip?.label}. Pairings come from estimates or pasted text."
                            else -> "No bottle is in its drinking window right now."
                        },
                        color = Palette.TextDim, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(picks, key = { it.row.wine.id }) { pick ->
                TonightCard(vm, pick.row, year, chip, onOpen = { opening = pick.row }) { nav.navigate("wine/${pick.row.wine.id}") }
            }
            if (past.isNotEmpty() && chip == null) {
                item { Text("Use soon: past their window", style = MaterialTheme.typography.titleMedium, color = Palette.Crimson, modifier = Modifier.padding(top = 8.dp)) }
                items(past, key = { "p${it.wine.id}" }) { r ->
                    TonightCard(vm, r, year, null, onOpen = { opening = r }) { nav.navigate("wine/${r.wine.id}") }
                }
            }
            if (unknown > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$unknown wine${if (unknown == 1) " has" else "s have"} no drinking window, so ${if (unknown == 1) "it is" else "they are"} not ranked here.",
                            color = Palette.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                        if (settings.hasKey) TextButton(onClick = { vm.estimateAll() }) { Text("Estimate") }
                    }
                }
            }
        }
    }
    opening?.let { r -> ConsumeDialog(vm, r, r.bottles.minByOrNull { it.addedAt }, onDismiss = { opening = null }) }
}

@Composable
private fun TonightCard(vm: MainViewModel, row: WineRow, year: Int, chip: PairingTag?, onOpen: () -> Unit, onClick: () -> Unit) {
    val w = row.wine
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WineThumb(vm.images.file(row.thumb), 52.dp, 76.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReadinessPill(row.readiness(year), ReadinessCalc.windowClosing(row.window, year))
                    row.quality100?.let { Text("★ ${score(it)}", color = Palette.Gold, style = MaterialTheme.typography.bodySmall) }
                    if (w.windowSource == Source.AI_ESTIMATE) Text("est.", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
                }
                Text(ReadinessCalc.verdict(row.window, year, row.qty), style = MaterialTheme.typography.bodySmall)
                val dish = chip?.let { c -> row.pairings.firstOrNull { it.tag == c }?.dish }
                Text(
                    if (dish != null) "${chip?.label}: $dish" else row.servingMode(year).label,
                    color = Palette.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onOpen) { Text("Open") }
        }
    }
}
