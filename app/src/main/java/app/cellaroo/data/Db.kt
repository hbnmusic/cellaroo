package app.cellaroo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.ServingMode
import app.cellaroo.core.WineType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** One row per label + vintage + bottle size. */
@Serializable
@Entity(tableName = "wines", indices = [Index("normalizedKey")])
data class WineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val producer: String,
    val wineName: String,
    val appellationRegion: String = "",
    val country: String = "",
    val vintage: Int? = null,
    val wineType: WineType = WineType.RED,
    val grapesJson: String = "[]",
    val abvPercent: Float? = null,
    val bottleSizeMl: Int = 750,
    val normalizedKey: String = "",
    val drinkFromYear: Int? = null,
    val peakStartYear: Int? = null,
    val peakEndYear: Int? = null,
    val drinkUntilYear: Int? = null,
    val windowSource: String? = null,
    val windowUpdatedAt: Long? = null,
    val servingMode: ServingMode? = null,
    val servingReason: String? = null,
    val servingTempC: Int? = null,
    val decantMinutes: Int? = null,
    val servingSource: String? = null,
    val pairingsJson: String = "[]",
    val pairingNotes: String? = null,
    val pairingSource: String? = null,
    val styleSummary: String? = null,
    val referenceNotes: String? = null,
    val myNotes: String? = null,
    val vintageSummary: String? = null,
    val stickerNote: String? = null,
    val marketPriceUsd: Double? = null,
    val marketPriceAsOf: Long? = null,
    val marketPriceSource: String? = null,
    val labelImage: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One row per physical bottle. */
@Serializable
@Entity(
    tableName = "bottles",
    foreignKeys = [
        ForeignKey(entity = WineEntity::class, parentColumns = ["id"], childColumns = ["wineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("wineId"), Index("locationId"), Index("status")],
)
data class BottleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineId: Long,
    val locationId: Long? = null,
    val status: BottleStatus = BottleStatus.IN_CELLAR,
    val purchasePriceUsd: Double? = null,
    val purchaseDate: Long? = null,
    val purchaseSource: String? = null,
    val cropImage: String? = null,
    val addedVia: String = "MANUAL",
    val addedAt: Long = 0,
    val removedAt: Long? = null,
)

@Serializable
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val description: String? = null,
    val lastReconciledAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "critic_scores",
    foreignKeys = [ForeignKey(entity = WineEntity::class, parentColumns = ["id"], childColumns = ["wineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("wineId")],
)
data class CriticScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineId: Long,
    val source: String,
    val sourceLabel: String,
    val rawText: String,
    val score100: Float,
    val rangeLow100: Float? = null,
    val rangeHigh100: Float? = null,
    val origin: String = "PASTE",
    val addedAt: Long = 0,
)

@Serializable
@Entity(
    tableName = "consumption_log",
    foreignKeys = [ForeignKey(entity = WineEntity::class, parentColumns = ["id"], childColumns = ["wineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("wineId"), Index("bottleId")],
)
data class ConsumptionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bottleId: Long,
    val wineId: Long,
    val wineSnapshotTitle: String,
    val consumedDate: Long,
    val outcome: BottleStatus = BottleStatus.CONSUMED,
    val pairedWith: String? = null,
    /** 0.5–5 stars. */
    val userRating: Float? = null,
    val personalNotes: String? = null,
)

/** Raw model output of each photo scan, kept so prompts can be debugged later. */
@Serializable
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val locationId: Long? = null,
    val image: String,
    val modelId: String,
    val rawResponseJson: String,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "paste_log")
data class PasteLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineId: Long? = null,
    val rawText: String,
    val parsedJson: String,
    val createdAt: Long,
)

/** Enough information to undo one batch of changes. */
@Serializable
@Entity(tableName = "change_sets")
data class ChangeSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val label: String,
    val payloadJson: String,
    val createdAt: Long,
    val undone: Boolean = false,
)

@Dao
interface CellarDao {
    @Query("SELECT * FROM wines") fun observeWines(): Flow<List<WineEntity>>
    @Query("SELECT * FROM bottles") fun observeBottles(): Flow<List<BottleEntity>>
    @Query("SELECT * FROM locations ORDER BY sortOrder, id") fun observeLocations(): Flow<List<LocationEntity>>
    @Query("SELECT * FROM critic_scores") fun observeScores(): Flow<List<CriticScoreEntity>>
    @Query("SELECT * FROM consumption_log ORDER BY consumedDate DESC") fun observeLogs(): Flow<List<ConsumptionLogEntity>>
    @Query("SELECT * FROM change_sets WHERE undone = 0 ORDER BY id DESC LIMIT 15") fun observeChangeSets(): Flow<List<ChangeSetEntity>>

    @Query("SELECT * FROM wines") suspend fun wines(): List<WineEntity>
    @Query("SELECT * FROM bottles") suspend fun bottles(): List<BottleEntity>
    @Query("SELECT * FROM locations ORDER BY sortOrder, id") suspend fun locations(): List<LocationEntity>
    @Query("SELECT * FROM critic_scores") suspend fun scores(): List<CriticScoreEntity>
    @Query("SELECT * FROM consumption_log") suspend fun logs(): List<ConsumptionLogEntity>
    @Query("SELECT * FROM scans") suspend fun scans(): List<ScanEntity>
    @Query("SELECT * FROM paste_log") suspend fun pastes(): List<PasteLogEntity>
    @Query("SELECT * FROM change_sets") suspend fun changeSets(): List<ChangeSetEntity>

    @Query("SELECT * FROM wines WHERE id = :id") suspend fun wine(id: Long): WineEntity?
    @Query("SELECT * FROM wines WHERE normalizedKey = :key LIMIT 1") suspend fun wineByKey(key: String): WineEntity?
    @Query("SELECT * FROM bottles WHERE id = :id") suspend fun bottle(id: Long): BottleEntity?
    @Query("SELECT * FROM bottles WHERE id IN (:ids)") suspend fun bottlesByIds(ids: List<Long>): List<BottleEntity>
    @Query("SELECT * FROM change_sets WHERE id = :id") suspend fun changeSet(id: Long): ChangeSetEntity?
    @Query("SELECT * FROM locations WHERE id = :id") suspend fun location(id: Long): LocationEntity?

    @Insert suspend fun insertWine(w: WineEntity): Long
    @Update suspend fun updateWine(w: WineEntity)
    @Query("DELETE FROM wines WHERE id IN (:ids)") suspend fun deleteWines(ids: List<Long>)

    @Insert suspend fun insertBottle(b: BottleEntity): Long
    @Update suspend fun updateBottle(b: BottleEntity)
    @Query("DELETE FROM bottles WHERE id IN (:ids)") suspend fun deleteBottles(ids: List<Long>)

    @Insert suspend fun insertLocation(l: LocationEntity): Long
    @Update suspend fun updateLocation(l: LocationEntity)
    @Query("DELETE FROM locations WHERE id = :id") suspend fun deleteLocation(id: Long)

    @Insert suspend fun insertScore(s: CriticScoreEntity): Long
    @Query("DELETE FROM critic_scores WHERE id = :id") suspend fun deleteScore(id: Long)
    @Query("DELETE FROM critic_scores WHERE wineId = :wineId AND source = :source") suspend fun deleteScoresOf(wineId: Long, source: String)

    @Insert suspend fun insertLog(l: ConsumptionLogEntity): Long
    @Query("DELETE FROM consumption_log WHERE id IN (:ids)") suspend fun deleteLogs(ids: List<Long>)

    @Insert suspend fun insertScan(s: ScanEntity): Long
    @Insert suspend fun insertPaste(p: PasteLogEntity): Long
    @Insert suspend fun insertChangeSet(c: ChangeSetEntity): Long
    @Query("UPDATE change_sets SET undone = 1 WHERE id = :id") suspend fun markUndone(id: Long)

    // Restore support
    @Insert suspend fun insertWines(l: List<WineEntity>)
    @Insert suspend fun insertBottles(l: List<BottleEntity>)
    @Insert suspend fun insertLocations(l: List<LocationEntity>)
    @Insert suspend fun insertScores(l: List<CriticScoreEntity>)
    @Insert suspend fun insertLogs(l: List<ConsumptionLogEntity>)
    @Insert suspend fun insertScans(l: List<ScanEntity>)
    @Insert suspend fun insertPastes(l: List<PasteLogEntity>)
    @Query("DELETE FROM wines") suspend fun clearWines()
    @Query("DELETE FROM locations") suspend fun clearLocations()
    @Query("DELETE FROM scans") suspend fun clearScans()
    @Query("DELETE FROM paste_log") suspend fun clearPastes()
    @Query("DELETE FROM change_sets") suspend fun clearChangeSets()
}

@Database(
    entities = [
        WineEntity::class, BottleEntity::class, LocationEntity::class, CriticScoreEntity::class,
        ConsumptionLogEntity::class, ScanEntity::class, PasteLogEntity::class, ChangeSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CellarDb : RoomDatabase() {
    abstract fun dao(): CellarDao
}
