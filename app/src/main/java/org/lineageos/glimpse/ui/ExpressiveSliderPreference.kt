/*
 * SPDX-FileCopyrightText: 2024-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import org.lineageos.glimpse.R

/**
 * Material 3 Expressive-style slider used for numeric settings.
 * Keeps the existing preference key/value while replacing the legacy SeekBar widget.
 */
class ExpressiveSliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : Preference(context, attrs, defStyleAttr) {

    private var slider: Slider? = null
    private var valueView: View? = null
    private val changeListener = Slider.OnChangeListener { _, value, fromUser ->
        if (!fromUser) return@OnChangeListener

        val seconds = value.toInt().coerceIn(MIN_VALUE, MAX_VALUE)
        sharedPreferences?.edit()?.putInt(key, seconds)?.apply()
        updateValueLabel(seconds)
    }

    init {
        layoutResource = R.layout.preference_expressive_slider
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        slider?.removeOnChangeListener(changeListener)

        val newSlider = holder.itemView.findViewById<Slider>(R.id.expressive_slider)
        val newValueView = holder.itemView.findViewById<android.widget.TextView>(R.id.slider_value)

        slider = newSlider
        valueView = newValueView

        val seconds = sharedPreferences?.getInt(key, DEFAULT_VALUE)
            ?.coerceIn(MIN_VALUE, MAX_VALUE)
            ?: DEFAULT_VALUE

        newSlider.value = seconds.toFloat()
        newSlider.isEnabled = isEnabled
        newValueView.isEnabled = isEnabled
        newSlider.addOnChangeListener(changeListener)
        updateValueLabel(seconds)
    }

    override fun onDetached() {
        slider?.removeOnChangeListener(changeListener)
        slider = null
        valueView = null
        super.onDetached()
    }

    private fun updateValueLabel(seconds: Int) {
        (valueView as? android.widget.TextView)?.text = context.getString(
            R.string.double_tap_to_seek_seconds_value,
            seconds,
        )
    }

    companion object {
        private const val MIN_VALUE = 5
        private const val MAX_VALUE = 30
        private const val DEFAULT_VALUE = 10
    }
}
