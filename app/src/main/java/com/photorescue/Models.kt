package com.photorescue

import android.net.Uri
import java.io.File

data class TrashedPhoto(val uri: Uri, val name: String)
data class Candidate(val file: File, val label: String, val isThumb: Boolean)
data class VaultItem(val id: String, val name: String, val mime: String, val addedAt: Long)
