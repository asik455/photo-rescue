package com.photorescue

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/** Helpers for identifying image files and writing recovered ones back to the gallery. */
object Gallery {

    /** Detects image type from magic bytes (hidden cache files often have no extension). */
    fun sniffMime(f: File): String? {
        val b = ByteArray(12)
        val n = try { f.inputStream().use { it.read(b) } } catch (e: Exception) { return null }
        if (n < 12) return null
        fun at(i: Int) = b[i].toInt() and 0xFF
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
            at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "image/gif"
            at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                at(8) == 0x57 && at(9) == 0x45 -> "image/webp"
            else -> null
        }
    }

    fun ext(mime: String) = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    /** Saves into Pictures/Recovered via MediaStore (no storage permission needed to write). */
    fun save(ctx: Context, name: String, mime: String, writer: (OutputStream) -> Unit): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Recovered")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)!!.use(writer)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
