package com.torecastop.ledger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * On-device storage for optional sale photos. Files live under
 * `filesDir/photos/` (declared in res/xml/file_paths.xml) so they can be shared
 * through the app's FileProvider and bundled into the export zip.
 *
 * Photos are downscaled and re-encoded after capture to keep both on-device
 * storage and the export bundle small.
 */
object PhotoStorage {

    /** Longest edge kept after compression (px). */
    private const val MAX_DIMENSION = 1600

    /** JPEG quality for the re-encoded photo (0–100). */
    private const val JPEG_QUALITY = 75

    private fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    /** A fresh, uniquely named jpg target for the camera to write into. */
    fun newPhotoFile(context: Context, prefix: String = "sale"): File =
        File(photosDir(context), "${prefix}_${System.currentTimeMillis()}.jpg")

    /** FileProvider content Uri the camera app can write to. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Downscales [file] so its longest edge is at most [MAX_DIMENSION] and
     * re-encodes it as JPEG, applying the original EXIF orientation so the
     * result is upright. Overwrites the file in place. Safe to run off the main
     * thread; failures leave the original untouched.
     */
    fun compress(file: File) {
        if (!file.exists()) return

        // Read the orientation before we decode; re-encoding drops EXIF tags.
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return

        val scaled = scaleToMax(decoded, MAX_DIMENSION)
        if (scaled !== decoded) decoded.recycle()

        val upright = applyOrientation(scaled, orientation)
        if (upright !== scaled) scaled.recycle()

        runCatching {
            FileOutputStream(file).use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }
        upright.recycle()
    }

    /** Largest power-of-two subsample that keeps the longest edge ≥ [maxDim]. */
    private fun sampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= maxDim) sample *= 2
        return sample
    }

    private fun scaleToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
