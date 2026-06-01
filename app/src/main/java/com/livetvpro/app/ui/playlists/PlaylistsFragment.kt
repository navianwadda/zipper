package com.livetvpro.app.ui.playlists

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Playlist
import com.livetvpro.app.ui.adapters.PlaylistItem
import com.livetvpro.app.utils.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlaylistsFragment : Fragment() {

    private val viewModel: PlaylistsViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> showAddPlaylistDialog(isFile = true, fileUri = uri) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                PlaylistsScreen(
                    viewModel       = viewModel,
                    onPlaylistClick = { playlist ->
                        findNavController().navigate(
                            R.id.action_playlists_to_category,
                            bundleOf("categoryId" to playlist.id, "categoryName" to playlist.title)
                        )
                    },
                    onEditClick     = { showEditPlaylistDialog(it) },
                    onDeleteClick   = { showDeleteConfirmationDialog(it) },
                    onAddFile       = { openFilePicker() },
                    onAddUrl        = { showAddPlaylistDialog(isFile = false) },
                )
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-mpegURL"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-mpegURL", "audio/x-mpegurl", "application/vnd.apple.mpegurl"))
        }
        filePickerLauncher.launch(intent)
    }

    private fun suppressKeyboardForTv(dialog: android.app.Dialog, vararg fields: EditText) {
        if (!DeviceUtils.isTvDevice) return
        fields.forEach { it.isFocusable = false; it.isFocusableInTouchMode = false }
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        dialog.setOnShowListener { imm.hideSoftInputFromWindow(fields.firstOrNull()?.windowToken, 0) }
    }

    private fun showAddPlaylistDialog(isFile: Boolean, fileUri: Uri? = null) {
        val dialogView  = layoutInflater.inflate(R.layout.dialog_add_playlist, null)
        val titleInput  = dialogView.findViewById<EditText>(R.id.input_title)
        val urlInput    = dialogView.findViewById<EditText>(R.id.input_url)
        if (isFile && fileUri != null) {
            urlInput.isEnabled = false
            val name = requireContext().contentResolver.query(fileUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null
            } ?: fileUri.lastPathSegment ?: fileUri.toString()
            titleInput.setText(name)
            urlInput.setText(fileUri.toString())
        }
        val builtDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Playlist").setView(dialogView)
            .setPositiveButton("Add", null).setNegativeButton("Cancel", null).create()
        suppressKeyboardForTv(builtDialog, titleInput, urlInput)
        builtDialog.show()
        builtDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            requestFocus()
            setOnClickListener {
                val title = titleInput.text.toString().trim()
                val url   = urlInput.text.toString().trim()
                if (title.isEmpty()) { Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (!isFile && url.isEmpty()) { Toast.makeText(requireContext(), "URL is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (isFile && fileUri != null) {
                    try { requireContext().contentResolver.takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                    viewModel.addPlaylist(title, "", true, fileUri.toString())
                } else { viewModel.addPlaylist(title, url, false, "") }
                builtDialog.dismiss()
            }
        }
    }

    private fun showEditPlaylistDialog(playlist: Playlist) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_playlist, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.input_title)
        val urlInput   = dialogView.findViewById<EditText>(R.id.input_url)
        titleInput.setText(playlist.title)
        if (playlist.isFile) { urlInput.setText(playlist.filePath); urlInput.isEnabled = false }
        else urlInput.setText(playlist.url)
        val builtDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update Playlist Details").setView(dialogView)
            .setPositiveButton("Update", null)
            .setNeutralButton("Delete") { d, _ -> d.dismiss(); showDeleteConfirmationDialog(playlist) }
            .setNegativeButton("Cancel", null).create()
        suppressKeyboardForTv(builtDialog, titleInput, urlInput)
        builtDialog.show()
        builtDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            requestFocus()
            setOnClickListener {
                val newTitle = titleInput.text.toString().trim()
                val newUrl   = urlInput.text.toString().trim()
                if (newTitle.isEmpty()) { Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (!playlist.isFile && newUrl.isEmpty()) { Toast.makeText(requireContext(), "URL is required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                viewModel.updatePlaylist(playlist.copy(title = newTitle, url = if (!playlist.isFile) newUrl else playlist.url))
                builtDialog.dismiss()
            }
        }
    }

    private fun showDeleteConfirmationDialog(playlist: Playlist) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"${playlist.title}\"?")
            .setPositiveButton("Delete") { d, _ ->
                if (playlist.isFile && playlist.filePath.isNotEmpty()) {
                    try { requireContext().contentResolver.releasePersistableUriPermission(Uri.parse(playlist.filePath), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                }
                viewModel.deletePlaylist(playlist)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null).show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }
}

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onEditClick: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit,
    onAddFile: () -> Unit,
    onAddUrl: () -> Unit,
) {
    val playlists by viewModel.playlists.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    var fabExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            playlists.isEmpty() -> Text(
                text     = "No Playlists Found",
                modifier = Modifier.align(Alignment.Center),
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .padding(bottom = 90.dp),
            ) {
                items(playlists) { playlist ->
                    PlaylistItem(
                        playlist      = playlist,
                        onClick       = { onPlaylistClick(playlist) },
                        onEditClick   = { onEditClick(playlist) },
                        onDeleteClick = { onDeleteClick(playlist) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible  = fabExpanded,
            enter    = scaleIn(),
            exit     = scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = { fabExpanded = false; onAddFile() },
                    icon    = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                    text    = { Text("From File") },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ExtendedFloatingActionButton(
                    onClick = { fabExpanded = false; onAddUrl() },
                    icon    = { Icon(painterResource(R.drawable.ic_network_stream), contentDescription = null) },
                    text    = { Text("From URL") },
                )
            }
        }

        FloatingActionButton(
            onClick  = { fabExpanded = !fabExpanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Playlist")
        }
    }
}
