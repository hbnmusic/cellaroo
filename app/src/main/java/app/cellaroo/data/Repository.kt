package app.cellaroo.data

import androidx.room.withTransaction
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.DrinkWindow
import app.cellaroo.core.Matching
import app.cellaroo.core.Pairing
import app.cellaroo.core.PairingTag
import app.cellaroo.core.ParsedScore
import app.cellaroo.core.Readiness
import app.cellaroo.core.ReadinessCalc
import app.cellaroo.core.ScoreSources
import app.cellaroo.core.ServingMode
import app.cellaroo.core.WineRef
import app.cellaroo.core.WineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class PairingDto(val tag: String = "", val dish: String? = null)

fun encodePairings(list: List<Pairing>): String =
    AppJson.encodeToString(ListSerializer(PairingDto.serializer()), list.map { PairingDto(it.tag.name, it.dish) })

fun decodePairings(json: String): List<Pairing> = runCatching {
    AppJson.decodeFromString(ListSerializer(PairingDto.serializer()), json)
        .mapNotNull { d -> PairingTag.parse(d.tag)?.let { Pairing(it, d.dish?.takeIf { s -> s.isNotBlank() }) } }
}.getOrDefault(emptyList())

fun encodeStrings(list: List<String>): String = AppJson.encodeToString(ListSerializer(String.serializer()), list)

fun decodeStrings(json: String): List<String> =
    runCatching { AppJson.decodeFromString(ListSerializer(String.serializer()), json) }.getOrDefault(emptyList())

/** A wine with everything the UI needs, assembled in memory. */
data class WineRow(
    val wine: WineEntity,
    val allBottles: List<BottleEntity>,
    val scores: List<CriticScoreEntity>,
    val logs: List<ConsumptionLogEntity>,
) {
    val bottles: List<BottleEntity> = allBottles.filter { it.status == BottleStatus.IN_CELLAR }
    val qty: Int get() = bottles.size
    val window: DrinkWindow = DrinkWindow(wine.drinkFromYear, wine.peakStartYear, wine.peakEndYear, wine.drinkUntilYear)
    val title: String =
        listOf(wine.vintage?.toString() ?: "NV", wine.producer, wine.wineName).filter { it.isNotBlank() }.joinToString(" ")
    val avgScore: Double? = scores.map { it.score100.toDouble() }.takeIf { it.isNotEmpty() }?.average()
    val myRating100: Double? = logs.mapNotNull { it.userRating?.toDouble() }.takeIf { it.isNotEmpty() }?.average()?.times(20)
    val quality100: Double? = myRating100 ?: avgScore
    val pairings: List<Pairing> = decodePairings(wine.pairingsJson)
    val grapes: List<String> = decodeStrings(wine.grapesJson)
    val thumb: String? = wine.labelImage ?: allBottles.firstNotNullOfOrNull { it.cropImage }
    val costBasis: Double = bottles.sumOf { it.purchasePriceUsd ?: 0.0 }
    val pricedBottles: Int = bottles.count { it.purchasePriceUsd != null }
    val marketValue: Double? = wine.marketPriceUsd?.let { it * qty }

    fun readiness(year: Int): Readiness = ReadinessCalc.readiness(window, year)
    fun servingMode(year: Int): ServingMode =
        wine.servingMode ?: ReadinessCalc.fallbackServingMode(wine.wineType, wine.vintage, year)
    fun ref(): WineRef = WineRef(wine.id, wine.producer, wine.wineName, wine.vintage, wine.bottleSizeMl)
}

data class CellarState(
    val rows: List<WineRow> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    /** Wines that still have at least one bottle. */
    val inCellar: List<WineRow> get() = rows.filter { it.qty > 0 }
    fun row(id: Long): WineRow? = rows.firstOrNull { it.wine.id == id }
    fun locationName(id: Long?): String = locations.firstOrNull { it.id == id }?.name ?: "No location"
}

data class WineDraft(
    val producer: String,
    val wineName: String,
    val region: String = "",
    val country: String = "",
    val vintage: Int? = null,
    val type: WineType = WineType.RED,
    val grapes: List<String> = emptyList(),
    val abv: Float? = null,
    val sizeMl: Int = 750,
    val stickerNote: String? = null,
)

data class BottleDraft(
    val locationId: Long?,
    val purchasePriceUsd: Double? = null,
    val purchaseDate: Long? = null,
    val purchaseSource: String? = null,
    val cropImage: String? = null,
    val addedVia: String = "MANUAL",
)

data class AddItem(val existingWineId: Long?, val draft: WineDraft, val bottles: List<BottleDraft>, val labelImage: String? = null)

enum class MissingAction(val label: String) {
    DRUNK("Drunk"), STILL_HERE("Still here"), GIFTED("Gifted"), SOLD("Sold"), LOST("Lost")
}

data class ReconcileOps(
    val missing: List<Pair<Long, MissingAction>>,
    /** Bottles to move into the reconciled location. */
    val moveHere: List<Long>,
    val additions: List<AddItem>,
)

@Serializable
data class BottleSnap(val id: Long, val status: BottleStatus, val locationId: Long?, val removedAt: Long?)

@Serializable
data class ChangePayload(
    val bottlesBefore: List<BottleSnap> = emptyList(),
    val createdBottleIds: List<Long> = emptyList(),
    val createdWineIds: List<Long> = emptyList(),
    val createdLogIds: List<Long> = emptyList(),
)

@Serializable
data class BackupData(
    val format: Int = 1,
    val exportedAt: Long = 0,
    val appVersion: String = "",
    val wines: List<WineEntity> = emptyList(),
    val bottles: List<BottleEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val scores: List<CriticScoreEntity> = emptyList(),
    val logs: List<ConsumptionLogEntity> = emptyList(),
    val scans: List<ScanEntity> = emptyList(),
    val pastes: List<PasteLogEntity> = emptyList(),
)

class CellarRepository(private val db: CellarDb, private val now: () -> Long = { System.currentTimeMillis() }) {
    val dao: CellarDao = db.dao()

    val state: Flow<CellarState> = combine(
        dao.observeWines(), dao.observeBottles(), dao.observeScores(), dao.observeLogs(), dao.observeLocations(),
    ) { wines, bottles, scores, logs, locations ->
        val b = bottles.groupBy { it.wineId }
        val s = scores.groupBy { it.wineId }
        val l = logs.groupBy { it.wineId }
        CellarState(
            rows = wines.map { WineRow(it, b[it.id].orEmpty(), s[it.id].orEmpty(), l[it.id].orEmpty()) },
            locations = locations,
            loaded = true,
        )
    }

    val changeSets: Flow<List<ChangeSetEntity>> = dao.observeChangeSets()

    private fun WineDraft.toEntity(labelImage: String?): WineEntity = WineEntity(
        producer = producer.trim(), wineName = wineName.trim(), appellationRegion = region.trim(), country = country.trim(),
        vintage = vintage, wineType = type, grapesJson = encodeStrings(grapes), abvPercent = abv, bottleSizeMl = sizeMl,
        normalizedKey = Matching.normalizedKey(producer, wineName, vintage, sizeMl), stickerNote = stickerNote,
        labelImage = labelImage, createdAt = now(), updatedAt = now(),
    )

    private suspend fun record(kind: String, label: String, payload: ChangePayload): Long = dao.insertChangeSet(
        ChangeSetEntity(kind = kind, label = label, payloadJson = AppJson.encodeToString(ChangePayload.serializer(), payload), createdAt = now())
    )

    suspend fun ensureDefaultLocation(): Long = db.withTransaction {
        dao.locations().firstOrNull()?.id ?: dao.insertLocation(LocationEntity(name = "Default"))
    }

    /** Adds bottles, creating wines that are not in the cellar yet. Returns the change-set id for undo. */
    suspend fun addBottles(items: List<AddItem>, kind: String, label: String): Long = db.withTransaction {
        val created = addInternal(items)
        record(kind, label, ChangePayload(createdBottleIds = created.second, createdWineIds = created.first))
    }

    private suspend fun addInternal(items: List<AddItem>): Pair<List<Long>, List<Long>> {
        val wineIds = mutableListOf<Long>()
        val bottleIds = mutableListOf<Long>()
        for (item in items) {
            val d = item.draft
            val existing = item.existingWineId?.let { dao.wine(it) }
                ?: dao.wineByKey(Matching.normalizedKey(d.producer, d.wineName, d.vintage, d.sizeMl))
            val wineId = if (existing != null) {
                if (existing.labelImage == null && item.labelImage != null) dao.updateWine(existing.copy(labelImage = item.labelImage))
                existing.id
            } else {
                dao.insertWine(d.toEntity(item.labelImage)).also { wineIds += it }
            }
            for (b in item.bottles) {
                bottleIds += dao.insertBottle(
                    BottleEntity(
                        wineId = wineId, locationId = b.locationId, purchasePriceUsd = b.purchasePriceUsd,
                        purchaseDate = b.purchaseDate, purchaseSource = b.purchaseSource?.takeIf { it.isNotBlank() },
                        cropImage = b.cropImage, addedVia = b.addedVia, addedAt = now(),
                    )
                )
            }
        }
        return wineIds to bottleIds
    }

    private fun snap(b: BottleEntity) = BottleSnap(b.id, b.status, b.locationId, b.removedAt)

    /** Takes one bottle out of the cellar and logs it. Returns the change-set id for undo. */
    suspend fun removeBottle(
        bottleId: Long,
        outcome: BottleStatus = BottleStatus.CONSUMED,
        rating: Float? = null,
        notes: String? = null,
        pairedWith: String? = null,
    ): Long? = db.withTransaction {
        val b = dao.bottle(bottleId) ?: return@withTransaction null
        val logId = removeInternal(b, outcome, rating, notes, pairedWith)
        record("REMOVE", "Removed 1 bottle", ChangePayload(bottlesBefore = listOf(snap(b)), createdLogIds = listOf(logId)))
    }

    private suspend fun removeInternal(b: BottleEntity, outcome: BottleStatus, rating: Float?, notes: String?, pairedWith: String?): Long {
        val wine = dao.wine(b.wineId)
        dao.updateBottle(b.copy(status = outcome, removedAt = now()))
        val title = wine?.let { listOf(it.vintage?.toString() ?: "NV", it.producer, it.wineName).joinToString(" ").trim() } ?: "Wine"
        return dao.insertLog(
            ConsumptionLogEntity(
                bottleId = b.id, wineId = b.wineId, wineSnapshotTitle = title, consumedDate = now(), outcome = outcome,
                pairedWith = pairedWith?.takeIf { it.isNotBlank() }, userRating = rating, personalNotes = notes?.takeIf { it.isNotBlank() },
            )
        )
    }

    suspend fun updateBottle(b: BottleEntity) = dao.updateBottle(b)

    suspend fun deleteBottle(id: Long) = dao.deleteBottles(listOf(id))

    suspend fun updateWine(w: WineEntity) = dao.updateWine(
        w.copy(normalizedKey = Matching.normalizedKey(w.producer, w.wineName, w.vintage, w.bottleSizeMl), updatedAt = now())
    )

    suspend fun deleteWine(id: Long) = dao.deleteWines(listOf(id))

    /** Stores scores; a new score from a named source replaces the older one from that source. */
    suspend fun addScores(wineId: Long, scores: List<ParsedScore>, origin: String) = db.withTransaction {
        for (s in scores) {
            if (s.source != "OTHER") dao.deleteScoresOf(wineId, s.source)
            dao.insertScore(
                CriticScoreEntity(
                    wineId = wineId, source = s.source, sourceLabel = ScoreSources.label(s.source), rawText = s.raw,
                    score100 = s.score100.toFloat(), rangeLow100 = s.low100?.toFloat(), rangeHigh100 = s.high100?.toFloat(),
                    origin = origin, addedAt = now(),
                )
            )
        }
    }

    suspend fun deleteScore(id: Long) = dao.deleteScore(id)

    suspend fun logScan(kind: String, locationId: Long?, image: String, model: String, raw: String) {
        dao.insertScan(ScanEntity(kind = kind, locationId = locationId, image = image, modelId = model, rawResponseJson = raw, createdAt = now()))
    }

    suspend fun logPaste(wineId: Long?, text: String, parsed: String) {
        dao.insertPaste(PasteLogEntity(wineId = wineId, rawText = text, parsedJson = parsed, createdAt = now()))
    }

    suspend fun addLocation(name: String): Long =
        dao.insertLocation(LocationEntity(name = name.trim(), sortOrder = dao.locations().size))

    suspend fun renameLocation(l: LocationEntity, name: String) = dao.updateLocation(l.copy(name = name.trim()))

    suspend fun deleteLocation(id: Long) = dao.deleteLocation(id)

    /** Applies the outcome of a shelf check in one transaction. Returns the change-set id for undo. */
    suspend fun applyReconcile(locationId: Long, ops: ReconcileOps): Long = db.withTransaction {
        val touched = dao.bottlesByIds(ops.missing.map { it.first } + ops.moveHere)
        val byId = touched.associateBy { it.id }
        val logIds = mutableListOf<Long>()
        for ((id, action) in ops.missing) {
            val b = byId[id] ?: continue
            val outcome = when (action) {
                MissingAction.STILL_HERE -> null
                MissingAction.DRUNK -> BottleStatus.CONSUMED
                MissingAction.GIFTED -> BottleStatus.GIFTED
                MissingAction.SOLD -> BottleStatus.SOLD
                MissingAction.LOST -> BottleStatus.LOST
            }
            if (outcome != null) logIds += removeInternal(b, outcome, null, "Marked during shelf check", null)
        }
        for (id in ops.moveHere) byId[id]?.let { dao.updateBottle(it.copy(locationId = locationId)) }
        val created = addInternal(ops.additions)
        dao.location(locationId)?.let { dao.updateLocation(it.copy(lastReconciledAt = now())) }
        record(
            "RECONCILE", "Shelf check",
            ChangePayload(touched.map { snap(it) }, created.second, created.first, logIds),
        )
    }

    suspend fun undo(changeSetId: Long): Boolean = db.withTransaction {
        val cs = dao.changeSet(changeSetId) ?: return@withTransaction false
        if (cs.undone) return@withTransaction false
        val p = runCatching { AppJson.decodeFromString(ChangePayload.serializer(), cs.payloadJson) }.getOrNull()
            ?: return@withTransaction false
        dao.deleteLogs(p.createdLogIds)
        dao.deleteBottles(p.createdBottleIds)
        dao.deleteWines(p.createdWineIds)
        val current = dao.bottlesByIds(p.bottlesBefore.map { it.id }).associateBy { it.id }
        for (s in p.bottlesBefore) {
            current[s.id]?.let { dao.updateBottle(it.copy(status = s.status, locationId = s.locationId, removedAt = s.removedAt)) }
        }
        dao.markUndone(changeSetId)
        true
    }

    suspend fun snapshot(appVersion: String): BackupData = db.withTransaction {
        BackupData(
            exportedAt = now(), appVersion = appVersion, wines = dao.wines(), bottles = dao.bottles(), locations = dao.locations(),
            scores = dao.scores(), logs = dao.logs(), scans = dao.scans(), pastes = dao.pastes(),
        )
    }

    /** Replaces the whole cellar with the backup's contents. */
    suspend fun restore(data: BackupData) = db.withTransaction {
        dao.clearChangeSets()
        dao.clearScans()
        dao.clearPastes()
        dao.clearWines() // cascades to bottles, scores, logs
        dao.clearLocations()
        dao.insertLocations(data.locations)
        dao.insertWines(data.wines)
        dao.insertBottles(data.bottles)
        dao.insertScores(data.scores)
        dao.insertLogs(data.logs)
        dao.insertScans(data.scans)
        dao.insertPastes(data.pastes)
    }
}
