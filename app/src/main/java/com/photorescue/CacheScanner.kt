package com.photorescue

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Layer 2: looks for image files on disk that the gallery no longer lists:
 * thumbnails, leftover .pending/.trashed files, and chat-app media folders.
 */
object CacheScanner {

    private val roots = listOf(
        "DCIM/.thumbnails", "Pictures/.thumbnails", "DCIM", "Pictures", "Download",
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
        "WhatsApp/Media/WhatsApp Images",
        "Android/media/org.telegram.messenger/Telegram/Telegram Images"
    )

    @Suppress("DEPRECATION")
    private fun indexedPaths(ctx: Context): Set<String> {
        val set = HashSet<String>()
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DATA), null, null, null
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(set::add) }
        return set
    }

    suspend fun scan(ctx: Context): List<Candidate> = withContext(Dispatchers.IO) {
        val base = Environment.getExternalStorageDirectory()
        val known = indexedPaths(ctx)
        val seen = HashSet<String>()
        val out = ArrayList<Candidate>()
        for (rel in roots) {
            val root = File(base, rel)
            if (!root.isDirectory) continue
            root.walkTopDown().maxDepth(4).onFail { _, _ -> }.filter { it.isFile }.forEach { f ->
                val path = f.absolutePath
                if (path in known || !seen.add(path)) return@forEach
                // On Android 11+ the Trash tab already covers .trashed files
                if (Build.VERSION.SDK_INT >= 30 && f.name.startsWith(".trashed")) return@forEach
                val thumb = path.contains("thumbnail", ignoreCase = true)
                if (f.length() < (if (thumb) 1_000 else 4_000)) return@forEach
                if (Gallery.sniffMime(f) == null) return@forEach
                out += Candidate(f, f.parentFile?.name ?: "", thumb)
            }
        }
        out.sortedByDescending { it.file.lastModified() }.take(1500)
    }
}
