/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.lineageos.glimpse.ui.BackdropBlurBottomNavigationView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.SettingsActivity
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.AlbumType

class MainFragment : Fragment(R.layout.fragment_main) {
    // Views
    private val navigationBarView by getViewProperty<BackdropBlurBottomNavigationView>(R.id.navigationBarView)
    private val settingsMaterialButton by getViewProperty<MaterialButton>(R.id.settingsMaterialButton)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)
    private val viewPager2 by getViewProperty<ViewPager2>(R.id.viewPager2)

    private val onPageChangeCallback by lazy {
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                navigationBarView.menu.getItem(position).isChecked = true
                navigationBarView.refreshBackdrop()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep the floating dock clear of the system and gesture navigation bars
        val dockMarginHorizontal =
            resources.getDimensionPixelSize(R.dimen.glimpse_dock_margin_horizontal)
        val dockMarginBottom = resources.getDimensionPixelSize(R.dimen.glimpse_dock_margin_bottom)
        ViewCompat.setOnApplyWindowInsetsListener(navigationBarView) { dock, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            dock.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = dockMarginHorizontal + insets.left
                bottomMargin = dockMarginBottom + insets.bottom
            }

            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(settingsMaterialButton) { button, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = dockMarginHorizontal + insets.right
                bottomMargin = dockMarginBottom + insets.bottom
            }

            windowInsets
        }

        // Render a small, downsampled backdrop behind the dock. The snapshot
        // is refreshed after page changes and when scrolling settles, rather
        // than every frame, to keep the blur inexpensive.
        navigationBarView.setBackdropSource(viewPager2)

        // Toolbar
        toolbar.setupWithNavController(findNavController())

        settingsMaterialButton.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            requireActivity().overridePendingTransition(R.anim.glimpse_enter, R.anim.glimpse_exit)
        }

        // ViewPager2
        viewPager2.isUserInputEnabled = false
        viewPager2.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]()
        }
        viewPager2.offscreenPageLimit = fragments.size
        // Do not apply a page transformer here. Translating/scaling pages in
        // ViewPager2 can expose the neighboring fragment at the screen edge.
        // Navigation remains instantaneous and the destination transitions are
        // handled by Navigation Component where appropriate.
        viewPager2.setPageTransformer(null)
        viewPager2.registerOnPageChangeCallback(onPageChangeCallback)

        // Keep the backdrop live while the gallery moves. A scroll-listener with
        // a long delayed refresh makes the blur look like a slideshow. Instead,
        // sample at most once every ~33 ms (about 30 fps), only while the root is
        // actually drawing. This keeps the backdrop visually fluid without
        // forcing a full-resolution screenshot every frame.
        val frameRefresh = object : android.view.ViewTreeObserver.OnPreDrawListener {
            private var lastRefreshNanos = 0L

            override fun onPreDraw(): Boolean {
                val now = System.nanoTime()
                if (now - lastRefreshNanos >= 33_000_000L) {
                    lastRefreshNanos = now
                    navigationBarView.refreshBackdrop()
                }
                return true
            }
        }
        view.viewTreeObserver.addOnPreDrawListener(frameRefresh)
        view.setTag(R.id.glimpse_scroll_refresh_listener, frameRefresh)

        navigationBarView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.reelsFragment -> {
                    viewPager2.currentItem = 0
                    true
                }

                R.id.albumsFragment -> {
                    viewPager2.currentItem = 1
                    false
                }

                R.id.libraryFragment -> {
                    viewPager2.currentItem = 2
                    true
                }

                else -> false
            }
        }
    }

    override fun onDestroyView() {
        view?.let { root ->
            (root.getTag(R.id.glimpse_scroll_refresh_listener) as? android.view.ViewTreeObserver.OnPreDrawListener)?.let { listener ->
                root.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }

        // ViewPager2
        viewPager2.unregisterOnPageChangeCallback(onPageChangeCallback)
        viewPager2.adapter = null

        super.onDestroyView()
    }

    companion object {
        // Keep in sync with the NavigationBarView menu
        private val fragments = arrayOf(
            {
                AlbumFragment().apply {
                    arguments = AlbumFragment.createBundle(
                        albumType = AlbumType.REELS,
                        hideToolbar = true,
                    )
                }
            },
            {
                AlbumsFragment().apply {
                    arguments = AlbumsFragment.createBundle(reserveDockSpace = true)
                }
            },
            { LibraryFragment() },
        )
    }
}
