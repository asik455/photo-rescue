package com.photorescue

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** Layer 3 storage: encrypted copies of photos kept in the app's private storage. */
object Vault {
    private val lock = Any()

    private fun dir(ctx: Context) = File(ctx.filesDir, "vault").apply { mkdirs() }
    private fun index(ctx: Context) = File(dir(ctx), "index.json")

    private fun enc(ctx: Context, id: String): EncryptedFile {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedFile.Builder(
            ctx, File(dir(ctx), "$id.enc"), key,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun readIndex(ctx: Context): JSONArray =
        try { JSONArray(index(ctx).readText()) } catch (e: Exception) { JSONArray() }

    fun list(ctx: Context): List<VaultItem> = synchronized(lock) {
        val a = readIndex(ctx)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            VaultItem(o.getString("id"), o.getString("name"), o.getString("mime"), o.getLong("at"))
        }.sortedByDescending { it.addedAt }
    }

    fun backup(ctx: Context, uri: Uri, id: String, name: String, mime: String) {
        synchronized(lock) {
            val target = File(dir(ctx), "$id.enc")
            if (target.exists()) return
            try {
                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                    enc(ctx, id).openFileOutput().use { input.copyTo(it) }
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            val a = readIndex(ctx)
            a.put(JSONObject().put("id", id).put("name", name).put("mime", mime).put("at", System.currentTimeMillis()))
            index(ctx).writeText(a.toString())
        }
    }

    fun openStream(ctx: Context, id: String): InputStream = enc(ctx, id).openFileInput()

    fun remove(ctx: Context, ids: Collection<String>) = synchronized(lock) {
        ids.forEach { File(dir(ctx), "$it.enc").delete() }
        val a = readIndex(ctx)
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("id") !in ids) out.put(o)
        }
        index(ctx).writeText(out.toString())
    }
}
