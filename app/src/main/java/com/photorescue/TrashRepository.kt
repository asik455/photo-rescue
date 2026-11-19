package com.photorescue

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Layer 1: the Android 11+ system recycle bin (items stay ~30 days). */
@RequiresApi(30)
object TrashRepository {

    suspend fun list(ctx: Context): List<TrashedPhoto> = withContext(Dispatchers.IO) {
        val out = mutableListOf<TrashedPhoto>()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media._ID} DESC")
        }
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        ctx.contentResolver.query(collection, projection, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += TrashedPhoto(ContentUris.withAppendedId(collection, id), c.getString(1) ?: "photo_$id")
            }
        }
        out
    }

    /** System dialog asks the user to confirm; passing trash=false means "restore". */
    fun restoreRequest(ctx: Context, uris: List<Uri>) =
        MediaStore.createTrashRequest(ctx.contentResolver, uris, false).intentSender
}
