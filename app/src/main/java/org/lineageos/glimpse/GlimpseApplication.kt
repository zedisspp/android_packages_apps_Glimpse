/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.Application
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import org.lineageos.glimpse.ext.applyThemeMode
import org.lineageos.glimpse.ext.applyLanguage
import org.lineageos.glimpse.repository.MediaRepository

class GlimpseApplication : Application() {
    val mediaRepository by lazy { MediaRepository(applicationContext) }

    override fun onCreate() {
        super.onCreate()

        // Apply the user's chosen theme (defaults to following the system)
        PreferenceManager.getDefaultSharedPreferences(this).apply {
            applyLanguage()
            applyThemeMode()
        }

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
