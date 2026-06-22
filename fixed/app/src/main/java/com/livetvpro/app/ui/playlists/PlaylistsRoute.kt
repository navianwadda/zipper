package com.livetvpro.app.ui.playlists

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.livetvpro.app.ui.navigation.Routes
import com.livetvpro.app.utils.DeviceUtils

@Composable
fun PlaylistsRoute(navController: NavController) {
    val context = LocalContext.current

    var pendingFileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showFileDialog by rememberSaveable { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                pendingFileUri = uri
                showFileDialog = true
            }
        }
    }

    PlaylistsScreen(
        isTvDevice = DeviceUtils.isTvDevice || DeviceUtils.isTablet,
        onNavigateToCategory = { id, name ->
            navController.navigate(Routes.categoryChannels(id, name))
        },
        onPickFile = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            filePickerLauncher.launch(intent)
        },
        pendingFileUri = if (showFileDialog) pendingFileUri else null,
        onFileDialogDismissed = {
            showFileDialog = false
            pendingFileUri = null
        },
        onError = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    )
}
