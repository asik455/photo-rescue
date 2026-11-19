package com.photorescue

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private fun mediaPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShadowScheduler.ensureScheduled(this)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, mediaPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refreshTrash() }
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Photo Rescue needs access to your photos to find and restore deleted ones.")
            Spacer(Modifier.height(16.dp))
            Button({ permLauncher.launch(mediaPermission()) }) { Text("Grant access") }
            if (Build.VERSION.SDK_INT >= 34) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "On Android 14+, choose \"Allow all\" - partial access hides most photos from the app.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Delete, null) }, label = { Text("Bin") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Search, null) }, label = { Text("Scan") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Lock, null) }, label = { Text("Vault") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TrashScreen(vm) { uris ->
                    if (Build.VERSION.SDK_INT >= 30) {
                        trashLauncher.launch(IntentSenderRequest.Builder(TrashRepository.restoreRequest(ctx, uris)).build())
                    }
                }
                1 -> ScanScreen(vm)
                else -> VaultScreen(vm)
            }
        }
    }
}
