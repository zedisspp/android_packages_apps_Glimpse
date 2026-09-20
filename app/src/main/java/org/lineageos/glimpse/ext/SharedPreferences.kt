/*
 * SPDX-FileCopyrightText: 2023-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import java.security.MessageDigest

// App theme (light, dark or follow the system's setting)
private const val THEME_MODE_KEY = "theme_mode"
private const val THEME_MODE_LIGHT = "light"
private const val THEME_MODE_DARK = "dark"
private const val THEME_MODE_SYSTEM = "system"

// System is the default: a fresh install behaves exactly like the platform theme
private const val THEME_MODE_DEFAULT = THEME_MODE_SYSTEM

var SharedPreferences.themeMode: String
    get() = getString(THEME_MODE_KEY, THEME_MODE_DEFAULT) ?: THEME_MODE_DEFAULT
    set(value) = edit {
        putString(THEME_MODE_KEY, value)
    }

/**
 * Maps the stored [themeMode] value to the corresponding [AppCompatDelegate] night mode
 * constant and applies it. Safe to call before any [android.app.Activity] is created, e.g.
 * from [android.app.Application.onCreate].
 */
fun SharedPreferences.applyThemeMode() {
    AppCompatDelegate.setDefaultNightMode(
        when (themeMode) {
            THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}

// All files access dialog dismissed
private const val MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_KEY =
    "manage_media_permission_dialog_dismissed"
private const val MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_DEFAULT = false
var SharedPreferences.manageMediaPermissionDialogDismissed: Boolean
    get() = getBoolean(
        MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_KEY,
        MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_DEFAULT
    )
    set(value) = edit {
        putBoolean(MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_KEY, value)
    }

// Double-tap to seek video
private const val DOUBLE_TAP_SEEK_ENABLED_KEY = "double_tap_to_seek_enabled"
private const val DOUBLE_TAP_SEEK_ENABLED_DEFAULT = false
var SharedPreferences.doubleTapToSeekEnabled: Boolean
    get() = getBoolean(
        DOUBLE_TAP_SEEK_ENABLED_KEY,
        DOUBLE_TAP_SEEK_ENABLED_DEFAULT
    )
    set(value) = edit {
        putBoolean(DOUBLE_TAP_SEEK_ENABLED_KEY, value)
    }

// Double-tap seek time in seconds (5, 10, 15, 30)
private const val DOUBLE_TAP_SEEK_TIME_KEY = "double_tap_to_seek_seconds"
private const val DOUBLE_TAP_SEEK_TIME_DEFAULT = 10
var SharedPreferences.doubleTapToSeekSeconds: Int
    get() = getInt(DOUBLE_TAP_SEEK_TIME_KEY, DOUBLE_TAP_SEEK_TIME_DEFAULT)
    set(value) = edit {
        putInt(DOUBLE_TAP_SEEK_TIME_KEY, value)
    }

// Hide native video seek buttons
private const val HIDE_NATIVE_SEEK_BUTTONS_KEY = "hide_native_seek_buttons"
private const val HIDE_NATIVE_SEEK_BUTTONS_DEFAULT = false
var SharedPreferences.hideNativeSeekButtons: Boolean
    get() = getBoolean(
        HIDE_NATIVE_SEEK_BUTTONS_KEY,
        HIDE_NATIVE_SEEK_BUTTONS_DEFAULT
    )
    set(value) = edit {
        putBoolean(HIDE_NATIVE_SEEK_BUTTONS_KEY, value)
    }

// Edge tap navigation (tap on screen edges to navigate media)
private const val EDGE_TAP_NAVIGATION_ENABLED_KEY = "edge_tap_navigation_enabled"
private const val EDGE_TAP_NAVIGATION_ENABLED_DEFAULT = false
var SharedPreferences.edgeTapNavigationEnabled: Boolean
    get() = getBoolean(
        EDGE_TAP_NAVIGATION_ENABLED_KEY,
        EDGE_TAP_NAVIGATION_ENABLED_DEFAULT
    )
    set(value) = edit {
        putBoolean(EDGE_TAP_NAVIGATION_ENABLED_KEY, value)
    }

// Remember last playback position for videos
private const val REMEMBER_VIDEO_PLAYBACK_POSITION_ENABLED_KEY =
    "remember_video_playback_position_enabled"
private const val REMEMBER_VIDEO_PLAYBACK_POSITION_ENABLED_DEFAULT = false
private const val VIDEO_PLAYBACK_POSITION_PREFIX = "video_playback_position_"
var SharedPreferences.rememberVideoPlaybackPositionEnabled: Boolean
    get() = getBoolean(
        REMEMBER_VIDEO_PLAYBACK_POSITION_ENABLED_KEY,
        REMEMBER_VIDEO_PLAYBACK_POSITION_ENABLED_DEFAULT
    )
    set(value) = edit {
        putBoolean(REMEMBER_VIDEO_PLAYBACK_POSITION_ENABLED_KEY, value)
    }

fun SharedPreferences.getVideoPlaybackPosition(uri: Uri): Long =
    getLong(videoPlaybackPositionKey(uri), 0L)

fun SharedPreferences.setVideoPlaybackPosition(uri: Uri, positionMs: Long) = edit {
    putLong(videoPlaybackPositionKey(uri), positionMs)
}

fun SharedPreferences.removeVideoPlaybackPosition(uri: Uri) = edit {
    remove(videoPlaybackPositionKey(uri))
}

private fun videoPlaybackPositionKey(uri: Uri): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
    val hash = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "$VIDEO_PLAYBACK_POSITION_PREFIX$hash"
}
