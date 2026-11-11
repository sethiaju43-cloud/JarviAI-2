package com.jarvis.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys aur nijji data ko Android ke tijori (Keystore) wale taale mein rakhta hai.
 * Gemini key purane tareeke se (getKey/setKey), baaki secrets getSecret/setSecret se.
 * seal/open: lambe data (chat, memory) ko encrypt karne ke liye.
 */
object ApiKeyStore {

    private const val ALIAS = "jarvis_api_key"
    private const val SECURE_PREFS = "jarvis_secure"
    private const val SECURE_NAME = "k"
    private const val OLD_PREFS = "jarvis"
    private const val OLD_NAME = "api_key"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(plain: String): String? {
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, secretKey())
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun decrypt(stored: String): String {
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) {
                ""
            } else {
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val ct = Base64.decode(parts[1], Base64.NO_WRAP)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
                String(c.doFinal(ct), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun securePrefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

    private fun oldPrefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)

    private fun secureRead(ctx: Context): String {
        val s = securePrefs(ctx).getString(SECURE_NAME, "") ?: ""
        return if (s.isBlank()) "" else decrypt(s)
    }

    private fun oldRead(ctx: Context): String {
        return (oldPrefs(ctx).getString(OLD_NAME, "") ?: "").trim()
    }

    /** Key lautao. Purani khuli key mile to usse taale mein bhi rakh lo. */
    @Synchronized
    fun getKey(ctx: Context): String {
        val old = oldRead(ctx)
        val secure = secureRead(ctx)
        if (old.isNotEmpty()) {
            if (old != secure) {
                val enc = encrypt(old)
                if (enc != null) securePrefs(ctx).edit().putString(SECURE_NAME, enc).apply()
            }
            // Taale mein sahi copy ban gayi ho to purani khuli copy hata do (na bane to rehne do, app chalti rahe)
            removeOldCopy(ctx)
            return old
        }
        return secure
    }

    /** Key taale mein save karo (khali ho to hata do) */
    @Synchronized
    fun setKey(ctx: Context, key: String) {
        val k = key.trim()
        val sec = securePrefs(ctx)
        val old = oldPrefs(ctx)
        if (k.isEmpty()) {
            sec.edit().remove(SECURE_NAME).apply()
            old.edit().remove(OLD_NAME).apply()
            return
        }
        val enc = encrypt(k)
        if (enc != null) {
            sec.edit().putString(SECURE_NAME, enc).apply()
            old.edit().remove(OLD_NAME).apply()
        } else {
            // Taala nahi ban paya (kuch phones par hota hai): purane tareeke se rakho taaki app chalti rahe
            old.edit().putString(OLD_NAME, k).apply()
        }
    }

    /** Taale mein key sahi se rakhi ho to purani khuli copy hata do */
    @Synchronized
    fun removeOldCopy(ctx: Context) {
        val old = oldRead(ctx)
        if (old.isEmpty()) return
        if (secureRead(ctx) == old) {
            oldPrefs(ctx).edit().remove(OLD_NAME).apply()
        }
    }

    // ---------- Naye helpers ----------
    private const val SEAL_PREFIX = "enc1:"

    /** Data ko encrypt karo. Taala na chale to jaisa hai waisa lautao (data khona nahi chahiye) */
    fun seal(plain: String): String {
        val e = encrypt(plain)
        return if (e != null) SEAL_PREFIX + e else plain
    }

    /** seal ka ulta. Purana khula data bhi seedha padh leta hai */
    fun open(stored: String): String {
        return if (stored.startsWith(SEAL_PREFIX)) decrypt(stored.removePrefix(SEAL_PREFIX)) else stored
    }

    @Synchronized
    fun getSecret(ctx: Context, name: String): String {
        val s = securePrefs(ctx).getString("s_$name", "") ?: ""
        return if (s.isBlank()) "" else decrypt(s)
    }

    @Synchronized
    fun setSecret(ctx: Context, name: String, value: String) {
        val v = value.trim()
        val ed = securePrefs(ctx).edit()
        if (v.isEmpty()) {
            ed.remove("s_$name")
        } else {
            val enc = encrypt(v)
            if (enc != null) ed.putString("s_$name", enc)
        }
        ed.apply()
    }

    fun hasSecret(ctx: Context, name: String) = getSecret(ctx, name).isNotBlank()

    /** Sab secrets hatao (Privacy: sab data mitao) */
    @Synchronized
    fun wipeAll(ctx: Context) {
        securePrefs(ctx).edit().clear().apply()
        oldPrefs(ctx).edit().remove(OLD_NAME).apply()
    }
}
