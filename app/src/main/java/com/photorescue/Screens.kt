package com.photorescue

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

@Composable
private fun Info(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ActionBar(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text(label) }
}

@Composable
private fun <T> SelectableGrid(
    items: List<T>,
    key: (T) -> String,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { key(it) }) { item ->
            val k = key(item)
            val isSel = k in selected
            Box(Modifier.aspectRatio(1f).clickable { onToggle(k) }) {
                content(item)
                if (isSel) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Checkbox(checked = isSel, onCheckedChange = { onToggle(k) }, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

// ---------- Tab 1: system recycle bin ----------
@Composable
fun TrashScreen(vm: MainViewModel, onRestore: (List<Uri>) -> Unit) {
    if (Build.VERSION.SDK_INT < 30) {
        Info("The system recycle bin needs Android 11 or newer. Try the Scan and Vault tabs.")
        return
    }
    var sel by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { vm.refreshTrash() }
    Column(Modifier.fillMaxSize()) {
        if (vm.trashed.isEmpty()) {
            Box(Modifier.weight(1f)) { Info("Nothing in the system recycle bin. Items are kept about 30 days after deletion.") }
        } else {
            SelectableGrid(vm.trashed, { it.uri.toString() }, sel, { sel = sel.toggle(it) }, Modifier.weight(1f)) {
                AsyncImage(it.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        ActionBar("Restore selected (${sel.size})", sel.isNotEmpty()) {
            onRestore(vm.trashed.filter { it.uri.toString() in sel }.map { it.uri })
            sel = emptySet()
        }
    }
}

// ---------- Tab 2: cache / thumbnail scan ----------
@Composable
fun ScanScreen(vm: MainViewModel) {
    var sel by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.scan() }, enabled = !vm.scanning) { Text("Scan device") }
            Spacer(Modifier.width(12.dp))
            if (vm.scanning) CircularProgressIndicator(Modifier.size(24.dp))
        }
        if (vm.candidates.isEmpty()) {
            Box(Modifier.weight(1f)) {
                Info("Scans thumbnails, leftover temp files and chat-app media folders for images the gallery no longer shows. Results are often low quality.")
            }
        } else {
            SelectableGrid(vm.candidates, { it.file.absolutePath }, sel, { sel = sel.toggle(it) }, Modifier.weight(1f)) { c ->
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(c.file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Text(
                        if (c.isThumb) "thumbnail" else c.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp)
                    )
                }
            }
        }
        ActionBar("Recover selected (${sel.size})", sel.isNotEmpty()) {
            vm.recover(vm.candidates.filter { it.file.absolutePath in sel }.map { it.file })
            sel = emptySet()
        }
    }
}

// ---------- Tab 3: encrypted vault + protection ----------
@Composable
private fun VaultThumb(id: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Vault.openStream(ctx, id).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

@Composable
fun VaultScreen(vm: MainViewModel) {
    var sel by remember { mutableStateOf(setOf<String>()) }
    var askExisting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshVault() }

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Protection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (vm.protectionOn) "New photos are copied to an encrypted vault, so you can always bring them back."
                        else "Off. Turn on to make future deletions fully recoverable.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(vm.protectionOn, onCheckedChange = { if (it) askExisting = true else vm.disableProtection() })
            }
        }
        if (askExisting) {
            AlertDialog(
                onDismissRequest = { askExisting = false },
                title = { Text("Turn on protection") },
                text = { Text("Also back up the photos already on this phone? This uses extra storage.") },
                confirmButton = { TextButton({ vm.enableProtection(true); askExisting = false }) { Text("All photos") } },
                dismissButton = { TextButton({ vm.enableProtection(false); askExisting = false }) { Text("Only new photos") } }
            )
        }
        if (vm.vault.isEmpty()) {
            Box(Modifier.weight(1f)) { Info("The vault is empty.") }
        } else {
            SelectableGrid(vm.vault, { it.id }, sel, { sel = sel.toggle(it) }, Modifier.weight(1f)) { VaultThumb(it.id) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.restoreVault(sel); sel = emptySet() }, enabled = sel.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Restore (${sel.size})")
            }
            OutlinedButton({ vm.deleteVault(sel); sel = emptySet() }, enabled = sel.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Delete from vault")
            }
        }
    }
}
