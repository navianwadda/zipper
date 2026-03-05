package com.livetvpro.app.ui.playlists

import android.animation.ValueAnimator
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Playlist
import com.livetvpro.app.databinding.FragmentPlaylistsBinding
import com.livetvpro.app.ui.adapters.PlaylistAdapter
import com.livetvpro.app.utils.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlaylistsFragment : Fragment() {
    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlaylistsViewModel by viewModels()
    private lateinit var playlistAdapter: PlaylistAdapter
    private var isFabExpanded = false
    private var scrimView: View? = null
    private var fabFileContainer: LinearLayout? = null
    private var fabUrlContainer: LinearLayout? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> showAddPlaylistDialog(isFile = true, fileUri = uri) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val toolbarTitle = requireActivity().findViewById<TextView>(R.id.toolbar_title)
            toolbarTitle?.text = "Playlists"
        } catch (e: Exception) {}
        setupRecyclerView()
        setupAnimatedFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                findNavController().navigate(
                    R.id.action_playlists_to_category,
                    bundleOf("categoryId" to playlist.id, "categoryName" to playlist.title)
                )
            },
            onEditClick = { playlist -> showEditPlaylistDialog(playlist) },
            onDeleteClick = { playlist -> showDeleteConfirmationDialog(playlist) }
        )
        binding.recyclerViewPlaylists.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = playlistAdapter
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    if (isFabExpanded) {
                        collapseFab()
                        return
                    }
                    if (dy > 0) {
                        binding.fabAddPlaylist.hide()
                    } else if (dy < 0) {
                        binding.fabAddPlaylist.show()
                    }
                }
            })
        }
    }

    private fun setupAnimatedFab() {
        if (DeviceUtils.isTvDevice) {
            binding.fabAddPlaylist.setOnClickListener { showTvAddPlaylistDialog() }
            binding.fabAddPlaylist.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                    showTvAddPlaylistDialog()
                    true
                } else false
            }
            binding.fabAddPlaylist.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFFFB300.toInt())
            binding.fabAddPlaylist.imageTintList =
                android.content.res.ColorStateList.valueOf(0xFF3E2000.toInt())
            return
        }

        val rootLayout = binding.root as ConstraintLayout
        val context = requireContext()

        scrimView = View(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            setBackgroundColor(0x80000000.toInt())
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { collapseFab() }
        }
        rootLayout.addView(scrimView)

        fabFileContainer = createFabWithLabel(context, rootLayout, "Add Playlist File", R.drawable.ic_folder, isTopItem = true) {
            collapseFab()
            openFilePicker()
        }
        rootLayout.addView(fabFileContainer)

        fabUrlContainer = createFabWithLabel(context, rootLayout, "Add Playlist URL", R.drawable.ic_link, isTopItem = false) {
            collapseFab()
            showAddPlaylistDialog(isFile = false)
        }
        rootLayout.addView(fabUrlContainer)

        binding.fabAddPlaylist.setOnClickListener {
            if (isFabExpanded) collapseFab() else expandFab()
        }
        binding.fabAddPlaylist.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFFFFB300.toInt())
        binding.fabAddPlaylist.imageTintList =
            android.content.res.ColorStateList.valueOf(0xFF3E2000.toInt())
    }

    private fun showTvAddPlaylistDialog() {
        val options = arrayOf("Add Playlist URL", "Add Playlist File")
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Playlist")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddPlaylistDialog(isFile = false)
                    1 -> openFilePicker()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun createFabWithLabel(
        context: android.content.Context,
        parent: ConstraintLayout,
        labelText: String,
        iconRes: Int,
        isTopItem: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        val marginBottom = if (isTopItem) (136 * dp).toInt() else (72 * dp).toInt()
        return LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomToBottom = binding.fabAddPlaylist.id
                endToEnd = binding.fabAddPlaylist.id
                bottomMargin = marginBottom
                marginEnd = (16 * dp).toInt()
            }
            alpha = 0f
            scaleX = 0f
            scaleY = 0f
            visibility = View.GONE
            val pillCard = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                cardElevation = 8f
                radius = 999f
                setCardBackgroundColor(0xFF8B6914.toInt())
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((20 * dp).toInt(), (14 * dp).toInt(), (28 * dp).toInt(), (14 * dp).toInt())
                    val icon = android.widget.ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply { marginEnd = (16 * dp).toInt() }
                        setImageResource(iconRes)
                        imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFECB3.toInt())
                    }
                    addView(icon)
                    val label = TextView(context).apply {
                        text = labelText
                        textSize = 16f
                        setTextColor(0xFFFFECB3.toInt())
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    addView(label)
                }
                addView(row)
                setOnClickListener { onClick() }
            }
            addView(pillCard)
        }
    }

    private fun expandFab() {
        isFabExpanded = true
        scrimView?.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(150).start()
        }
        binding.fabAddPlaylist.animate().rotation(45f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        fabFileContainer?.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setStartDelay(0).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
        fabUrlContainer?.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setStartDelay(0).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    private fun collapseFab() {
        isFabExpanded = false
        scrimView?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction { scrimView?.visibility = View.GONE }?.start()
        binding.fabAddPlaylist.animate().rotation(0f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        fabFileContainer?.animate()?.alpha(0f)?.scaleX(0f)?.scaleY(0f)?.setDuration(150)?.withEndAction { fabFileContainer?.visibility = View.GONE }?.start()
        fabUrlContainer?.animate()?.alpha(0f)?.scaleX(0f)?.scaleY(0f)?.setDuration(150)?.withEndAction { fabUrlContainer?.visibility = View.GONE }?.start()
    }

    private fun observeViewModel() {
        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            playlistAdapter.submitList(playlists)
            updateEmptyState(playlists.isEmpty())
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewPlaylists.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-mpegURL", "audio/x-mpegurl", "application/vnd.apple.mpegurl", "*/*"))
        }
        filePickerLauncher.launch(intent)
    }

    private fun suppressKeyboardForTv(dialog: android.app.Dialog, vararg fields: EditText) {
        if (!DeviceUtils.isTvDevice) return
        fields.forEach { field ->
            field.isFocusable = false
            field.isFocusableInTouchMode = false
        }
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        dialog.setOnShowListener {
            imm.hideSoftInputFromWindow(fields.firstOrNull()?.windowToken, 0)
        }
    }

    private fun showAddPlaylistDialog(isFile: Boolean, fileUri: Uri? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_playlist, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.input_title)
        val urlInput = dialogView.findViewById<EditText>(R.id.input_url)
        if (isFile) {
            urlInput.isEnabled = false
            urlInput.setText(fileUri?.toString() ?: "")
        }
        val builtDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Playlist")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val title = titleInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!isFile && url.isEmpty()) {
                    Toast.makeText(requireContext(), "URL is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isFile && fileUri != null) {
                    try {
                        requireContext().contentResolver.takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {}
                    viewModel.addPlaylist(title, "", true, fileUri.toString())
                } else {
                    viewModel.addPlaylist(title, url, false, "")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        suppressKeyboardForTv(builtDialog, titleInput, urlInput)
        builtDialog.show()
        builtDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun showEditPlaylistDialog(playlist: Playlist) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_playlist, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.input_title)
        val urlInput = dialogView.findViewById<EditText>(R.id.input_url)
        titleInput.setText(playlist.title)
        if (playlist.isFile) {
            urlInput.setText(playlist.filePath)
            urlInput.isEnabled = false
        } else {
            urlInput.setText(playlist.url)
        }
        val builtDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update Playlist Details")
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newUrl = urlInput.text.toString().trim()
                if (newTitle.isEmpty()) {
                    Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!playlist.isFile && newUrl.isEmpty()) {
                    Toast.makeText(requireContext(), "URL is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updatedPlaylist = playlist.copy(
                    title = newTitle,
                    url = if (!playlist.isFile) newUrl else playlist.url
                )
                viewModel.updatePlaylist(updatedPlaylist)
                dialog.dismiss()
            }
            .setNeutralButton("Delete") { dialog, _ ->
                dialog.dismiss()
                showDeleteConfirmationDialog(playlist)
            }
            .setNegativeButton("Cancel", null)
            .create()

        suppressKeyboardForTv(builtDialog, titleInput, urlInput)
        builtDialog.show()
        builtDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun showDeleteConfirmationDialog(playlist: Playlist) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"${playlist.title}\"?")
            .setPositiveButton("Delete") { dialog, _ ->
                viewModel.deletePlaylist(playlist)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = _binding?.recyclerViewPlaylists?.layoutManager as? LinearLayoutManager
        layoutManager?.onSaveInstanceState()?.let { outState.putParcelable("rv_playlists_state", it) }
        outState.putBoolean("fab_expanded", isFabExpanded)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_playlists_state")?.let {
            binding.recyclerViewPlaylists.layoutManager?.onRestoreInstanceState(it)
        }
        if (savedInstanceState?.getBoolean("fab_expanded") == true) expandFab()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
