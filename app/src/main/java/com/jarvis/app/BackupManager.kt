package com.jarvis.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup / Restore: settings, yaaddasht (memory), skills aur chats ek JSON file mein.
 * Password lagao to poori file AES-256 se band (encrypt) ho jati hai.
 * API keys SIRF tab jaati hain jab backup password laga ho (tab wo band file ke andar rehti hain).
 * Bina password ke backup mein keys kabhi nahi jaati.
 */
object BackupManager {

    private const val FORMAT = "jarvis-backup"
    private const val VERSION = 1
    private const val ENC_FORMAT = "jarvis-backup-encrypted"

    class Outcome(val ok: Boolean, val message: String, val needsPassword: Boolean = false)

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }

    /** Text ko password se band karo (AES-256-GCM) */
    fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16)
        rnd.nextBytes(salt)
        val iv = ByteArray(12)
        rnd.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val o = JSONObject()
        o.put("format", ENC_FORMAT)
        o.put("version", VERSION)
        o.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        o.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        o.put("data", Base64.encodeToString(enc, Base64.NO_WRAP))
        return o.toString(2)
    }

    /** Band file khologe. Password galat ho to null */
    fun decrypt(text: String, password: String): String? {
        try {
            val o = JSONObject(text)
            val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(o.getString("data"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
    }

    /** Kya ye file password se band hai? */
    fun isEncrypted(text: String): Boolean {
        return try {
            JSONObject(text).optString("format", "") == ENC_FORMAT
        } catch (e: Exception) {
            false
        }
    }

    /** Backup text: agar password laga hai to band (encrypted, keys ke saath), warna seedha (keys ke bina) */
    fun exportSecure(ctx: Context): String {
        val pw = AutoBackup.password(ctx)
        return if (pw.isEmpty()) export(ctx, false) else encrypt(export(ctx, true), pw)
    }

    // ---------- API keys (sirf password wale backup mein) ----------
    private fun keyNames(): List<String> {
        val out = ArrayList<String>()
        for (p in ProviderRegistry.presets) out.add("prov:" + p.id)
        out.add("search_tavily")
        out.add("search_brave")
        out.add("hf_token")
        return out
    }

    private fun readKey(ctx: Context, name: String): String {
        if (name == "prov:gemini") return ApiKeyStore.getKey(ctx)
        if (name.startsWith("prov:")) return ApiKeyStore.getSecret(ctx, "prov_" + name.removePrefix("prov:"))
        return ApiKeyStore.getSecret(ctx, name)
    }

    private fun writeKey(ctx: Context, name: String, value: String) {
        if (name == "prov:gemini") {
            ApiKeyStore.setKey(ctx, value)
        } else if (name.startsWith("prov:")) {
            ApiKeyStore.setSecret(ctx, "prov_" + name.removePrefix("prov:"), value)
        } else {
            ApiKeyStore.setSecret(ctx, name, value)
        }
    }

    private fun skip(key: String): Boolean {
        val k = key.lowercase()
        return k == "crash" || k.contains("api_key") || k.contains("token") ||
            k.contains("secret") || k.contains("password")
    }

    private fun encodePref(k: String, v: Any?): JSONObject? {
        val o = JSONObject()
        o.put("k", k)
        if (v is Boolean) {
            o.put("t", "b")
            o.put("v", v)
        } else if (v is Int) {
            o.put("t", "i")
            o.put("v", v)
        } else if (v is Long) {
            o.put("t", "l")
            o.put("v", v)
        } else if (v is Float) {
            o.put("t", "f")
            o.put("v", v.toDouble())
        } else if (v is String) {
            o.put("t", "s")
            o.put("v", v)
        } else {
            return null
        }
        return o
    }

    private fun applyPref(ed: SharedPreferences.Editor, o: JSONObject): Boolean {
        val k = o.optString("k", "")
        if (k.isEmpty() || skip(k)) return false
        return when (o.optString("t", "")) {
            "b" -> { ed.putBoolean(k, o.optBoolean("v", false)); true }
            "i" -> { ed.putInt(k, o.optInt("v", 0)); true }
            "l" -> { ed.putLong(k, o.optLong("v", 0L)); true }
            "f" -> { ed.putFloat(k, o.optDouble("v", 0.0).toFloat()); true }
            "s" -> { ed.putString(k, o.optString("v", "")); true }
            else -> false
        }
    }

    private fun msgsToJson(list: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("t", m.text)
            o.put("u", m.isUser)
            arr.put(o)
        }
        return arr
    }

    private fun jsonToMsgs(arr: JSONArray?): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(ChatMessage(o.optString("t", ""), o.optBoolean("u", false)))
        }
        return out
    }

    /** Poora backup ek text (JSON) mein. withKeys = true tabhi dena jab text aage password se band hone wala ho */
    fun export(ctx: Context, withKeys: Boolean = false): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("time", System.currentTimeMillis())

        val prefs = JSONArray()
        for ((k, v) in Prefs.sp(ctx).all) {
            if (skip(k)) continue
            val o = encodePref(k, v)
            if (o != null) prefs.put(o)
        }
        root.put("prefs", prefs)

        val mem = JSONArray()
        for (e in MemoryStore.list(ctx)) {
            val o = JSONObject()
            o.put("t", e.text)
            o.put("k", e.kind)
            o.put("c", e.createdAt)
            o.put("a", e.auto)
            mem.put(o)
        }
        root.put("memory", mem)

        val skills = JSONArray()
        for (k in SkillStore.list(ctx)) {
            val o = JSONObject()
            o.put("n", k.name)
            o.put("w", k.trigger)
            o.put("s", k.steps)
            skills.put(o)
        }
        root.put("skills", skills)

        root.put("chat", msgsToJson(ChatStore.load(ctx)))

        val arch = JSONArray()
        for (a in ChatStore.listArchive(ctx)) {
            val o = JSONObject()
            o.put("title", a.title)
            o.put("ts", a.ts)
            o.put("msgs", msgsToJson(a.msgs))
            arch.put(o)
        }
        root.put("archive", arch)

        if (withKeys) {
            val keys = JSONArray()
            for (n in keyNames()) {
                val v = readKey(ctx, n)
                if (v.isNotBlank()) {
                    val o = JSONObject()
                    o.put("n", n)
                    o.put("v", v)
                    keys.put(o)
                }
            }
            root.put("keys", keys)
        }

        return root.toString(2)
    }

    /** Backup wapas daalo. Purani cheezein mitati nahi: yaadein aur chats jud jati hain, settings badal jati hain */
    fun import(ctx: Context, jsonIn: String, password: String? = null): Outcome {
        var json = jsonIn
        if (isEncrypted(jsonIn)) {
            if (password.isNullOrEmpty()) {
                return Outcome(false, "Ye backup password se band hai", true)
            }
            val opened = decrypt(jsonIn, password)
            if (opened == null) {
                return Outcome(false, "Password galat hai", true)
            }
            json = opened
        }
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return Outcome(false, "Ye Jarvis ki backup file nahi lagti")
        }
        if (root.optString("format", "") != FORMAT) {
            return Outcome(false, "Ye Jarvis ki backup file nahi lagti")
        }

        var nPrefs = 0
        var nMem = 0
        var nChats = 0
        var nSkills = 0
        var nKeys = 0

        val pa = root.optJSONArray("prefs")
        if (pa != null) {
            val ed = Prefs.sp(ctx).edit()
            for (i in 0 until pa.length()) {
                val o = pa.optJSONObject(i) ?: continue
                if (applyPref(ed, o)) nPrefs++
            }
            ed.apply()
        }

        val ma = root.optJSONArray("memory")
        if (ma != null) {
            for (i in 0 until ma.length()) {
                val o = ma.optJSONObject(i) ?: continue
                val r = MemoryStore.add(ctx, o.optString("t", ""), o.optString("k", "fact"), o.optBoolean("a", false), true)
                if (r == MemoryStore.AddResult.SAVED) nMem++
            }
        }

        val sa = root.optJSONArray("skills")
        if (sa != null) {
            for (i in 0 until sa.length()) {
                val o = sa.optJSONObject(i) ?: continue
                if (SkillStore.add(ctx, o.optString("n", ""), o.optString("w", ""), o.optString("s", ""))) nSkills++
            }
        }

        val ka = root.optJSONArray("keys")
        if (ka != null) {
            val allowed = keyNames()
            for (i in 0 until ka.length()) {
                val o = ka.optJSONObject(i) ?: continue
                val n = o.optString("n", "")
                val v = o.optString("v", "")
                if (n in allowed && v.isNotBlank()) {
                    writeKey(ctx, n, v)
                    nKeys++
                }
            }
        }

        val current = ChatStore.load(ctx)
        val aa = root.optJSONArray("archive")
        if (aa != null) {
            val existing = ChatStore.listArchive(ctx)
            // backup mein sabse nayi chat pehle hai, isliye peeche se shuru (purani pehle)
            for (i in aa.length() - 1 downTo 0) {
                val o = aa.optJSONObject(i) ?: continue
                val msgs = jsonToMsgs(o.optJSONArray("msgs"))
                if (msgs.none { it.isUser }) continue
                val title = o.optString("title", "")
                if (existing.any { it.title == title && it.msgs.size == msgs.size }) continue
                ChatStore.save(ctx, msgs)
                if (ChatStore.archiveCurrent(ctx)) nChats++
            }
        }

        val backupCurrent = jsonToMsgs(root.optJSONArray("chat"))
        val keep = if (current.none { it.isUser } && backupCurrent.any { it.isUser }) backupCurrent else current
        ChatStore.save(ctx, keep)

        return Outcome(
            true,
            "Backup wapas aa gaya ✅ Settings: " + nPrefs + ", Yaadein: " + nMem + ", Skills: " + nSkills + ", Keys: " + nKeys + ", Purani chats: " + nChats +
                ". App band karke dobara kholo."
        )
    }
}
