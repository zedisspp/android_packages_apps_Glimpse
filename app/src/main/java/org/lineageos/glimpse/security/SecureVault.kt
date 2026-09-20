/*
 * SPDX-FileCopyrightText: 2026 ZedissP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.glimpse.security

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore

/**
 * Private, encrypted media vault.
 *
 * Vault files live only in the app's internal storage and are encrypted with
 * AES-256-GCM. A random vault key is wrapped by a PIN/password-derived key.
 * This means MediaStore, Gallery providers and other apps cannot index the
 * protected originals.
 */
class SecureVault(private val context: Context) {
    private val root = File(context.filesDir, "secure_vault").apply { mkdirs() }
    private val filesDir = File(root, "media").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("secure_vault", Context.MODE_PRIVATE)

    fun isConfigured() = prefs.contains(KEY_SALT) && prefs.contains(KEY_WRAPPED)

    fun configure(password: CharArray) {
        require(password.size >= 4) { "Password/PIN must contain at least 4 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val vaultKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappingKey = deriveKey(password, salt)
        val wrapped = encryptRaw(vaultKey, wrappingKey)
        val biometricWrapped = runCatching { wrapForBiometric(vaultKey) }.getOrNull()
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .apply {
                if (biometricWrapped != null) {
                    putString(KEY_BIOMETRIC_WRAPPED, Base64.encodeToString(biometricWrapped, Base64.NO_WRAP))
                }
            }
            .apply()
        // Keep the raw vault key out of persistent storage.
        vaultKey.fill(0)
        wrappingKey.fill(0)
    }

    fun unlock(password: CharArray): ByteArray {
        val salt = Base64.decode(prefs.getString(KEY_SALT, null), Base64.NO_WRAP)
        val wrapped = Base64.decode(prefs.getString(KEY_WRAPPED, null), Base64.NO_WRAP)
        val key = deriveKey(password, salt)
        return try {
            decryptRaw(wrapped, key)
        } finally {
            key.fill(0)
        }
    }

    fun biometricCipher(): Cipher? {
        val wrapped = prefs.getString(KEY_BIOMETRIC_WRAPPED, null) ?: return null
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(KEY_BIOMETRIC_ALIAS, null) ?: return null
        val bytes = Base64.decode(wrapped, Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, bytes, 0, 12)
            )
        }
    }

    fun unwrapBiometric(cipher: Cipher): ByteArray {
        val wrapped = Base64.decode(
            prefs.getString(KEY_BIOMETRIC_WRAPPED, null),
            Base64.NO_WRAP
        )
        return cipher.doFinal(wrapped, 12, wrapped.size - 12)
    }

    private fun wrapForBiometric(vaultKey: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_BIOMETRIC_ALIAS)) {
            val generator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_BIOMETRIC_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKey()
        }
        val key = keyStore.getKey(KEY_BIOMETRIC_ALIAS, null) as SecretKey
        return encryptRaw(vaultKey, key)
    }

    fun importMedia(input: InputStream, displayName: String, mimeType: String, vaultKey: ByteArray) {
        val id = java.util.UUID.randomUUID().toString()
        val target = File(filesDir, "$id.bin")
        val payload = java.io.ByteArrayOutputStream()
        payload.write(HEADER.toByteArray(Charsets.UTF_8))
        val name = displayName.replace("\u0000", "").take(240)
        val mime = mimeType.take(120)
        val metadata = "$name\n$mime\n".toByteArray(Charsets.UTF_8)
        val lengthBytes = java.nio.ByteBuffer.allocate(4).putInt(metadata.size).array()
        payload.write(lengthBytes)
        payload.write(metadata)
        input.use { it.copyTo(payload) }
        val encrypted = encryptRaw(payload.toByteArray(), vaultKey)
        target.writeBytes(encrypted)
    }

    fun entries(vaultKey: ByteArray): List<Entry> {
        return filesDir.listFiles { f -> f.isFile && f.extension == "bin" }
            ?.mapNotNull { file ->
                runCatching {
                    val plain = decryptRaw(file.readBytes(), vaultKey)
                    val bb = java.nio.ByteBuffer.wrap(plain)
                    val header = ByteArray(HEADER.length)
                    bb.get(header)
                    if (String(header, Charsets.UTF_8) != HEADER) error("bad header")
                    val size = bb.int
                    val meta = ByteArray(size).also { bb.get(it) }.toString(Charsets.UTF_8)
                    val lines = meta.split('\n')
                    Entry(file.name, lines.getOrElse(0) { "Media" }, lines.getOrElse(1) { "application/octet-stream" })
                }.getOrNull()
            }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun decryptToCache(entry: Entry, vaultKey: ByteArray): File {
        val plain = decryptRaw(File(filesDir, entry.id).readBytes(), vaultKey)
        val bb = java.nio.ByteBuffer.wrap(plain)
        val header = ByteArray(HEADER.length)
        bb.get(header)
        val size = bb.int
        bb.position(bb.position() + size)
        val out = File(context.cacheDir, "vault_${entry.id.removeSuffix(".bin")}")
        out.outputStream().use { output -> output.write(plain, bb.position(), plain.size - bb.position()) }
        return out
    }

    fun delete(entry: Entry) {
        File(filesDir, entry.id).delete()
    }

    data class Entry(val id: String, val name: String, val mimeType: String)

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encryptRaw(plain: ByteArray, keyBytes: ByteArray): ByteArray =
        encryptRaw(plain, SecretKeySpec(keyBytes, "AES"))

    private fun encryptRaw(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decryptRaw(data: ByteArray, keyBytes: ByteArray): ByteArray {
        require(data.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, data, 0, 12)
        )
        return cipher.doFinal(data, 12, data.size - 12)
    }

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_WRAPPED = "wrapped"
        private const val KEY_BIOMETRIC_WRAPPED = "biometric_wrapped"
        private const val KEY_BIOMETRIC_ALIAS = "glimpse_secure_vault_biometric"
        private const val HEADER = "GLIMPSE_VAULT_1\n"
    }
}
