package com.torecastop.ledger.ui.session

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * The shared "Add photo / thumbnail / Retake / Remove" row used by the sale and
 * trade entry forms. Captures via the system camera into app storage,
 * compresses on capture, and reports the confirmed path via [onPhotoChanged].
 *
 * A retake only replaces the previous photo once the new capture succeeds, so
 * a cancelled retake keeps the original. When [deleteReplacedFiles] is true
 * (new entries) replaced/removed files are deleted from disk; pass false when
 * editing an existing record so its stored photo file is never destroyed
 * before the edit is saved.
 */
@Composable
fun PhotoCaptureRow(
    photoPath: String?,
    onPhotoChanged: (String?) -> Unit,
    filePrefix: String,
    deleteReplacedFiles: Boolean
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
                    withContext(Dispatchers.IO) {
                        PhotoStorage.compress(file)
                        if (deleteReplacedFiles) {
                            photoPath?.takeIf { it != file.absolutePath }?.let { File(it).delete() }
                        }
                    }
                    onPhotoChanged(file.absolutePath)
                }
            } else {
                file?.delete()
            }
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val file = PhotoStorage.newPhotoFile(context, filePrefix)
            pendingFile = file
            takePicture.launch(PhotoStorage.uriFor(context, file))
        }) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (photoPath == null) "Add photo" else "Retake")
        }
        photoPath?.let { path ->
            Spacer(modifier = Modifier.width(12.dp))
            AsyncImage(
                model = File(path),
                contentDescription = "Captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = {
                if (deleteReplacedFiles) File(path).delete()
                onPhotoChanged(null)
            }) { Text("Remove") }
        }
    }
}
