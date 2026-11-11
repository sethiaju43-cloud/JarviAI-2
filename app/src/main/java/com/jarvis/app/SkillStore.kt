package com.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/**
 * Skills: user ne Jarvi ko jo kaam sikhaya (naam + jab main ye bolun + kya karna hai).
 * Encrypted (Keystore) phone mein save hoti hain. Sawaal se milti skill AI ke prompt mein jaati hai.
 */
object SkillStore {

    class Skill(val id: String, var name: String, var trigger: String, var steps: String)

    private const val PREFS = "jarvis_skills"
    private const val KEY = "skills"
    private const val MAX = 40

    private var cache: ArrayList<Skill>? = null
    private val rnd = Random()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun newId() = java.lang.Long.toString(System.nanoTime(), 36) + rnd.nextInt(1000)

    @Synchronized
    private fun load(ctx: Context): ArrayList<Skill> {
        cache?.let { return it }
        val out = ArrayList<Skill>()
        val stored = prefs(ctx).getString(KEY, null)
        if (stored != null) {
            try {
                val arr = JSONArray(ApiKeyStore.open(stored))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Skill(o.getString("id"), o.optString("n", ""), o.optString("w", ""), o.optString("s", "")))
                }
            } catch (e: Exception) {
            }
        }
        cache = out
        return out
    }

    @Synchronized
    private fun save(ctx: Context) {
        val list = cache ?: return
        val arr = JSONArray()
        for (k in list) {
            arr.put(JSONObject().put("id", k.id).put("n", k.name).put("w", k.trigger).put("s", k.steps))
        }
        prefs(ctx).edit().putString(KEY, ApiKeyStore.seal(arr.toString())).apply()
    }

    @Synchronized
    fun list(ctx: Context): List<Skill> = ArrayList(load(ctx))

    /** Nayi skill jodo. Naam aur steps zaroori. Pehle se wahi ho ya 40 se zyada ho to false */
    @Synchronized
    fun add(ctx: Context, nameIn: String, triggerIn: String, stepsIn: String): Boolean {
        val n = nameIn.trim().take(40)
        val w = triggerIn.trim().take(120)
        val s = stepsIn.trim().take(400)
        if (n.isEmpty() || s.length < 4) return false
        val list = load(ctx)
        if (list.size >= MAX) return false
        val dupName = n.lowercase()
        val dupWhen = w.lowercase()
        if (list.any { it.name.lowercase() == dupName && it.trigger.lowercase() == dupWhen }) return false
        list.add(Skill(newId(), n, w, s))
        save(ctx)
        return true
    }

    @Synchronized
    fun update(ctx: Context, id: String, nameIn: String, triggerIn: String, stepsIn: String): Boolean {
        val k = load(ctx).firstOrNull { it.id == id } ?: return false
        val n = nameIn.trim().take(40)
        val w = triggerIn.trim().take(120)
        val s = stepsIn.trim().take(400)
        if (n.isEmpty() || s.length < 4) return false
        k.name = n
        k.trigger = w
        k.steps = s
        save(ctx)
        return true
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        if (load(ctx).removeAll { it.id == id }) save(ctx)
    }

    /** Prompt mein jodne ke liye: sirf sawaal se milti-julti (zyada se zyada 3) skills */
    fun line(ctx: Context, query: String): String {
        val all = list(ctx)
        if (all.isEmpty()) return ""
        val fake = ArrayList<MemoryEntry>()
        for (k in all) {
            fake.add(MemoryEntry(k.id, k.name + " " + k.trigger + " " + k.steps, "project", 0L, false))
        }
        val hits = MemoryRanker.rank(query, fake, 3)
        if (hits.isEmpty()) return ""
        val sb = StringBuilder("User ne tumhe ye skills sikhayi hain. Agar user ka kaam inse milta ho to wahi tareeka apnao " +
            "(phone ke kaam ke liye upar bataye OPEN/ACTION format mein hi jawab do): ")
        for (h in hits) {
            val k = all.firstOrNull { it.id == h.id } ?: continue
            sb.append("[").append(k.name).append("] ")
            if (k.trigger.isNotEmpty()) sb.append("jab user bole '").append(k.trigger).append("' to ")
            sb.append(k.steps).append("; ")
        }
        sb.append(". ")
        return sb.toString()
    }
}
