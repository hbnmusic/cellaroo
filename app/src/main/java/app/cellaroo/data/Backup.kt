package app.cellaroo.data

import android.content.Context
import android.net.Uri
import app.cellaroo.core.Csv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Full backup as a ZIP (cellar.json + images/), plain JSON, and a flat CSV for spreadsheets. */
class Backup(private val context: Context, private val repo: CellarRepository, private val images: ImageStore) {

    suspend fun exportZip(uri: Uri, appVersion: String) {
        val data = repo.snapshot(appVersion)
        val out = context.contentResolver.openOutputStream(uri) ?: error("Cannot open the chosen file")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("cellar.json"))
            zip.write(AppJson.encodeToString(BackupData.serializer(), data).toByteArray())
            zip.closeEntry()
            images.dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("images/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun exportJson(uri: Uri, appVersion: String) {
        val data = repo.snapshot(appVersion)
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(AppJson.encodeToString(BackupData.serializer(), data).toByteArray())
        } ?: error("Cannot open the chosen file")
    }

    suspend fun exportCsv(uri: Uri) {
        val data = repo.snapshot("")
        val text = buildCsv(data)
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Cannot open the chosen file")
    }

    /** Restores a ZIP or JSON backup, replacing the current cellar. Returns the number of bottles restored. */
    suspend fun restore(uri: Uri): Int {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read the chosen file")
        val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        var json: String? = null
        val staged = File(context.cacheDir, "restore_images").apply { deleteRecursively(); mkdirs() }
        if (isZip) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (name == "cellar.json") json = zip.readBytes().toString(Charsets.UTF_8)
                    else if (name.startsWith("images/") && !e.isDirectory) {
                        // Use only the last path segment so a crafted archive cannot write outside the folder.
                        val safe = File(name).name
                        if (safe.isNotBlank()) File(staged, safe).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        } else {
            json = bytes.toString(Charsets.UTF_8)
        }
        val data = AppJson.decodeFromString(BackupData.serializer(), json ?: error("No cellar.json in this backup"))
        repo.restore(data)
        if (isZip) {
            images.dir.listFiles()?.forEach { it.delete() }
            staged.listFiles()?.forEach { it.copyTo(File(images.dir, it.name), overwrite = true) }
        }
        staged.deleteRecursively()
        return data.bottles.size
    }

    companion object {
        fun buildCsv(data: BackupData): String {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fun d(t: Long?) = t?.let { day.format(Date(it)) } ?: ""
            val wines = data.wines.associateBy { it.id }
            val locs = data.locations.associateBy { it.id }
            val scores = data.scores.groupBy { it.wineId }
            val sb = StringBuilder()
            sb.appendLine(
                Csv.line(
                    listOf(
                        "bottle_id", "status", "producer", "wine", "vintage", "type", "region", "country", "grapes", "size_ml",
                        "location", "purchase_price_usd", "purchase_date", "purchase_source", "market_price_usd", "market_price_as_of",
                        "drink_from", "peak_start", "peak_end", "drink_until", "window_source", "avg_critic_score", "critic_scores",
                        "serving_mode", "pairings", "my_notes",
                    )
                )
            )
            for (b in data.bottles) {
                val w = wines[b.wineId] ?: continue
                val s = scores[w.id].orEmpty()
                sb.appendLine(
                    Csv.line(
                        listOf(
                            b.id, b.status, w.producer, w.wineName, w.vintage ?: "NV", w.wineType, w.appellationRegion, w.country,
                            decodeStrings(w.grapesJson).joinToString("; "), w.bottleSizeMl, locs[b.locationId]?.name ?: "",
                            b.purchasePriceUsd, d(b.purchaseDate), b.purchaseSource, w.marketPriceUsd, d(w.marketPriceAsOf),
                            w.drinkFromYear, w.peakStartYear, w.peakEndYear, w.drinkUntilYear, w.windowSource,
                            s.takeIf { it.isNotEmpty() }?.map { it.score100 }?.average()?.let { "%.1f".format(Locale.US, it) },
                            s.joinToString("; ") { "${it.sourceLabel} ${it.rawText}" },
                            w.servingMode, decodePairings(w.pairingsJson).joinToString("; ") { p -> p.tag.label + (p.dish?.let { ": $it" } ?: "") },
                            w.myNotes,
                        )
                    )
                )
            }
            return sb.toString()
        }
    }
}
