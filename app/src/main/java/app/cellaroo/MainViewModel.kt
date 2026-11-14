package app.cellaroo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cellaroo.ai.DetectedBottle
import app.cellaroo.ai.EstimateWorker
import app.cellaroo.ai.GeminiException
import app.cellaroo.ai.currentYear
import app.cellaroo.core.BottleRef
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.Detection
import app.cellaroo.core.DrinkWindow
import app.cellaroo.core.MatchKind
import app.cellaroo.core.MatchResult
import app.cellaroo.core.Matching
import app.cellaroo.core.Pairing
import app.cellaroo.core.ParsedScore
import app.cellaroo.core.PasteParser
import app.cellaroo.core.ReconcilePlan
import app.cellaroo.core.Resolution
import app.cellaroo.core.ServingMode
import app.cellaroo.core.Source
import app.cellaroo.core.WineType
import app.cellaroo.data.AddItem
import app.cellaroo.data.BottleDraft
import app.cellaroo.data.BottleEntity
import app.cellaroo.data.CellarState
import app.cellaroo.data.ChangeSetEntity
import app.cellaroo.data.ImageStore
import app.cellaroo.data.LocationEntity
import app.cellaroo.data.MissingAction
import app.cellaroo.data.ReconcileOps
import app.cellaroo.data.SettingsState
import app.cellaroo.data.StoredPhoto
import app.cellaroo.data.WineDraft
import app.cellaroo.data.WineEntity
import app.cellaroo.data.WineRow
import app.cellaroo.data.encodePairings
import app.cellaroo.data.encodeStrings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiEvent(val message: String, val undoId: Long? = null)

enum class ScanKind { ADD, RECONCILE }

data class ScanPhoto(val name: String, val width: Int, val height: Int)

/** One editable card on the review screen. In ADD mode identical labels are merged and [qty] counts them. */
data class ScanItem(
    val id: Int,
    val photo: Int,
    val boxes: List<List<Int>>,
    val producer: String,
    val wineName: String,
    val region: String,
    val country: String,
    val vintage: String,
    val type: WineType,
    val grapes: String,
    val abv: Float?,
    val sizeMl: Int,
    val sticker: String?,
    val legible: Boolean,
    val confidence: Double,
    val qty: Int = 1,
    val price: String = "",
) {
    fun detection(): Detection = Detection(id, producer, wineName, vintage.trim().toIntOrNull(), confidence, legible)
    fun draft(): WineDraft = WineDraft(
        producer = producer.trim(), wineName = wineName.trim(), region = region.trim(), country = country.trim(),
        vintage = vintage.trim().toIntOrNull(), type = type,
        grapes = grapes.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }, abv = abv, sizeMl = sizeMl,
        stickerNote = sticker,
    )
}

enum class ExtraChoice(val label: String) { MOVE("Move here"), NEW("Add as new bottle"), IGNORE("Ignore") }

data class ScanSession(
    val kind: ScanKind,
    val locationId: Long?,
    val photos: List<ScanPhoto> = emptyList(),
    val items: List<ScanItem> = emptyList(),
    val resolutions: Map<Int, Resolution> = emptyMap(),
    val missingActions: Map<Long, MissingAction> = emptyMap(),
    val extraChoices: Map<Int, ExtraChoice> = emptyMap(),
)

/** What "Paste stats" extracted, after combining the regex pass with the AI pass. */
data class PasteParsed(
    val text: String,
    val producer: String? = null,
    val wineName: String? = null,
    val region: String? = null,
    val country: String? = null,
    val vintage: Int? = null,
    val type: WineType? = null,
    val grapes: List<String> = emptyList(),
    val window: DrinkWindow = DrinkWindow(),
    val priceUsd: Double? = null,
    val priceNote: String? = null,
    val scores: List<ParsedScore> = emptyList(),
    val notes: String? = null,
    val pairings: List<Pairing> = emptyList(),
    val servingMode: ServingMode? = null,
    val usedAi: Boolean = false,
    val aiError: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CellarooApp).container
    private val repo = c.repo

    val cellar: StateFlow<CellarState> = repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, CellarState())
    val changeSets: StateFlow<List<ChangeSetEntity>> = repo.changeSets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<SettingsState> = c.settings.state
    val images: ImageStore get() = c.images

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events
    private val _nav = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val nav: SharedFlow<String> = _nav

    /** Non-null while a long operation runs; the text is shown next to a spinner. */
    val busy = MutableStateFlow<String?>(null)
    val scan = MutableStateFlow<ScanSession?>(null)
    val scanError = MutableStateFlow<String?>(null)
    val paste = MutableStateFlow<PasteParsed?>(null)
    private var failedPhoto: StoredPhoto? = null

    init {
        viewModelScope.launch { repo.ensureDefaultLocation() }
    }

    private fun say(message: String, undoId: Long? = null) {
        _events.tryEmit(UiEvent(message, undoId))
    }

    private fun describe(e: Throwable): String = (e as? GeminiException)?.userMessage ?: (e.message ?: e.javaClass.simpleName)

    private fun launchBusy(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = label
            try {
                block()
            } catch (e: Exception) {
                say(describe(e))
            } finally {
                busy.value = null
            }
        }
    }

    // ---------------------------------------------------------------- scanning

    fun startScan(kind: ScanKind, locationId: Long?) {
        scan.value = ScanSession(kind, locationId)
        scanError.value = null
        failedPhoto = null
    }

    fun cancelScan() {
        scan.value = null
        scanError.value = null
    }

    fun onPhotoCaptured(file: File) = processPhoto { c.images.importFile(file).also { file.delete() } }

    fun onPhotoPicked(uri: Uri) = processPhoto { c.images.importUri(uri) }

    fun retryFailedPhoto() {
        val p = failedPhoto ?: return
        processPhoto { p }
    }

    val canRetryScan: Boolean get() = failedPhoto != null

    private fun processPhoto(load: () -> StoredPhoto?) {
        if (scan.value == null) startScan(ScanKind.ADD, null)
        viewModelScope.launch {
            busy.value = "Reading labels…"
            scanError.value = null
            var photo: StoredPhoto? = null
            try {
                val loaded = withContext(Dispatchers.IO) { load() } ?: throw IllegalStateException("Could not read that photo.")
                photo = loaded
                val (bottles, raw, model) = c.ai.scan(loaded.jpeg)
                val session = scan.value ?: ScanSession(ScanKind.ADD, null)
                repo.logScan(session.kind.name, session.locationId, loaded.name, model, raw)
                failedPhoto = null
                if (bottles.isEmpty()) {
                    scanError.value = "No wine labels found in that photo. Try a closer or brighter shot."
                } else {
                    scan.value = appendPhoto(session, loaded, bottles)
                    _nav.tryEmit(if (session.kind == ScanKind.ADD) "scanReview" else "reconcileReview")
                }
            } catch (e: Exception) {
                failedPhoto = photo
                scanError.value = describe(e)
            } finally {
                busy.value = null
            }
        }
    }

    private fun appendPhoto(s: ScanSession, photo: StoredPhoto, bottles: List<DetectedBottle>): ScanSession {
        val photoIndex = s.photos.size
        var nextId = (s.items.maxOfOrNull { it.id } ?: 0) + 1
        val fresh = bottles.map { b ->
            ScanItem(
                id = nextId++, photo = photoIndex, boxes = listOfNotNull(b.box), producer = b.producer, wineName = b.wineName,
                region = b.region, country = b.country, vintage = b.vintage?.toString() ?: "", type = b.type,
                grapes = b.grapes.joinToString(", "), abv = b.abv, sizeMl = b.sizeMl, sticker = b.sticker,
                legible = b.legible, confidence = b.confidence,
            )
        }
        val merged = if (s.kind == ScanKind.RECONCILE) fresh else fresh
            .groupBy { Matching.normalizedKey(it.producer, it.wineName, it.vintage.toIntOrNull(), it.sizeMl) }
            .values.map { g -> g.first().copy(boxes = g.flatMap { it.boxes }, qty = g.size, confidence = g.minOf { it.confidence }) }
        return s.copy(photos = s.photos + ScanPhoto(photo.name, photo.width, photo.height), items = s.items + merged)
    }

    fun updateScanItem(item: ScanItem) {
        val s = scan.value ?: return
        scan.value = s.copy(items = s.items.map { if (it.id == item.id) item else it })
    }

    fun removeScanItem(id: Int) {
        val s = scan.value ?: return
        scan.value = s.copy(items = s.items.filterNot { it.id == id })
    }

    /** How a scanned card relates to what is already in the cellar. */
    fun matchFor(item: ScanItem): MatchResult = Matching.match(item.detection(), cellar.value.rows.map { it.ref() })

    private suspend fun cropsFor(s: ScanSession, item: ScanItem): List<String> = withContext(Dispatchers.IO) {
        val photo = s.photos.getOrNull(item.photo) ?: return@withContext emptyList()
        item.boxes.mapIndexedNotNull { i, b -> c.images.crop(photo.name, b, "${item.id}_$i") }
    }

    private suspend fun toAddItem(s: ScanSession, item: ScanItem, existingWineId: Long?, locationId: Long?, via: String): AddItem {
        val crops = cropsFor(s, item)
        val now = System.currentTimeMillis()
        val bottles = (0 until item.qty.coerceAtLeast(1)).map { i ->
            BottleDraft(
                locationId = locationId, purchasePriceUsd = item.price.trim().toDoubleOrNull(),
                purchaseDate = if (item.price.isNotBlank()) now else null,
                cropImage = crops.getOrNull(i) ?: crops.firstOrNull(), addedVia = via,
            )
        }
        return AddItem(existingWineId, item.draft(), bottles, crops.firstOrNull())
    }

    fun saveScan(locationId: Long?) {
        val s = scan.value ?: return
        launchBusy("Saving…") {
            val usable = s.items.filter { it.producer.isNotBlank() || it.wineName.isNotBlank() }
            val adds = usable.map { item ->
                val m = matchFor(item)
                toAddItem(s, item, m.wineId.takeIf { m.kind == MatchKind.MATCH }, locationId, "SCAN")
            }
            val n = adds.sumOf { it.bottles.size }
            if (n == 0) {
                say("Nothing to save.")
                return@launchBusy
            }
            val id = repo.addBottles(adds, "SCAN", "Added $n bottle${if (n == 1) "" else "s"} from a photo")
            scan.value = null
            say("Added $n bottle${if (n == 1) "" else "s"} to the cellar", id)
            _nav.tryEmit("home")
            autoEstimate()
        }
    }

    // --------------------------------------------------------------- reconcile

    fun reconcilePlan(s: ScanSession): ReconcilePlan? {
        val loc = s.locationId ?: return null
        val state = cellar.value
        val bottles = state.rows.flatMap { r -> r.bottles.map { BottleRef(it.id, it.wineId, it.locationId) } }
        return Matching.plan(s.items.map { it.detection() }, state.rows.map { it.ref() }, bottles, loc, s.resolutions)
    }

    fun defaultMissingAction(plan: ReconcilePlan): MissingAction = if (plan.isClean) MissingAction.DRUNK else MissingAction.STILL_HERE

    fun resolve(itemId: Int, r: Resolution?) {
        val s = scan.value ?: return
        scan.value = s.copy(resolutions = if (r == null) s.resolutions - itemId else s.resolutions + (itemId to r))
    }

    fun setMissingAction(bottleId: Long, a: MissingAction) {
        val s = scan.value ?: return
        scan.value = s.copy(missingActions = s.missingActions + (bottleId to a))
    }

    fun setExtraChoice(itemId: Int, ch: ExtraChoice) {
        val s = scan.value ?: return
        scan.value = s.copy(extraChoices = s.extraChoices + (itemId to ch))
    }

    fun applyReconcile() {
        val s = scan.value ?: return
        val loc = s.locationId ?: return
        val plan = reconcilePlan(s) ?: return
        launchBusy("Updating cellar…") {
            val fallback = defaultMissingAction(plan)
            val items = s.items.associateBy { it.id }
            val missing = plan.missing.map { it.bottleId to (s.missingActions[it.bottleId] ?: fallback) }
            val moves = mutableListOf<Long>()
            val adds = mutableListOf<AddItem>()
            for (x in plan.extras) {
                val choice = s.extraChoices[x.detection.index] ?: if (x.moveFrom != null) ExtraChoice.MOVE else ExtraChoice.NEW
                val item = items[x.detection.index] ?: continue
                when (choice) {
                    ExtraChoice.MOVE -> x.moveFrom?.let { moves += it.bottleId } ?: run { adds += toAddItem(s, item, x.wineId, loc, "RECONCILE") }
                    ExtraChoice.NEW -> adds += toAddItem(s, item, x.wineId, loc, "RECONCILE")
                    ExtraChoice.IGNORE -> Unit
                }
            }
            for (d in plan.unknown) items[d.index]?.let { adds += toAddItem(s, it, null, loc, "RECONCILE") }
            val id = repo.applyReconcile(loc, ReconcileOps(missing, moves, adds))
            val removed = missing.count { it.second != MissingAction.STILL_HERE }
            val added = adds.sumOf { it.bottles.size }
            scan.value = null
            say("Shelf check applied: $removed removed, ${moves.size} moved, $added added", id)
            _nav.tryEmit("home")
            if (added > 0) autoEstimate()
        }
    }

    // ------------------------------------------------------------------- paste

    fun clearPaste() {
        paste.value = null
    }

    fun parsePaste(text: String) = launchBusy("Parsing…") {
        val year = currentYear()
        val rx = PasteParser.parse(text, year)
        var aiError: String? = null
        val ai = if (c.settings.apiKey() != null) {
            try {
                c.ai.parsePaste(text).first
            } catch (e: Exception) {
                aiError = describe(e)
                null
            }
        } else null

        var priceUsd: Double? = null
        var priceNote: String? = null
        if (ai?.priceAmount != null) {
            when {
                ai.priceCurrency != null && ai.priceCurrency != "USD" ->
                    priceNote = "Found a price in ${ai.priceCurrency} (${ai.priceAmount}). Not applied: the app tracks USD."
                ai.pricePerBottle -> priceUsd = ai.priceAmount
                else -> priceNote = "The price found is for a case, not one bottle."
            }
        }
        if (priceUsd == null) priceUsd = rx.bestUsdPrice?.perBottle
        if (priceUsd == null && priceNote == null) {
            rx.prices.firstOrNull()?.let { priceNote = "Found a price in ${it.currency} (${it.raw}). Not applied: the app tracks USD." }
        }
        paste.value = PasteParsed(
            text = text,
            producer = ai?.producer, wineName = ai?.wineName, region = ai?.region, country = ai?.country,
            vintage = ai?.vintage ?: rx.vintage, type = ai?.type, grapes = ai?.grapes.orEmpty(),
            window = ai?.window?.takeIf { !it.isEmpty } ?: rx.window,
            priceUsd = priceUsd, priceNote = priceNote,
            scores = ai?.scores?.takeIf { it.isNotEmpty() } ?: rx.scores,
            notes = ai?.referenceNotes, pairings = ai?.pairings.orEmpty(), servingMode = ai?.servingMode,
            usedAi = ai != null, aiError = aiError,
        )
    }

    /** Merges the selected groups of a parsed paste into a wine; creates the wine when [wineId] is null. */
    fun applyPaste(wineId: Long?, selected: Set<String>, locationId: Long?, onDone: (Long) -> Unit) {
        val p = paste.value ?: return
        launchBusy("Saving…") {
            val now = System.currentTimeMillis()
            val id = wineId ?: run {
                if (p.producer.isNullOrBlank() && p.wineName.isNullOrBlank()) {
                    say("The text does not name a wine. Add the wine manually, then paste its stats.")
                    return@launchBusy
                }
                val draft = WineDraft(
                    producer = p.producer.orEmpty(), wineName = p.wineName.orEmpty(), region = p.region.orEmpty(),
                    country = p.country.orEmpty(), vintage = p.vintage, type = p.type ?: WineType.RED, grapes = p.grapes,
                )
                repo.addBottles(listOf(AddItem(null, draft, listOf(BottleDraft(locationId, addedVia = "PASTE")))), "PASTE", "Added 1 bottle from pasted text")
                repo.dao.wineByKey(Matching.normalizedKey(draft.producer, draft.wineName, draft.vintage, draft.sizeMl))?.id
                    ?: return@launchBusy
            }
            var w = repo.dao.wine(id) ?: return@launchBusy
            if ("identity" in selected) {
                w = w.copy(
                    appellationRegion = w.appellationRegion.ifBlank { p.region.orEmpty() },
                    country = w.country.ifBlank { p.country.orEmpty() },
                    grapesJson = if (w.grapesJson == "[]" && p.grapes.isNotEmpty()) encodeStrings(p.grapes) else w.grapesJson,
                )
            }
            if ("window" in selected && !p.window.isEmpty) {
                w = w.copy(
                    drinkFromYear = p.window.from, peakStartYear = p.window.peakStart, peakEndYear = p.window.peakEnd,
                    drinkUntilYear = p.window.until, windowSource = Source.PASTE, windowUpdatedAt = now,
                )
            }
            if ("price" in selected && p.priceUsd != null) {
                w = w.copy(marketPriceUsd = p.priceUsd, marketPriceAsOf = now, marketPriceSource = Source.PASTE)
            }
            if ("notes" in selected && p.notes != null) w = w.copy(referenceNotes = p.notes)
            if ("pairings" in selected && p.pairings.isNotEmpty()) {
                w = w.copy(pairingsJson = encodePairings(p.pairings), pairingSource = Source.PASTE)
            }
            if ("serving" in selected && p.servingMode != null) w = w.copy(servingMode = p.servingMode, servingSource = Source.PASTE)
            repo.updateWine(w)
            if ("scores" in selected && p.scores.isNotEmpty()) repo.addScores(id, p.scores, Source.PASTE)
            repo.logPaste(id, p.text, p.copy(text = "").toString())
            paste.value = null
            say("Stats merged")
            onDone(id)
            if (wineId == null) autoEstimate()
        }
    }

    // ------------------------------------------------------------ cellar edits

    /** Removes the oldest bottle of a wine without asking for a rating; the snackbar offers undo. */
    fun quickRemove(row: WineRow, outcome: BottleStatus = BottleStatus.CONSUMED) {
        val b = row.bottles.minByOrNull { it.addedAt } ?: return
        removeBottle(b.id, outcome, null, null, null, row.title)
    }

    fun removeBottle(bottleId: Long, outcome: BottleStatus, rating: Float?, notes: String?, pairedWith: String?, title: String) {
        viewModelScope.launch {
            val id = repo.removeBottle(bottleId, outcome, rating, notes, pairedWith)
            val verb = when (outcome) {
                BottleStatus.CONSUMED -> "Logged as drunk"
                BottleStatus.GIFTED -> "Logged as gifted"
                BottleStatus.SOLD -> "Logged as sold"
                else -> "Removed"
            }
            say("$verb: $title", id)
        }
    }

    fun addBottle(wineId: Long, locationId: Long?, price: Double?) {
        viewModelScope.launch {
            val row = cellar.value.row(wineId) ?: return@launch
            val w = row.wine
            val draft = WineDraft(w.producer, w.wineName, w.appellationRegion, w.country, w.vintage, w.wineType, row.grapes, w.abvPercent, w.bottleSizeMl)
            val now = System.currentTimeMillis()
            val id = repo.addBottles(
                listOf(AddItem(wineId, draft, listOf(BottleDraft(locationId, price, if (price != null) now else null)))),
                "ADD", "Added 1 bottle",
            )
            say("Added 1 bottle: ${row.title}", id)
        }
    }

    fun updateBottle(b: BottleEntity) {
        viewModelScope.launch { repo.updateBottle(b) }
    }

    fun deleteBottle(id: Long) {
        viewModelScope.launch { repo.deleteBottle(id) }
    }

    fun saveWine(w: WineEntity) {
        viewModelScope.launch { repo.updateWine(w) }
    }

    fun deleteWine(id: Long) {
        viewModelScope.launch {
            repo.deleteWine(id)
            say("Wine deleted")
        }
    }

    fun addManualWine(draft: WineDraft, qty: Int, price: Double?, locationId: Long?, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val bottles = (0 until qty.coerceIn(1, 60)).map { BottleDraft(locationId, price, if (price != null) now else null) }
            val id = repo.addBottles(listOf(AddItem(null, draft, bottles)), "ADD", "Added ${bottles.size} bottle(s) manually")
            say("Added ${bottles.size} bottle${if (bottles.size == 1) "" else "s"}", id)
            repo.dao.wineByKey(Matching.normalizedKey(draft.producer, draft.wineName, draft.vintage, draft.sizeMl))?.let { onDone(it.id) }
            autoEstimate()
        }
    }

    fun addScore(wineId: Long, source: String, raw: String, score100: Double) {
        viewModelScope.launch { repo.addScores(wineId, listOf(ParsedScore(source, raw, score100)), Source.MANUAL) }
    }

    fun deleteScore(id: Long) {
        viewModelScope.launch { repo.deleteScore(id) }
    }

    fun undo(changeSetId: Long) {
        viewModelScope.launch { say(if (repo.undo(changeSetId)) "Undone" else "That change can no longer be undone") }
    }

    // ---------------------------------------------------------------- locations

    fun addLocation(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addLocation(name) }
    }

    fun renameLocation(l: LocationEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameLocation(l, name) }
    }

    fun deleteLocation(id: Long) {
        viewModelScope.launch { repo.deleteLocation(id) }
    }

    // ---------------------------------------------------------------- estimates

    private fun autoEstimate() {
        val s = c.settings.state.value
        if (s.hasKey && s.estimatesEnabled) EstimateWorker.enqueue(getApplication<Application>())
    }

    fun estimateAll() {
        if (!c.settings.state.value.hasKey) {
            say("Add your Gemini API key in Settings first.")
            return
        }
        EstimateWorker.enqueue(getApplication<Application>())
        say("Estimating wines without a drinking window in the background")
    }

    fun estimate(wineId: Long) = launchBusy("Estimating…") {
        // An explicit request replaces an older estimate but never pasted or hand-entered values.
        c.enricher.estimateAndApply(wineId)
        say("Estimate added. Values marked ESTIMATE come from Gemini, not from a critic.")
    }

    // ----------------------------------------------------------------- settings

    fun setApiKey(key: String) {
        try {
            c.settings.setApiKey(key)
            say(if (key.isBlank()) "API key removed" else "API key saved on this device")
        } catch (e: Exception) {
            say("Could not store the key: ${describe(e)}")
        }
    }

    fun setModel(model: String) = c.settings.setModel(model)

    fun setEstimatesEnabled(on: Boolean) = c.settings.setEstimatesEnabled(on)

    fun testKey() = launchBusy("Contacting Gemini…") {
        val models = c.ai.listModels()
        c.settings.setKnownModels(models)
        val chosen = c.settings.state.value.model
        say(
            if (chosen in models) "Key works. ${models.size} models available, including $chosen."
            else "Key works, but $chosen is not offered to this key. Pick another model below."
        )
    }

    // ------------------------------------------------------------------- backup

    fun exportZip(uri: Uri) = launchBusy("Exporting…") {
        withContext(Dispatchers.IO) { c.backup.exportZip(uri, BuildConfig.VERSION_NAME) }
        say("Backup saved")
    }

    fun exportJson(uri: Uri) = launchBusy("Exporting…") {
        withContext(Dispatchers.IO) { c.backup.exportJson(uri, BuildConfig.VERSION_NAME) }
        say("JSON saved")
    }

    fun exportCsv(uri: Uri) = launchBusy("Exporting…") {
        withContext(Dispatchers.IO) { c.backup.exportCsv(uri) }
        say("CSV saved")
    }

    fun restore(uri: Uri) = launchBusy("Restoring…") {
        val n = withContext(Dispatchers.IO) { c.backup.restore(uri) }
        repo.ensureDefaultLocation()
        say("Restored $n bottles from the backup")
    }
}
