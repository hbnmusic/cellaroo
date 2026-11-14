package app.cellaroo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo that has been rotated upright, downscaled and saved inside the app. */
data class StoredPhoto(val name: String, val width: Int, val height: Int, val jpeg: ByteArray)

/**
 * Keeps photos in filesDir/images. The database stores file names only, so backups stay portable.
 */
class ImageStore(private val context: Context) {
    val dir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    fun file(name: String?): File? = name?.let { File(dir, it) }?.takeIf { it.exists() }

    fun tempCaptureFile(): File = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")

    fun importUri(uri: Uri): StoredPhoto? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return importBytes(bytes)
    }

    fun importFile(f: File): StoredPhoto? = importBytes(f.readBytes())

    private fun importBytes(bytes: ByteArray): StoredPhoto? {
        val rotation = bytes.inputStream().use { exifRotation(it) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val scale = (MAX_EDGE.toFloat() / max(decoded.width, decoded.height)).coerceAtMost(1f)
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        val jpeg = ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val name = "scan_${System.currentTimeMillis()}.jpg"
        File(dir, name).writeBytes(jpeg)
        return StoredPhoto(name, upright.width, upright.height, jpeg)
    }

    /** Cuts one bottle out of a stored photo. [box] is [ymin, xmin, ymax, xmax] on a 0–1000 scale. */
    fun crop(photoName: String, box: List<Int>, suffix: String): String? = try {
        val src = BitmapFactory.decodeFile(File(dir, photoName).path)
        val pad = 0.02f
        val left = ((box[1] / 1000f - pad) * src.width).roundToInt().coerceIn(0, src.width - 2)
        val top = ((box[0] / 1000f - pad) * src.height).roundToInt().coerceIn(0, src.height - 2)
        val right = ((box[3] / 1000f + pad) * src.width).roundToInt().coerceIn(left + 1, src.width)
        val bottom = ((box[2] / 1000f + pad) * src.height).roundToInt().coerceIn(top + 1, src.height)
        var cut = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        val longest = max(cut.width, cut.height)
        if (longest > 720) {
            val s = 720f / longest
            cut = Bitmap.createScaledBitmap(cut, (cut.width * s).roundToInt().coerceAtLeast(1), (cut.height * s).roundToInt().coerceAtLeast(1), true)
        }
        val name = photoName.removeSuffix(".jpg") + "_$suffix.jpg"
        File(dir, name).outputStream().use { cut.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    } catch (e: Exception) {
        null
    }

    private fun exifRotation(input: InputStream): Int = try {
        ExifInterface(input).rotationDegrees
    } catch (e: Exception) {
        0
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (max(w, h) / (s * 2) >= MAX_EDGE) s *= 2
        return s
    }

    companion object {
        const val MAX_EDGE = 2048
    }
}
