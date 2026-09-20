/*
 * SPDX-FileCopyrightText: 2026 ZedissP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.glimpse

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.lineageos.glimpse.security.SecureVault
import java.util.concurrent.Executors

class SecureFolderActivity : AppCompatActivity() {
    private lateinit var vault: SecureVault
    private var vaultKey: ByteArray? = null
    private lateinit var list: LinearLayout
    private var pickerOpen = false
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = SecureVault(this)
        buildUi()
        if (vault.isConfigured()) showUnlock() else showSetup()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        val title = TextView(this).apply {
            text = getString(R.string.secure_folder_title)
            textSize = 28f
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this@SecureFolderActivity, com.google.android.material.R.attr.colorOnSurface))
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.secure_folder_description)
            textSize = 15f
            setPadding(0, dp(6), 0, dp(14))
        }
        root.addView(title)
        root.addView(subtitle)

        val add = MaterialButton(this).apply {
            text = getString(R.string.secure_folder_add)
            setOnClickListener { chooseMedia() }
            isAllCaps = false
        }
        root.addView(add)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showSetup() {
        val input = passwordInput()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.secure_folder_create_title)
            .setMessage(R.string.secure_folder_create_message)
            .setView(input.container)
            .setPositiveButton(R.string.secure_folder_create) { _, _ ->
                val value = input.edit.text?.toString()?.toCharArray() ?: CharArray(0)
                if (value.size < 4) {
                    toast(R.string.secure_folder_password_short)
                    showSetup()
                } else {
                    vault.configure(value)
                    value.fill('\u0000')
                    showUnlock()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showUnlock() {
        val input = passwordInput()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.secure_folder_unlock)
            .setMessage(R.string.secure_folder_unlock_message)
            .setView(input.container)
            .setPositiveButton(R.string.secure_folder_unlock_pin) { _, _ ->
                val value = input.edit.text?.toString()?.toCharArray() ?: CharArray(0)
                unlockWithPassword(value)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            if (canUseBiometric()) {
                dialog.setNeutralButton(R.string.secure_folder_fingerprint) { _, _ ->
                    authenticateBiometric()
                }
            }
        }
        dialog.show()
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

    private fun canUseBiometric(): Boolean =
        android.hardware.biometrics.BiometricManager.from(this).canAuthenticate(
            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticateBiometric() {
        val cipher = runCatching { vault.biometricCipher() }.getOrNull()
        if (cipher == null) {
            toast(R.string.secure_folder_fingerprint_unavailable)
            return
        }
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.secure_folder_title))
            .setSubtitle(getString(R.string.secure_folder_fingerprint_subtitle))
            .setDescription(getString(R.string.secure_folder_unlock_message))
            .setNegativeButton(
                getString(R.string.secure_folder_use_password),
                mainExecutor
            ) { _, _ -> showUnlock() }
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
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancellation and user choice simply return to the unlock screen.
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
            },
            REQUEST_PICK
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
        if (requestCode == REQUEST_PICK) {
            pickerOpen = false
        }
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
                        contentResolver.openInputStream(uri)?.let {
                            vault.importMedia(it, name, mime, key)
                        }
                        // Remove the original after successful encryption so
                        // it disappears from MediaStore/Gallery/other apps.
                        runCatching {
                            android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                        }.recoverCatching {
                            contentResolver.delete(uri, null, null)
                        }
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
                    list.addView(TextView(this).apply {
                        text = getString(R.string.secure_folder_empty)
                        textSize = 16f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(40), 0, dp(40))
                    })
                    return@runOnUiThread
                }
                entries.forEach { entry ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(12), 0, dp(12))
                    }
                    val name = TextView(this).apply {
                        text = entry.name
                        textSize = 16f
                        maxLines = 2
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }
                    val open = MaterialButton(this).apply {
                        text = getString(R.string.secure_folder_open)
                        isAllCaps = false
                        setOnClickListener { openEntry(entry) }
                    }
                    val delete = MaterialButton(this).apply {
                        text = getString(R.string.secure_folder_delete)
                        isAllCaps = false
                        setOnClickListener {
                            MaterialAlertDialogBuilder(this@SecureFolderActivity)
                                .setTitle(R.string.secure_folder_delete)
                                .setMessage(entry.name)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    vault.delete(entry); refresh()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                    row.addView(name)
                    row.addView(open)
                    row.addView(delete)
                    list.addView(row)
                }
            }
        }
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
                        setImageURI(Uri.fromFile(temp))
                    }
                    MaterialAlertDialogBuilder(this)
                        .setView(image)
                        .setOnDismissListener { temp.delete() }
                        .show()
                } else {
                    val video = VideoView(this).apply {
                        setVideoURI(Uri.fromFile(temp))
                        setMediaController(MediaController(this@SecureFolderActivity))
                        setOnPreparedListener { it.start() }
                    }
                    MaterialAlertDialogBuilder(this)
                        .setView(video)
                        .setOnDismissListener { video.stopPlayback(); temp.delete() }
                        .show()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Relock when the user really leaves the vault. Keep the session alive
        // while the system document picker is being used for an import.
        if (!pickerOpen) {
            vaultKey?.fill(0)
            vaultKey = null
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun passwordInput(): PasswordInput {
        val layout = TextInputLayout(this).apply {
            hint = getString(R.string.secure_folder_password_hint)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(edit)
        return PasswordInput(layout, edit)
    }

    private fun canUseBiometric(): Boolean {
        return try {
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return "media_${System.currentTimeMillis()}"
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private data class PasswordInput(val container: TextInputLayout, val edit: TextInputEditText)

    companion object {
        private const val REQUEST_PICK = 4101
        private const val REQUEST_DEVICE = 4102
    }
}
