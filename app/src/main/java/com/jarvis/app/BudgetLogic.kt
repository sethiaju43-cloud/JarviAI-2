package com.jarvis.app

/*
 * Budget aur duplicate-request ka hisaab. Is file mein Android ka koi code nahi hai,
 * isliye ye JVM unit tests mein seedha test hota hai.
 */

enum class BudgetLevel { OFF, OK, WARN, EXCEEDED }

object BudgetLogic {

    /** limit 0 ya kam = band (OFF). 80% par WARN, 100% par EXCEEDED. */
    fun level(used: Double, limit: Double): BudgetLevel {
        if (limit <= 0.0) return BudgetLevel.OFF
        val r = used / limit
        return when {
            r >= 1.0 -> BudgetLevel.EXCEEDED
            r >= 0.8 -> BudgetLevel.WARN
            else -> BudgetLevel.OK
        }
    }

    /** Do levels mein jo zyada gambhir ho: EXCEEDED > WARN > OK > OFF */
    fun worst(a: BudgetLevel, b: BudgetLevel): BudgetLevel = if (a.ordinal >= b.ordinal) a else b

    /**
     * Ek hi sawaal do baar (jaise double-tap) pehchanne ke liye chabi.
     * Chabi mein kaam ka naam, baat-cheet ki lambai aur aakhri do message poore shamil hain,
     * isliye alag context wala sawaal kabhi barabar nahi hota.
     */
    fun dupKey(task: String, history: List<Pair<String, String>>): String {
        val tail = history.takeLast(2).joinToString("\u0001") { it.first + ":" + it.second.trim().lowercase() }
        return task + "|" + history.size + "|" + tail
    }

    /** Pichla jawab abhi bhi taaza hai (windowMs ke andar)? */
    fun isFresh(storedAtMs: Long, nowMs: Long, windowMs: Long): Boolean =
        nowMs >= storedAtMs && nowMs - storedAtMs <= windowMs
}
