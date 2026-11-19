package com.photorescue

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.MediaStore
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Preventive protection: whenever the gallery changes, new photos are copied into the vault,
 * so a later deletion is always recoverable. A periodic job is a safety net.
 */
object ShadowScheduler {
    private const val PREFS = "shadow"
    const val KEY = "last_id"
    private const val TRIGGER = "shadow_trigger"
    private const val PERIODIC = "shadow_periodic"

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(ctx: Context) = prefs(ctx).getLong(KEY, -1L) >= 0

    private fun maxId(ctx: Context): Long {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media._ID} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
        }
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), args, null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return 0L
    }

    fun enable(ctx: Context, includeExisting: Boolean) {
        val base = if (includeExisting) 0L else maxId(ctx)
        prefs(ctx).edit().putLong(KEY, base).apply()
        schedule(ctx, ExistingWorkPolicy.REPLACE)
        schedulePeriodic(ctx)
        if (includeExisting) {
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<ShadowBackupWorker>().build())
        }
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().putLong(KEY, -1L).apply()
        WorkManager.getInstance(ctx).cancelUniqueWork(TRIGGER)
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
    }

    fun ensureScheduled(ctx: Context) {
        if (!isEnabled(ctx)) return
        schedule(ctx, ExistingWorkPolicy.KEEP)
        schedulePeriodic(ctx)
    }

    /** One-shot job that fires when the gallery changes; the worker re-schedules itself. */
    fun schedule(ctx: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(3, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(15, TimeUnit.SECONDS)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = OneTimeWorkRequestBuilder<ShadowBackupWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("trigger" to true))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(TRIGGER, policy, req)
    }

    private fun schedulePeriodic(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ShadowBackupWorker>(6, TimeUnit.HOURS).build()
        )
    }
}

class ShadowBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        try {
            val prefs = ShadowScheduler.prefs(ctx)
            var last = prefs.getLong(ShadowScheduler.KEY, -1L)
            if (last < 0) return@withContext Result.success()

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE
            )
            ctx.contentResolver.query(
                collection, projection,
                "${MediaStore.Images.Media._ID} > ?", arrayOf(last.toString()),
                "${MediaStore.Images.Media._ID} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = ContentUris.withAppendedId(collection, id)
                    try {
                        Vault.backup(ctx, uri, id.toString(), c.getString(1) ?: "photo_$id", c.getString(2) ?: "image/jpeg")
                    } catch (e: Exception) { /* skip unreadable item */ }
                    last = id
                    prefs.edit().putLong(ShadowScheduler.KEY, last).apply()
                }
            }
            Result.success()
        } catch (e: SecurityException) {
            Result.failure() // permission revoked
        } finally {
            if (inputData.getBoolean("trigger", false)) {
                ShadowScheduler.schedule(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
    }
}
