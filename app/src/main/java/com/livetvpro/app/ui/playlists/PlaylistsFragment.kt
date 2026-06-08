package com.livetvpro.app.ui.playlists

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.livetvpro.app.R
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaylistsFragment : Fragment() {

    private val viewModel: PlaylistsViewModel by viewModels()

    @Inject lateinit var themeManager: ThemeManager

    private var pendingFileUri by mutableStateOf<Uri?>(null)
    private var showFileDialog by mutableStateOf(false)

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val displayName = requireContext().contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst())
                        cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    else null
                } ?: uri.lastPathSegment ?: uri.toString()

                pendingFileUri = uri
                showFileDialog = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        try {
            requireActivity().findViewById<TextView>(R.id.toolbar_title)?.text = "Playlists"
        } catch (_: Exception) {}

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LiveTVProTheme(themeManager) {
                    PlaylistsScreen(
                        viewModel = viewModel,
                        isTvDevice = DeviceUtils.isTvDevice,
                        onNavigateToCategory = { id, name ->
                            findNavController().navigate(
                                R.id.action_playlists_to_category,
                                bundleOf(
                                    "categoryId" to id,
                                    "categoryName" to name
                                )
                            )
                        },
                        onPickFile = { openFilePicker() },
                        pendingFileUri = if (showFileDialog) pendingFileUri else null,
                        onFileDialogDismissed = {
                            showFileDialog = false
                            pendingFileUri = null
                        },
                        onError = { message ->
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-mpegURL"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/x-mpegURL",
                    "audio/x-mpegurl",
                    "application/vnd.apple.mpegurl"
                )
            )
        }
        filePickerLauncher.launch(intent)
    }
}
