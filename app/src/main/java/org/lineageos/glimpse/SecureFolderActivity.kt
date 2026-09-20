/*
 * SPDX-FileCopyrightText: 2026 ZedissP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.glimpse

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.lineageos.glimpse.security.SecureVault
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import java.util.concurrent.Executors

class SecureFolderActivity : AppCompatActivity() {
    private lateinit var vault: SecureVault
    private var vaultKey: ByteArray? = null
    private lateinit var list: LinearLayout
    private var pickerOpen = false
    private val executor = Executors.newSingleThreadExecutor()

    private val onSurface get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface)
    private val onSurfaceVariant get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val surfaceContainer get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorSurfaceContainer)
    private val surfaceContainerHigh get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorSurfaceContainerHigh)
    private val primary get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimary)
    private val primaryContainer get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimaryContainer)
    private val onPrimaryContainer get() = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnPrimaryContainer)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = SecureVault(this)
        buildUi()
        if (vault.isConfigured()) showUnlock() else showSetup()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(20))
            setBackgroundColor(MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorSurface))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20), dp(8) + bars.top, dp(20), dp(20) + bars.bottom)
            insets
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = null
            contentDescription = getString(android.R.string.cancel)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setColorFilter(onSurface)
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))

        val toolbarText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbarText.addView(TextView(this).apply {
            text = getString(R.string.secure_folder_title)
            textSize = 24f
            setTextColor(onSurface)
        })
        toolbarText.addView(TextView(this).apply {
            text = getString(R.string.secure_folder_library_summary)
            textSize = 13f
            setTextColor(onSurfaceVariant)
        })
        toolbar.addView(toolbarText, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)

        val header = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            setCardBackgroundColor(primaryContainer)
            strokeWidth = 0
            cardElevation = 0f
            contentPadding = dp(18)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lock = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock_vault)
            setColorFilter(onPrimaryContainer)
            contentDescription = null
        }
        headerRow.addView(lock, LinearLayout.LayoutParams(dp(48), dp(48)))
        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        headerText.addView(TextView(this).apply {
            text = getString(R.string.secure_folder_description)
            textSize = 14f
            setTextColor(onPrimaryContainer)
        })
        headerText.addView(TextView(this).apply {
            text = getString(R.string.secure_folder_private_hint)
            textSize = 12f
            setTextColor(onPrimaryContainer)
            alpha = 0.78f
            setPadding(0, dp(5), 0, 0)
        })
        headerRow.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(headerRow)
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val add = MaterialButton(this).apply {
            text = getString(R.string.secure_folder_add)
            isAllCaps = false
            icon = ContextCompat.getDrawable(this@SecureFolderActivity, R.drawable.ic_photo_size_select_actual)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = android.content.res.ColorStateList.valueOf(onPrimaryContainer)
            setTextColor(onPrimaryContainer)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryContainer)
            cornerRadius = dp(24)
            minHeight = dp(52)
            setOnClickListener { chooseMedia() }
        }
        root.addView(add, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(list, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showSetup() {
        val content = dialogContent(
            title = getString(R.string.secure_folder_create_title),
            message = getString(R.string.secure_folder_create_message),
            showFingerprint = false
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.secure_folder_create_title)
            .setView(content.container)
            .setCancelable(false)
            .create()
        content.primary.setText(R.string.secure_folder_create)
        content.primary.setOnClickListener {
            val value = content.edit.text?.toString()?.toCharArray() ?: CharArray(0)
            if (value.size < 4) {
                content.layout.error = getString(R.string.secure_folder_password_short)
                value.fill('\u0000')
                return@setOnClickListener
            }
            vault.configure(value)
            value.fill('\u0000')
            dialog.dismiss()
            showUnlock()
        }
        content.cancel.setOnClickListener { dialog.dismiss(); finish() }
        dialog.show()
    }

    private fun showUnlock() {
        val content = dialogContent(
            title = getString(R.string.secure_folder_unlock),
            message = getString(R.string.secure_folder_unlock_message),
            showFingerprint = canUseBiometric()
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.secure_folder_unlock)
            .setView(content.container)
            .setCancelable(false)
            .create()
        content.primary.setText(R.string.secure_folder_unlock_pin)
        content.primary.setOnClickListener {
            val value = content.edit.text?.toString()?.toCharArray() ?: CharArray(0)
            if (value.isEmpty()) {
                content.layout.error = getString(R.string.secure_folder_password_short)
                value.fill('\u0000')
                return@setOnClickListener
            }
            dialog.dismiss()
            unlockWithPassword(value)
        }
        content.fingerprint?.setOnClickListener {
            dialog.dismiss()
            authenticateBiometric()
        }
        content.cancel.setOnClickListener { dialog.dismiss(); finish() }
        dialog.show()
    }

    private fun dialogContent(title: String, message: String, showFingerprint: Boolean): DialogContent {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(8))
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock_vault)
            setColorFilter(primary)
            scaleType = ImageView.ScaleType.CENTER
        }
        container.addView(icon, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(4) })
        container.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(onSurfaceVariant)
            setPadding(0, 0, 0, dp(14))
        })

        val layout = TextInputLayout(this).apply {
            hint = getString(R.string.secure_folder_password_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            singleLine = true
        }
        layout.addView(edit)
        container.addView(layout)

        val fingerprint = if (showFingerprint) {
            MaterialButton(this).apply {
                text = getString(R.string.secure_folder_fingerprint)
                isAllCaps = false
                icon = ContextCompat.getDrawable(this@SecureFolderActivity, android.R.drawable.ic_lock_idle_lock)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                cornerRadius = dp(20)
                minHeight = dp(48)
                setTextColor(onPrimaryContainer)
                backgroundTintList = android.content.res.ColorStateList.valueOf(primaryContainer)
            }.also { button ->
                container.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
            }
        } else null

        val primaryButton = MaterialButton(this).apply {
            isAllCaps = false
            cornerRadius = dp(20)
            minHeight = dp(48)
            setTextColor(primary)
            backgroundTintList = android.content.res.ColorStateList.valueOf(surfaceContainerHigh)
        }
        container.addView(primaryButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = if (fingerprint == null) dp(14) else dp(8) })

        val cancel = MaterialButton(this).apply {
            text = getString(android.R.string.cancel)
            isAllCaps = false
            minHeight = dp(44)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(onSurfaceVariant)
        }
        container.addView(cancel, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(2) })
        return DialogContent(container, layout, edit, primaryButton, fingerprint, cancel)
    }

    private fun unlockWithPassword(value: CharArray) {
        executor.execute {
            val key = runCatching { vault.unlock(value) }.getOrNull()
            value.fill('\u0000')
            runOnUiThread {
                if (key == null) {
                    toast(R.string.secure_folder_wrong_password)
                    showUnlock()
                } else {
                    vaultKey = key
                    refresh()
                }
            }
        }
    }

    private fun authenticateBiometric() {
        val cipher = runCatching { vault.biometricCipher() }.getOrNull()
        if (cipher == null) {
            toast(R.string.secure_folder_fingerprint_unavailable)
            showUnlock()
            return
        }
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.secure_folder_title))
            .setSubtitle(getString(R.string.secure_folder_fingerprint_subtitle))
            .setDescription(getString(R.string.secure_folder_unlock_message))
            .setNegativeButton(getString(R.string.secure_folder_use_password), mainExecutor) { _, _ -> showUnlock() }
            .build()
        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = runCatching {
                        vault.unwrapBiometric(result.cryptoObject?.cipher ?: cipher)
                    }.getOrNull()
                    if (unlocked != null) {
                        vaultKey = unlocked
                        refresh()
                    } else {
                        toast(R.string.secure_folder_wrong_password)
                        showUnlock()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                        showUnlock()
                    }
                }
            }
        )
    }

    private fun chooseMedia() {
        if (vaultKey == null) return
        pickerOpen = true
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }, REQUEST_PICK
        )
    }

    @Deprecated("Android compatibility callback")
    override fun onResume() {
        super.onResume()
        if (vaultKey != null) refresh()
    }

    @Deprecated("Android compatibility callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK) pickerOpen = false
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && data != null) {
            val uris = buildList {
                data.data?.let(::add)
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
            }.distinct()
            val key = vaultKey ?: return
            executor.execute {
                uris.forEach { uri ->
                    runCatching {
                        val name = queryName(uri)
                        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                        contentResolver.openInputStream(uri)?.use { vault.importMedia(it, name, mime, key) }
                        runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }
                            .recoverCatching { contentResolver.delete(uri, null, null) }
                    }
                }
                runOnUiThread { refresh() }
            }
        }
    }

    private fun refresh() {
        list.removeAllViews()
        val key = vaultKey ?: return
        executor.execute {
            val entries = vault.entries(key)
            runOnUiThread {
                if (entries.isEmpty()) {
                    list.addView(emptyState())
                    return@runOnUiThread
                }
                val count = TextView(this).apply {
                    text = resources.getQuantityString(R.plurals.secure_folder_item_count, entries.size, entries.size)
                    textSize = 13f
                    setTextColor(onSurfaceVariant)
                    setPadding(dp(4), dp(8), dp(4), dp(6))
                }
                list.addView(count)
                entries.forEach { entry -> list.addView(mediaCard(entry)) }
            }
        }
    }

    private fun emptyState(): View {
        val card = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            setCardBackgroundColor(surfaceContainer)
            cardElevation = 0f
            strokeWidth = 0
            contentPadding = dp(24)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_lock_vault)
            setColorFilter(primary)
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        content.addView(TextView(this).apply {
            text = getString(R.string.secure_folder_empty)
            textSize = 16f
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.secure_folder_empty_hint)
            textSize = 13f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
        })
        card.addView(content)
        return card.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) } }
    }

    private fun mediaCard(entry: SecureVault.Entry): View {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            setCardBackgroundColor(surfaceContainer)
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOutlineVariant)
            contentPadding = dp(14)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconBox = FrameLayout(this).apply {
            background = roundedBackground(primaryContainer, dp(18).toFloat())
            addView(ImageView(this@SecureFolderActivity).apply {
                setImageResource(if (entry.mimeType.startsWith("video/")) R.drawable.ic_motion_play else R.drawable.ic_photo_size_select_actual)
                setColorFilter(onPrimaryContainer)
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }, FrameLayout.LayoutParams(dp(48), dp(48)))
        }
        top.addView(iconBox, LinearLayout.LayoutParams(dp(48), dp(48)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(TextView(this).apply {
            text = entry.name
            textSize = 15f
            setTextColor(onSurface)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        info.addView(TextView(this).apply {
            text = if (entry.mimeType.startsWith("video/")) getString(R.string.secure_folder_video) else getString(R.string.secure_folder_photo)
            textSize = 12f
            setTextColor(onSurfaceVariant)
            setPadding(0, dp(3), 0, 0)
        })
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(top)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val open = compactButton(R.drawable.ic_photo_size_select_actual, R.string.secure_folder_open) { openEntry(entry) }
        val delete = compactButton(R.drawable.ic_delete, R.string.secure_folder_delete) {
            MaterialAlertDialogBuilder(this@SecureFolderActivity)
                .setTitle(R.string.secure_folder_delete)
                .setMessage(entry.name)
                .setPositiveButton(android.R.string.ok) { _, _ -> vault.delete(entry); refresh() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        actions.addView(open, LinearLayout.LayoutParams(0, dp(44), 1f).apply { topMargin = dp(12); rightMargin = dp(5) })
        actions.addView(delete, LinearLayout.LayoutParams(0, dp(44), 1f).apply { topMargin = dp(12); leftMargin = dp(5) })
        content.addView(actions)
        card.addView(content)
        return card.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
    }

    private fun compactButton(iconRes: Int, textRes: Int, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
        text = getString(textRes)
        isAllCaps = false
        icon = ContextCompat.getDrawable(this@SecureFolderActivity, iconRes)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconSize = dp(18)
        cornerRadius = dp(18)
        minHeight = dp(44)
        setTextColor(primary)
        backgroundTintList = android.content.res.ColorStateList.valueOf(surfaceContainerHigh)
        setOnClickListener { action() }
    }

    private fun openEntry(entry: SecureVault.Entry) {
        val key = vaultKey ?: return
        executor.execute {
            val temp = runCatching { vault.decryptToCache(entry, key) }.getOrNull()
            runOnUiThread {
                if (temp == null) {
                    toast(R.string.secure_folder_open_error)
                    return@runOnUiThread
                }
                if (entry.mimeType.startsWith("image/")) {
                    val image = ImageView(this).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageURI(Uri.fromFile(temp))
                    }
                    val maxHeight = (resources.displayMetrics.heightPixels * 0.62f).toInt()
                    image.layoutParams = ViewGroup.LayoutParams(-1, maxHeight)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(entry.name)
                        .setView(image, dp(12), dp(4), dp(12), dp(8))
                        .setOnDismissListener { temp.delete() }
                        .show()
                } else {
                    val video = VideoView(this).apply {
                        setVideoURI(Uri.fromFile(temp))
                        setMediaController(MediaController(this@SecureFolderActivity))
                        setOnPreparedListener { it.start() }
                    }
                    MaterialAlertDialogBuilder(this)
                        .setTitle(entry.name)
                        .setView(video, dp(12), dp(4), dp(12), dp(8))
                        .setOnDismissListener { video.stopPlayback(); temp.delete() }
                        .show()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!pickerOpen) {
            vaultKey?.fill(0)
            vaultKey = null
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return "media_${System.currentTimeMillis()}"
    }

    private fun canUseBiometric(): Boolean = try {
        val manager = getSystemService(BIOMETRIC_SERVICE) as? android.hardware.biometrics.BiometricManager ?: return false
        manager.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Throwable) { false }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private data class DialogContent(
        val container: LinearLayout,
        val layout: TextInputLayout,
        val edit: TextInputEditText,
        val primary: MaterialButton,
        val fingerprint: MaterialButton?,
        val cancel: MaterialButton
    )

    companion object {
        private const val REQUEST_PICK = 4101
    }
}
