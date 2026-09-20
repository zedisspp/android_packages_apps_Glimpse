/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
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
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.MaterialShapeDrawable
import org.lineageos.glimpse.R
import org.lineageos.glimpse.SettingsActivity
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.AlbumType

class MainFragment : Fragment(R.layout.fragment_main) {
    // Views
    private val dockContainer by getViewProperty<View>(R.id.dockContainer)
    private val navigationBarView by getViewProperty<NavigationBarView>(R.id.navigationBarView)
    private val settingsMaterialButton by getViewProperty<MaterialButton>(R.id.settingsMaterialButton)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)
    private val viewPager2 by getViewProperty<ViewPager2>(R.id.viewPager2)

    private val onPageChangeCallback by lazy {
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                navigationBarView.menu.getItem(position).isChecked = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep the floating dock clear of the system and gesture navigation bars
        val dockMarginHorizontal =
            resources.getDimensionPixelSize(R.dimen.glimpse_dock_margin_horizontal)
        val dockMarginBottom = resources.getDimensionPixelSize(R.dimen.glimpse_dock_margin_bottom)
        ViewCompat.setOnApplyWindowInsetsListener(dockContainer) { dock, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            dock.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = dockMarginHorizontal + insets.left
                rightMargin = dockMarginHorizontal + insets.right
                bottomMargin = dockMarginBottom + insets.bottom
            }

            windowInsets
        }

        // Let the dock's pill be translucent so content scrolling underneath
        // (now edge-to-edge) shows through it, instead of a solid block
        (navigationBarView.background as? MaterialShapeDrawable)?.let { dockBackground ->
            val surfaceColor = MaterialColors.getColor(
                navigationBarView,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
            )
            dockBackground.fillColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(surfaceColor, DOCK_BACKGROUND_ALPHA)
            )
        }

        // Toolbar
        toolbar.setupWithNavController(findNavController())

        settingsMaterialButton.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

        // ViewPager2
        viewPager2.isUserInputEnabled = false
        viewPager2.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]()
        }
        viewPager2.offscreenPageLimit = fragments.size
        viewPager2.registerOnPageChangeCallback(onPageChangeCallback)

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
        // ViewPager2
        viewPager2.unregisterOnPageChangeCallback(onPageChangeCallback)
        viewPager2.adapter = null

        super.onDestroyView()
    }

    companion object {
        // ~80% opacity, translucent enough to reveal content scrolling
        // underneath the dock without hurting icon/label legibility
        private const val DOCK_BACKGROUND_ALPHA = 204 // 255 * 0.8

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
