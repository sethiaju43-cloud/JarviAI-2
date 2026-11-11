package com.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/**
 * Long-term memory (optional). Encrypted (Keystore) phone mein save hoti hai.
 * Sensitive lagne wali baat bina user ki haan ke save NAHI hoti (pending mein rukti hai).
 */
object MemoryStore {

    enum class AddResult { SAVED, DUPLICATE, SENSITIVE_PENDING, DISABLED, TOO_SHORT }

    class Pending(val text: String, val kind: String, val reason: String)

    private const val PREFS = "jarvis_mem2"
    private const val KEY = "entries"
    private const val MAX = 80

    private var cache: ArrayList<MemoryEntry>? = null
    private val pending = ArrayList<Pending>()
    private val rnd = Random()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun load(ctx: Context): ArrayList<MemoryEntry> {
        cache?.let { return it }
        val out = ArrayList<MemoryEntry>()
        val p = prefs(ctx)
        val stored = p.getString(KEY, null)
        if (stored != null) {
            try {
                val arr = JSONArray(ApiKeyStore.open(stored))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(MemoryEntry(o.getString("id"), o.getString("t"), o.optString("k", "fact"),
                        o.optLong("c", 0L), o.optBoolean("a", true)))
                }
            } catch (e: Exception) {
            }
        } else {
            // Purani "facts" list se ek baar import
            try {
                val legacy = ctx.applicationContext.getSharedPreferences("jarvis_mem", Context.MODE_PRIVATE)
                val s = legacy.getString("facts", null)
                if (s != null) {
                    val arr = JSONArray(s)
                    for (i in 0 until arr.length()) {
                        out.add(MemoryEntry(newId(), arr.getString(i), "fact", System.currentTimeMillis(), true))
                    }
                    legacy.edit().remove("facts").apply()
                }
            } catch (e: Exception) {
            }
        }
        cache = out
        if (stored == null && out.isNotEmpty()) save(ctx)
        return out
    }

    private fun newId() = java.lang.Long.toString(System.nanoTime(), 36) + rnd.nextInt(1000)

    @Synchronized
    private fun save(ctx: Context) {
        val list = cache ?: return
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject().put("id", e.id).put("t", e.text).put("k", e.kind)
                .put("c", e.createdAt).put("a", e.auto))
        }
        prefs(ctx).edit().putString(KEY, ApiKeyStore.seal(arr.toString())).apply()
    }

    fun enabled(ctx: Context) = Prefs.memoryEnabled(ctx)

    @Synchronized
    fun list(ctx: Context): List<MemoryEntry> = ArrayList(load(ctx))

    @Synchronized
    fun add(ctx: Context, textIn: String, kind: String = "fact", auto: Boolean = true, force: Boolean = false): AddResult {
        if (!force && !enabled(ctx)) return AddResult.DISABLED
        val t = textIn.trim().trim('"', '\'', '*', '.').take(200)
        if (t.length < 4) return AddResult.TOO_SHORT
        val list = load(ctx)
        val low = t.lowercase()
        if (list.any { it.text.lowercase() == low }) return AddResult.DUPLICATE
        if (!force) {
            val why = SensitiveFilter.reasons(t)
            if (why.isNotEmpty()) {
                if (pending.none { it.text.lowercase() == low }) pending.add(Pending(t, kind, why.joinToString(", ")))
                return AddResult.SENSITIVE_PENDING
            }
        }
        list.add(MemoryEntry(newId(), t, kind, System.currentTimeMillis(), auto))
        while (list.size > MAX) {
            val idx = list.indexOfFirst { it.auto }
            list.removeAt(if (idx >= 0) idx else 0)
        }
        save(ctx)
        return AddResult.SAVED
    }

    @Synchronized
    fun update(ctx: Context, id: String, text: String) {
        val e = load(ctx).firstOrNull { it.id == id } ?: return
        val t = text.trim().take(200)
        if (t.length >= 2) {
            e.text = t
            save(ctx)
        }
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        if (load(ctx).removeAll { it.id == id }) save(ctx)
    }

    @Synchronized
    fun clear(ctx: Context) {
        load(ctx).clear()
        pending.clear()
        save(ctx)
    }

    @Synchronized
    fun pendingList(): List<Pending> = ArrayList(pending)

    @Synchronized
    fun approvePending(ctx: Context, index: Int): AddResult {
        if (index !in pending.indices) return AddResult.TOO_SHORT
        val p = pending.removeAt(index)
        return add(ctx, p.text, p.kind, auto = false, force = true)
    }

    @Synchronized
    fun rejectPending(index: Int) {
        if (index in pending.indices) pending.removeAt(index)
    }

    /** Prompt mein jodne ke liye: sirf sawaal se milti-julti yaadein */
    fun contextLine(ctx: Context, query: String): String {
        if (!enabled(ctx)) return ""
        val rel = MemoryRanker.rank(query, list(ctx), 6)
        if (rel.isEmpty()) return ""
        val sb = StringBuilder("User ke baare mein saved yaadein (naturally kaam mein lo, har baar mat dohrao): ")
        for ((i, e) in rel.withIndex()) {
            if (i > 0) sb.append("; ")
            if (e.kind == "project") sb.append("(project) ")
            sb.append(e.text)
        }
        sb.append(". ")
        return sb.toString()
    }

    fun sizeBytes(ctx: Context): Long =
        (prefs(ctx).getString(KEY, "") ?: "").length.toLong()
}
