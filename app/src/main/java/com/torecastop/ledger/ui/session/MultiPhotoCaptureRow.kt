package com.torecastop.ledger.ui.session

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.torecastop.ledger.data.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Capture any number of photos (not just one) — used for both whole-sale/trade
 * photos and per-item photos. (v1.3 revision, replacing the single-photo
 * [PhotoCaptureRow] for sales and trades.)
 *
 * Each capture is compressed on the way in; removing a photo here deletes its
 * file immediately (callers own the list, so an unsaved capture that's later
 * discarded — e.g. cancelling the whole form — is the caller's job to clean
 * up via the same file paths).
 */
@Composable
fun MultiPhotoCaptureRow(
    photoPaths: List<String>,
    onPhotosChanged: (List<String>) -> Unit,
    filePrefix: String,
    label: String = "Add photo"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            pendingFile = null
            if (success && file != null) {
                scope.launch {
                    withContext(Dispatchers.IO) { PhotoStorage.compress(file) }
                    onPhotosChanged(photoPaths + file.absolutePath)
                }
            } else {
                file?.delete()
            }
        }

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        photoPaths.forEach { path ->
            Box(modifier = Modifier.size(64.dp)) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "Captured photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                IconButton(
                    onClick = {
                        File(path).delete()
                        onPhotosChanged(photoPaths - path)
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .offset(x = 6.dp, y = (-6).dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove photo",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        OutlinedButton(onClick = {
            val file = PhotoStorage.newPhotoFile(context, filePrefix)
            pendingFile = file
            takePicture.launch(PhotoStorage.uriFor(context, file))
        }) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    }
}
