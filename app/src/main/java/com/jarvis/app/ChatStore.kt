package com.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Chat history phone mein (encrypted). Abhi ki chat + purani chats ka archive. */
object ChatStore {

    private const val PREFS = "jarvis_chat"
    private const val KEY = "msgs"
    private const val ARCH = "archive"
    private const val MAX = 100
    private const val MAX_ARCH = 30

    class Archived(val id: String, val title: String, val ts: Long, val msgs: List<ChatMessage>)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(list: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        for (m in list) arr.put(JSONObject().put("t", m.text).put("u", m.isUser))
        return arr
    }

    private fun fromJson(arr: JSONArray): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(ChatMessage(o.getString("t"), o.getBoolean("u")))
        }
        return out
    }

    @Synchronized
    fun load(ctx: Context): List<ChatMessage> {
        return try {
            val s = prefs(ctx).getString(KEY, "[]") ?: "[]"
            fromJson(JSONArray(ApiKeyStore.open(s)))
        } catch (e: Exception) {
            ArrayList()
        }
    }

    @Synchronized
    fun save(ctx: Context, list: List<ChatMessage>) {
        try {
            val clean = list.filter { it.text != "..." }
            val start = (clean.size - MAX).coerceAtLeast(0)
            prefs(ctx).edit()
                .putString(KEY, ApiKeyStore.seal(toJson(clean.subList(start, clean.size)).toString()))
                .apply()
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun append(ctx: Context, text: String, isUser: Boolean) {
        val list = ArrayList(load(ctx))
        list.add(ChatMessage(text, isUser))
        save(ctx, list)
    }

    @Synchronized
    fun clear(ctx: Context) {
        try {
            prefs(ctx).edit().remove(KEY).apply()
        } catch (e: Exception) {
        }
    }

    // ---------- Archive (Conversation history) ----------
    @Synchronized
    private fun loadArchive(ctx: Context): ArrayList<Archived> {
        val out = ArrayList<Archived>()
        try {
            val s = prefs(ctx).getString(ARCH, null) ?: return out
            val arr = JSONArray(ApiKeyStore.open(s))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Archived(o.getString("id"), o.getString("title"), o.getLong("ts"),
                    fromJson(o.getJSONArray("msgs"))))
            }
        } catch (e: Exception) {
        }
        return out
    }

    @Synchronized
    private fun saveArchive(ctx: Context, list: List<Archived>) {
        val arr = JSONArray()
        for (a in list.take(MAX_ARCH)) {
            arr.put(JSONObject().put("id", a.id).put("title", a.title).put("ts", a.ts)
                .put("msgs", toJson(a.msgs)))
        }
        prefs(ctx).edit().putString(ARCH, ApiKeyStore.seal(arr.toString())).apply()
    }

    private fun titleOf(msgs: List<ChatMessage>): String {
        val first = msgs.firstOrNull { it.isUser }?.text ?: "Chat"
        return first.replace("\n", " ").take(40)
    }

    /** Abhi ki chat ko history mein daal kar nayi chat shuru. true = kuch archive hua */
    @Synchronized
    fun archiveCurrent(ctx: Context): Boolean {
        val cur = load(ctx)
        if (cur.none { it.isUser }) {
            clear(ctx)
            return false
        }
        val list = loadArchive(ctx)
        list.add(0, Archived(System.currentTimeMillis().toString(), titleOf(cur), System.currentTimeMillis(), cur))
        saveArchive(ctx, list)
        clear(ctx)
        return true
    }

    @Synchronized
    fun listArchive(ctx: Context): List<Archived> = loadArchive(ctx)

    @Synchronized
    fun restore(ctx: Context, id: String): Boolean {
        val list = loadArchive(ctx)
        val a = list.firstOrNull { it.id == id } ?: return false
        list.remove(a)
        saveArchive(ctx, list)
        archiveCurrent(ctx)
        save(ctx, a.msgs)
        return true
    }

    @Synchronized
    fun deleteArchive(ctx: Context, id: String) {
        val list = loadArchive(ctx)
        if (list.removeAll { it.id == id }) saveArchive(ctx, list)
    }

    @Synchronized
    fun clearArchive(ctx: Context) {
        prefs(ctx).edit().remove(ARCH).apply()
    }

    fun sizeBytes(ctx: Context): Long {
        val p = prefs(ctx)
        return ((p.getString(KEY, "") ?: "").length + (p.getString(ARCH, "") ?: "").length).toLong()
    }
}
