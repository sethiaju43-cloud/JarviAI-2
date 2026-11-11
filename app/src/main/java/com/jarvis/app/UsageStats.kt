package com.jarvis.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI ki ginti (sirf phone mein rehti hai, kahin bheji nahi jati).
 *
 * MEASURED (asli ginti): kitni online calls gayi, kitni local jawab bane.
 * ESTIMATED (andaaza): tokens (text ki lambai / 4) aur bachaye hue paise. Dashboard mein dono alag likhe jate hain.
 */
object UsageStats {

    private const val KEEP_DAYS = 7
    private const val KEEP_DAYS_DASH = 30
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("jarvis_usage", Context.MODE_PRIVATE)

    private fun dayKey(ms: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    /** Text ki lambai se tokens ka andaaza (asli token count nahi, Hindi mein thoda zyada hota hai) */
    fun estTokens(chars: Int): Int = if (chars <= 0) 0 else (chars + 3) / 4

    /** Purani ginti hata do. n: = 7 din, baaki (l:, ti:, to:, f:, d:) = 30 din */
    private fun prune(p: android.content.SharedPreferences, e: android.content.SharedPreferences.Editor, now: Long) {
        val oldestN = dayKey(now - KEEP_DAYS * DAY_MS)
        val oldestDash = dayKey(now - KEEP_DAYS_DASH * DAY_MS)
        for (key in p.all.keys) {
            val parts = key.split(":", limit = 3)
            if (parts.size < 3) continue
            val limit = if (parts[0] == "n") oldestN else if (parts[0] in setOf("l", "ti", "to", "f", "d")) oldestDash else continue
            if (parts[1] < limit) e.remove(key)
        }
    }

    private fun bump(p: android.content.SharedPreferences, e: android.content.SharedPreferences.Editor, k: String, by: Long) {
        e.putLong(k, p.getLong(k, 0L) + by)
    }

    /** Ek asli online request gayi. label = provider ka naam (jaise "Google Gemini"). */
    @Synchronized
    fun hit(ctx: Context, label: String, now: Long = System.currentTimeMillis()) {
        try {
            val p = sp(ctx)
            val k = "n:" + dayKey(now) + ":" + label
            val e = p.edit()
            e.putInt(k, p.getInt(k, 0) + 1)
            prune(p, e, now)
            e.apply()
        } catch (t: Exception) {
        }
    }

    /** Local model ne ek jawab diya (internet ke bina). label = model ka naam. */
    @Synchronized
    fun local(ctx: Context, label: String, now: Long = System.currentTimeMillis()) {
        try {
            val p = sp(ctx)
            val e = p.edit()
            bump(p, e, "l:" + dayKey(now) + ":" + label, 1L)
            prune(p, e, now)
            e.apply()
        } catch (t: Exception) {
        }
    }

    /** Ek request ke tokens (andaaza). cloud=true to online, warna local. */
    @Synchronized
    fun tokens(ctx: Context, cloud: Boolean, tokIn: Int, tokOut: Int, now: Long = System.currentTimeMillis()) {
        try {
            val p = sp(ctx)
            val e = p.edit()
            val side = if (cloud) "C" else "L"
            bump(p, e, "ti:" + dayKey(now) + ":" + side, tokIn.toLong())
            bump(p, e, "to:" + dayKey(now) + ":" + side, tokOut.toLong())
            e.apply()
        } catch (t: Exception) {
        }
    }

    /** Online request fail hui (dashboard mein alag dikhti hai) */
    @Synchronized
    fun failure(ctx: Context, label: String, now: Long = System.currentTimeMillis()) {
        try {
            val p = sp(ctx)
            val e = p.edit()
            bump(p, e, "f:" + dayKey(now) + ":" + label, 1L)
            e.apply()
        } catch (t: Exception) {
        }
    }

    /** Ek duplicate request roki gayi (online call bachi, wahi purana jawab diya) */
    @Synchronized
    fun duplicate(ctx: Context, now: Long = System.currentTimeMillis()) {
        try {
            val p = sp(ctx)
            val e = p.edit()
            bump(p, e, "d:" + dayKey(now) + ":x", 1L)
            e.apply()
        } catch (t: Exception) {
        }
    }

    /** Ek din ki ginti: provider -> calls */
    private fun perProvider(ctx: Context, day: String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        val prefix = "n:$day:"
        for ((k, v) in sp(ctx).all) {
            if (k.startsWith(prefix) && v is Int) out[k.removePrefix(prefix)] = v
        }
        return out
    }

    /** prefix ("l", "f") ke liye pichhle [days] din ka label -> ginti */
    private fun sumByLabel(ctx: Context, prefix: String, days: Int, now: Long): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        val wanted = HashSet<String>()
        for (i in 0 until days) wanted.add(dayKey(now - i * DAY_MS))
        for ((k, v) in sp(ctx).all) {
            val parts = k.split(":", limit = 3)
            if (parts.size == 3 && parts[0] == prefix && parts[1] in wanted) {
                val n = when (v) { is Long -> v; is Int -> v.toLong(); else -> 0L }
                out[parts[2]] = (out[parts[2]] ?: 0L) + n
            }
        }
        return out
    }

    private fun sumCloudCalls(ctx: Context, days: Int, now: Long): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (i in 0 until days) {
            for ((k, v) in perProvider(ctx, dayKey(now - i * DAY_MS))) out[k] = (out[k] ?: 0L) + v
        }
        return out
    }

    private fun sumTokens(ctx: Context, prefix: String, side: String, days: Int, now: Long): Long {
        var sum = 0L
        val wanted = HashSet<String>()
        for (i in 0 until days) wanted.add(dayKey(now - i * DAY_MS))
        for ((k, v) in sp(ctx).all) {
            val parts = k.split(":", limit = 3)
            if (parts.size == 3 && parts[0] == prefix && parts[2] == side && parts[1] in wanted) {
                sum += when (v) { is Long -> v; is Int -> v.toLong(); else -> 0L }
            }
        }
        return sum
    }

    /** Dashboard ke liye poora hisaab (days = 1 aaj, 7, ya 30) */
    fun snapshot(ctx: Context, days: Int, now: Long = System.currentTimeMillis()): Snapshot {
        val d = days.coerceIn(1, KEEP_DAYS_DASH)
        val cloud = if (d <= KEEP_DAYS) sumCloudCalls(ctx, d, now) else sumCloudCalls(ctx, KEEP_DAYS, now)
        return Snapshot(
            days = d,
            cloudByProvider = cloud,
            localByModel = sumByLabel(ctx, "l", d, now),
            failedByProvider = sumByLabel(ctx, "f", d, now),
            duplicatesAvoided = sumByLabel(ctx, "d", d, now).values.sum(),
            tokInCloud = sumTokens(ctx, "ti", "C", d, now),
            tokOutCloud = sumTokens(ctx, "to", "C", d, now),
            tokInLocal = sumTokens(ctx, "ti", "L", d, now),
            tokOutLocal = sumTokens(ctx, "to", "L", d, now),
            cloudHistoryCapped = d > KEEP_DAYS
        )
    }

    /** Dashboard ka data. Calls = MEASURED. Tokens aur paise = ESTIMATE. */
    class Snapshot(
        val days: Int,
        val cloudByProvider: Map<String, Long>,
        val localByModel: Map<String, Long>,
        val failedByProvider: Map<String, Long>,
        /** Double-tap jaisi duplicate requests jo online gayi hi nahi (asli ginti) */
        val duplicatesAvoided: Long,
        val tokInCloud: Long,
        val tokOutCloud: Long,
        val tokInLocal: Long,
        val tokOutLocal: Long,
        /** online calls ki ginti sirf 7 din rakhi jati hai, isliye 30 din mein ye kam dikh sakti hai */
        val cloudHistoryCapped: Boolean
    ) {
        val cloudCalls: Long get() = cloudByProvider.values.sum()
        val localCalls: Long get() = localByModel.values.sum()
        val failedCalls: Long get() = failedByProvider.values.sum()
        val totalCalls: Long get() = cloudCalls + localCalls
        /** Local se jawab mile = utni online calls bachi (andaaza, kyunki local na hota to shayad online jaati) */
        val callsAvoided: Long get() = localCalls
        val tokensSaved: Long get() = tokInLocal + tokOutLocal
        val tokensSpentCloud: Long get() = tokInCloud + tokOutCloud
        fun moneySavedUsd(usdPerMillionTokens: Double): Double = tokensSaved / 1_000_000.0 * usdPerMillionTokens
        fun moneySpentUsd(usdPerMillionTokens: Double): Double = tokensSpentCloud / 1_000_000.0 * usdPerMillionTokens
    }

    /** Dashboard mein andaaze ka rate (USD per 10 lakh tokens). User badal sakta hai. */
    fun rate(ctx: Context): Double = Prefs.str(ctx, "usage_rate_usd_per_mtok", "0.50").toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 0.50

    fun setRate(ctx: Context, v: Double) = Prefs.setStr(ctx, "usage_rate_usd_per_mtok", v.toString())

    /** Saara usage hisaab mita do */
    @Synchronized
    fun clear(ctx: Context) {
        try { sp(ctx).edit().clear().apply() } catch (t: Exception) { }
    }

    fun report(ctx: Context, now: Long = System.currentTimeMillis()): String {
        val today = perProvider(ctx, dayKey(now))
        val yesterday = perProvider(ctx, dayKey(now - DAY_MS)).values.sum()
        var week = 0
        for (i in 0 until KEEP_DAYS) week += perProvider(ctx, dayKey(now - i * DAY_MS)).values.sum()
        val base = format(today, yesterday, week)
        val s = snapshot(ctx, 1, now)
        return if (s.localCalls > 0) base + "\nAaj local (offline) jawab: " + s.localCalls + " (ye online calls bachi, andaaza)." else base
    }

    /** Sirf text banata hai (tests ke liye alag) */
    fun format(today: Map<String, Int>, yesterday: Int, week: Int): String {
        val total = today.values.sum()
        val sb = StringBuilder("📊 Aaj online AI ki ")
        if (total == 0) {
            sb.append("koi call nahi hui.")
        } else {
            sb.append(total).append(" calls hui (")
            sb.append(today.entries.sortedByDescending { it.value }.joinToString(", ") { it.key + " " + it.value })
            sb.append(").")
        }
        sb.append(" Kal: ").append(yesterday).append(". Pichhle 7 din: ").append(week).append(".")
        sb.append("\nEk sawaal par retry ya kai-step kaam mein kai calls lag sakti hain. ")
        sb.append("Local model installed ho to 'local pehle' mode se online calls kam hongi.")
        return sb.toString()
    }

    private val apiWord = Regex("\\b(api|ai calls?|online calls?)\\b")
    private val askWords = listOf("kitni", "kitna", "usage", "use hui", "use hua", "kharch", "count", "ginti", "limit")
    private val notAsk = listOf("key", "kaise", "how", "kya hai", "kya hota")

    /** "API kitni use hui" jaisa sawaal? (chhota sawaal, AI ko bhejne ki zaroorat nahi) */
    fun isUsageQuestion(text: String): Boolean {
        val t = text.lowercase().trim()
        if (t.split(" ").size > 12) return false
        if (notAsk.any { t.contains(it) }) return false
        return apiWord.containsMatchIn(t) && askWords.any { t.contains(it) }
    }
}

/** Har request ko ginta hai (online = asli call, local = local jawab), baaki kaam andar wale backend ka hi hai */
class CountingBackend(private val ctx: Context, private val inner: Backend) : Backend by inner {
    override fun call(req: RouterRequest): Outcome {
        val label = inner.label.substringBefore(" · ")
        val local = inner.isLocal
        if (!local) UsageStats.hit(ctx, label)
        val out = inner.call(req)
        try {
            if (out.ok) {
                var inChars = if (local) req.systemLocal.length else req.systemOnline.length
                for (h in req.history) inChars += h.second.length
                UsageStats.tokens(ctx, !local, UsageStats.estTokens(inChars), UsageStats.estTokens(out.text.length))
                if (local) UsageStats.local(ctx, label)
            } else if (!local) {
                UsageStats.failure(ctx, label)
            }
        } catch (t: Exception) {
        }
        return out
    }
}
