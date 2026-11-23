package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AndroidKeyStore AES-256/GCM vault for user-entered third-party API secrets
 * (NVIDIA NIM, ImageKit, MongoDB). Secrets are never embedded in the APK or committed to Git.
 */
object CryptoVault {
    private const val PREFS_NAME = "aura_encrypted_vault_v1"
    private const val KEY_ALIAS = "aura_master_aes_gcm_v1"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
        return store.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun saveSecret(context: Context, keyName: String, rawValue: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            prefs.edit()
                .remove("${keyName}_iv")
                .remove("${keyName}_ct")
                .apply()
            return
        }
        val secret = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val ok = prefs.edit()
            .putString("${keyName}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("${keyName}_ct", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(ok) { "Could not persist encrypted secret for $keyName" }
    }

    fun readSecret(context: Context, keyName: String): String {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val iv = prefs.getString("${keyName}_iv", null)
            val ct = prefs.getString("${keyName}_ct", null)
            if (iv.isNullOrEmpty() || ct.isNullOrEmpty()) return ""
            val secret = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secret,
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Strictly validates user-supplied external cloud URLs:
     * - Requires HTTPS scheme
     * - Blocks loopback, link-local, and private network literal hostnames/IPs
     */
    fun validatePublicHttpsUrl(rawUrl: String): Result<String> {
        val cleaned = rawUrl.trim().trimEnd('/')
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Endpoint URL cannot be empty."))
        }
        val parsed = cleaned.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid URL format. Must start with https://"))
        if (!parsed.isHttps) {
            return Result.failure(IllegalArgumentException("Only secure https:// endpoints are allowed."))
        }
        val host = parsed.host.lowercase()
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
            return Result.failure(IllegalArgumentException("Local/private hostnames are not permitted."))
        }
        if (isPrivateOrLoopbackLiteral(host)) {
            return Result.failure(IllegalArgumentException("Private or loopback IP addresses are not permitted."))
        }
        return Result.success(cleaned)
    }

    private fun isPrivateOrLoopbackLiteral(host: String): Boolean {
        val cleanHost = host.removePrefix("[").removeSuffix("]")
        val isIpLiteral = cleanHost.all { it.isDigit() || it == '.' } || cleanHost.contains(':')
        if (!isIpLiteral) return false
        return try {
            val addr = InetAddress.getByName(cleanHost)
            addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isAnyLocalAddress ||
                addr.isMulticastAddress
        } catch (_: Exception) {
            true
        }
    }
}
