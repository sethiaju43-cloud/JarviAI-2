package com.jarvis.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Online AI ki limit (budget). Do limit ho sakti hain, dono optional (0 = band):
 *  - roz ki online calls (asli ginti)
 *  - pichhle 30 din ka kharch (ANDAAZA: tokens x aapka rate, asli bill nahi)
 * Limit poori hone par online model band, aur local model ho to wahi chalta hai.
 */
object Budget {

    class State(
        val dailyLevel: BudgetLevel,
        val monthlyLevel: BudgetLevel,
        val callsToday: Long,
        val dailyLimit: Int,
        val usdMonth: Double,
        val monthlyLimit: Double
    ) {
        val level: BudgetLevel get() = BudgetLogic.worst(dailyLevel, monthlyLevel)
        val exceeded: Boolean get() = level == BudgetLevel.EXCEEDED
    }

    fun dailyCallLimit(ctx: Context): Int =
        Prefs.str(ctx, "budget_daily_calls", "0").toIntOrNull()?.coerceIn(0, 100000) ?: 0

    fun setDailyCallLimit(ctx: Context, v: Int) =
        Prefs.setStr(ctx, "budget_daily_calls", v.coerceIn(0, 100000).toString())

    fun monthlyUsdLimit(ctx: Context): Double =
        Prefs.str(ctx, "budget_monthly_usd", "0").toDoubleOrNull()?.coerceIn(0.0, 100000.0) ?: 0.0

    fun setMonthlyUsdLimit(ctx: Context, v: Double) =
        Prefs.setStr(ctx, "budget_monthly_usd", v.coerceIn(0.0, 100000.0).toString())

    fun state(ctx: Context): State {
        val dLimit = dailyCallLimit(ctx)
        val mLimit = monthlyUsdLimit(ctx)
        if (dLimit <= 0 && mLimit <= 0.0) {
            return State(BudgetLevel.OFF, BudgetLevel.OFF, 0L, 0, 0.0, 0.0)
        }
        val today = UsageStats.snapshot(ctx, 1)
        val usd = if (mLimit > 0.0) UsageStats.snapshot(ctx, 30).moneySpentUsd(UsageStats.rate(ctx)) else 0.0
        return State(
            BudgetLogic.level(today.cloudCalls.toDouble(), dLimit.toDouble()),
            BudgetLogic.level(usd, mLimit),
            today.cloudCalls, dLimit, usd, mLimit
        )
    }

    fun blockMessage(s: State): String {
        val why = if (s.dailyLevel == BudgetLevel.EXCEEDED) {
            "Aaj ki online limit (" + s.dailyLimit + " calls) poori ho gayi"
        } else {
            "Mahine ki kharch limit (andaaza ~$" + String.format(Locale.US, "%.2f", s.monthlyLimit) + ") poori ho gayi"
        }
        return why + " 🛑 Local model install ho to wahi chalega. Limit badalni ho to Settings → AI Dashboard → Budget."
    }

    /** Chetavni ka text (80% ya 100%), din mein har level par sirf ek baar. Naya ho to text, warna null. */
    fun alertOnce(ctx: Context, s: State): String? {
        if (s.level != BudgetLevel.WARN && s.level != BudgetLevel.EXCEEDED) return null
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val tag = day + ":" + s.level.name
        if (Prefs.str(ctx, "budget_alert_tag", "") == tag) return null
        Prefs.setStr(ctx, "budget_alert_tag", tag)
        return if (s.level == BudgetLevel.WARN) {
            "⚠️ Online limit ka 80% ho gaya. Dashboard mein dekh lo."
        } else {
            "🛑 Online limit poori ho gayi, ab local model se jawab aayega."
        }
    }
}
