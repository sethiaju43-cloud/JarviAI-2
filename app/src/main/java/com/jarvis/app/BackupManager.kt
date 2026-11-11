package com.jarvis.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup / Restore: settings, yaaddasht (memory), skills aur chats ek JSON file mein.
 * API keys aur tokens file mein NAHI jaate (wo sirf is phone ke tijori mein rehte hain, doosre phone par chalte hi nahi).
 */
object BackupManager {

    private const val FORMAT = "jarvis-backup"
    private const val VERSION = 1

    class Outcome(val ok: Boolean, val message: String)

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

    /** Poora backup ek text (JSON) mein */
    fun export(ctx: Context): String {
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

        return root.toString(2)
    }

    /** Backup wapas daalo. Purani cheezein mitati nahi: yaadein, skills aur chats jud jati hain, settings badal jati hain */
    fun import(ctx: Context, json: String): Outcome {
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
            "Backup wapas aa gaya ✅ Settings: " + nPrefs + ", Yaadein: " + nMem + ", Skills: " + nSkills + ", Purani chats: " + nChats +
                ". App band karke dobara kholo."
        )
    }
}
