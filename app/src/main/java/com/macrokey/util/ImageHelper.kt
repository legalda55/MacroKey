package com.macrokey.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageHelper {

    private const val MAX_SIZE = 1024
    private const val IMAGE_DIR = "block_images"
    private const val QUALITY = 85
    private const val AUTHORITY = "com.macrokey.fileprovider"

    /**
     * Copies an image from the given URI into internal storage,
     * downscaling to MAX_SIZE x MAX_SIZE if needed.
     * Returns the absolute path of the saved file, or null on failure.
     */
    fun saveImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            val scaled = scaleDown(original)
            if (scaled !== original) original.recycle()

            val dir = File(context.filesDir, IMAGE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${UUID.randomUUID()}.webp")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.WEBP, QUALITY, out)
            }
            scaled.recycle()

            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes the image file at the given path. */
    fun deleteImage(path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            File(path).delete()
        } catch (_: Exception) {}
    }

    /** Loads a bitmap from internal storage path, returning null on failure. */
    fun loadBitmap(path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns a content:// URI for the image file via FileProvider,
     * so it can be shared with other apps (clipboard, share intent).
     */
    fun getContentUri(context: Context, path: String?): Uri? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            FileProvider.getUriForFile(context, AUTHORITY, file)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= MAX_SIZE && h <= MAX_SIZE) return bitmap

        val ratio = minOf(MAX_SIZE.toFloat() / w, MAX_SIZE.toFloat() / h)
        val newW = (w * ratio).toInt()
        val newH = (h * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
