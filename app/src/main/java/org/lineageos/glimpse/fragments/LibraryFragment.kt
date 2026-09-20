/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.MediaType

/**
 * A fragment showing a search bar with categories.
 */
class LibraryFragment : Fragment(R.layout.fragment_library) {
    // Views
    private val favoritesAlbumListItem by getViewProperty<MaterialCardView>(R.id.favoritesAlbumListItem)
    private val photosAlbumListItem by getViewProperty<MaterialCardView>(R.id.photosAlbumListItem)
    private val libraryNestedScrollView by getViewProperty<NestedScrollView>(R.id.libraryNestedScrollView)
    private val trashAlbumListItem by getViewProperty<MaterialCardView>(R.id.trashAlbumListItem)
    private val videosAlbumListItem by getViewProperty<MaterialCardView>(R.id.videosAlbumListItem)
    private val secureFolderListItem by getViewProperty<MaterialCardView>(R.id.secureFolderListItem)
    private val searchEditText by getViewProperty<TextInputEditText>(R.id.searchEditText)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            libraryNestedScrollView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            // The list runs edge-to-edge behind the translucent floating
            // dock, so reserve space for it and let content draw into the
            // padding area instead of clipping it away.
            libraryNestedScrollView.clipToPadding = false
            libraryNestedScrollView.updatePadding(
                bottom = insets.bottom + resources.getDimensionPixelSize(
                    R.dimen.glimpse_dock_content_reserved_space
                )
            )

            windowInsets
        }

        searchEditText.setOnEditorActionListener { _, actionId, event ->
            val submitted = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (submitted) {
                openSearch(searchEditText.text?.toString().orEmpty())
            }
            submitted
        }

        photosAlbumListItem.setOnClickListener {
            openAlbum(AlbumType.REELS, MediaType.IMAGE)
        }

        videosAlbumListItem.setOnClickListener {
            openAlbum(AlbumType.REELS, MediaType.VIDEO)
        }

        favoritesAlbumListItem.setOnClickListener {
            openAlbum(AlbumType.FAVORITES)
        }

        trashAlbumListItem.setOnClickListener {
            openAlbum(AlbumType.TRASH)
        }

        secureFolderListItem.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), org.lineageos.glimpse.SecureFolderActivity::class.java))
        }
    }

    private fun openSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return

        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        searchEditText.clearFocus()

        findNavController().navigate(
            R.id.action_mainFragment_to_fragment_album,
            AlbumFragment.createBundle(
                albumType = AlbumType.REELS,
                searchQuery = normalized,
            )
        )
    }

    private fun openAlbum(
        albumType: AlbumType,
        fileType: MediaType? = null,
    ) {
        findNavController().navigate(
            R.id.action_mainFragment_to_fragment_album,
            AlbumFragment.createBundle(
                albumType = albumType,
                fileType = fileType,
            )
        )
    }
}
