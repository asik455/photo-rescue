package com.photorescue

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var trashed by mutableStateOf<List<TrashedPhoto>>(emptyList()); private set
    var candidates by mutableStateOf<List<Candidate>>(emptyList()); private set
    var vault by mutableStateOf<List<VaultItem>>(emptyList()); private set
    var scanning by mutableStateOf(false); private set
    var protectionOn by mutableStateOf(ShadowScheduler.isEnabled(app)); private set
    var message by mutableStateOf<String?>(null)

    fun refreshTrash() {
        if (Build.VERSION.SDK_INT < 30) return
        viewModelScope.launch {
            trashed = try { TrashRepository.list(ctx) } catch (e: SecurityException) { emptyList() }
        }
    }

    fun scan() = viewModelScope.launch {
        scanning = true
        candidates = try { CacheScanner.scan(ctx) } catch (e: Exception) { emptyList() }
        scanning = false
        message = "${candidates.size} possible photo(s) found"
    }

    fun recover(files: List<File>) = viewModelScope.launch {
        val n = withContext(Dispatchers.IO) {
            files.count { f ->
                val mime = Gallery.sniffMime(f) ?: return@count false
                Gallery.save(ctx, "recovered_${System.nanoTime()}.${Gallery.ext(mime)}", mime) { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
            }
        }
        message = "Recovered $n photo(s) to Pictures/Recovered"
    }

    fun refreshVault() = viewModelScope.launch {
        vault = withContext(Dispatchers.IO) { Vault.list(ctx) }
    }

    fun restoreVault(ids: Set<String>) = viewModelScope.launch {
        val n = withContext(Dispatchers.IO) {
            vault.filter { it.id in ids }.count { item ->
                Gallery.save(ctx, item.name, item.mime) { out ->
                    Vault.openStream(ctx, item.id).use { it.copyTo(out) }
                }
            }
        }
        message = "Restored $n photo(s) to Pictures/Recovered"
    }

    fun deleteVault(ids: Set<String>) = viewModelScope.launch {
        withContext(Dispatchers.IO) { Vault.remove(ctx, ids) }
        refreshVault()
    }

    fun enableProtection(includeExisting: Boolean) = viewModelScope.launch {
        withContext(Dispatchers.IO) { ShadowScheduler.enable(ctx, includeExisting) }
        protectionOn = true
        message = "Protection is on"
        kotlinx.coroutines.delay(1500)
        refreshVault()
    }

    fun disableProtection() {
        ShadowScheduler.disable(ctx)
        protectionOn = false
    }
}
