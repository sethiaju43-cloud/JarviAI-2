package com.jarvis.app

/*
 * Chhote seedhe commands jo AI ko bheje bina phone par hi ho jate hain (API ki bachat):
 *  - yaaddasht: "chai wali baat bhool jao", "sudhaar: main Mumbai mein rehta hun", "mere baare mein kya yaad hai"
 *  - awaaz: "dobara bolo", "dheere bolo", "tez bolo", "normal speed"
 * Is file mein Android ka koi code nahi hai, isliye ye JVM unit tests mein seedha test hota hai.
 * Sirf poore, saaf vaakya pakde jate hain (andaaze se nahi), taaki galti se kuch na mitey.
 */

sealed class SelfCommand {
    class Forget(val about: String) : SelfCommand()
    class Correct(val fact: String) : SelfCommand()
    object Recall : SelfCommand()
    object Repeat : SelfCommand()

    /** dir: -1 dheere, +1 tez, 0 normal */
    class Speed(val dir: Int) : SelfCommand()
}

object SelfCommands {

    private const val MIN_SPEED = 0.6f
    private const val MAX_SPEED = 1.5f
    private const val STEP = 0.15f

    private val repeatPhrases = setOf(
        "dobara bolo", "dobara batao", "dobara kaho", "dobara sunao", "phir se bolo", "phir se batao",
        "phir se sunao", "ek baar aur bolo", "ek baar phir bolo", "kya bola", "kya bola tha", "kya kaha tha",
        "kya kaha", "repeat", "repeat that", "repeat it", "say that again", "say again",
        "फिर से बोलो", "दोबारा बोलो"
    )
    private val slowerPhrases = setOf(
        "dheere bolo", "dheere se bolo", "thoda dheere bolo", "aur dheere bolo", "slow bolo", "slowly bolo",
        "speak slowly", "slow down", "thoda slow bolo", "धीरे बोलो", "थोड़ा धीरे बोलो"
    )
    private val fasterPhrases = setOf(
        "tez bolo", "tez awaaz mein bolo", "thoda tez bolo", "aur tez bolo", "fast bolo", "speak faster",
        "speak fast", "faster", "तेज़ बोलो", "तेज बोलो", "थोड़ा तेज बोलो"
    )
    private val normalPhrases = setOf(
        "normal speed", "normal speed mein bolo", "normal awaaz", "normal bolo", "speed normal karo", "speed reset"
    )

    private val forgetPatterns = listOf(
        Regex("^(.{3,}?) (?:bhool jao|bhool ja|bhul jao|yaad mat rakho|yaad se hata do|yaad se mita do|memory se hata do|memory se mita do)$"),
        Regex("^forget (?:that |about |what i said about )?(.{3,})$"),
        Regex("^(.{3,}?) (?:भूल जाओ|भूल जा)$")
    )
    private val genericAbout = setOf(
        "sab", "sab kuch", "saari baatein", "sab baatein", "sabhi", "everything", "all", "isko", "usko", "ise", "use",
        "it", "this", "that", "mujhe", "mujhko", "wo", "woh", "vo", "ye", "yeh", "ye sab", "wo sab"
    )

    private val correctPatterns = listOf(
        Regex("(?i)^\\s*(?:jarvi\\s+|jarvis\\s+)?(?:sudhaar|sudhar|correction|correct\\s+that)\\s*[:,\\-]\\s*(.{4,})$"),
        Regex("^\\s*सुधार\\s*[:,\\-]\\s*(.{4,})$")
    )

    private val recallPatterns = listOf(
        Regex("mere (?:baare|bare) mein.*(?:yaad|pata|jaan)"),
        Regex("^kya(?: kya)? yaad hai(?: tumhe)?$"),
        Regex("what do you (?:remember|know) about me"),
        Regex("(?:tumhe|tumko|aapko) (?:mere (?:baare|bare) mein )?kya(?: kya)? (?:yaad|pata) hai")
    )

    private fun norm(text: String): String {
        var t = text.lowercase().trim().trimEnd('.', '!', '?', '।', ',').trim()
        t = t.replace(Regex("\\s+"), " ")
        t = t.replace(Regex("^(?:hey |hi |ok )?(?:jarvi|jarvis)[ ,]+"), "")
        t = t.replace(Regex("^(?:wo|woh|vo|ye|yeh) "), "")
        return t
    }

    /** User ke text se command pehchano; saaf command na ho to null (tab normal AI chalega) */
    fun parse(text: String): SelfCommand? {
        if (text.isBlank() || text.length > 160) return null

        for (p in correctPatterns) {
            val m = p.find(text.trim())
            if (m != null) return SelfCommand.Correct(m.groupValues[1].trim())
        }

        val t = norm(text)
        if (t.isEmpty()) return null
        if (t in repeatPhrases) return SelfCommand.Repeat
        if (t in slowerPhrases) return SelfCommand.Speed(-1)
        if (t in fasterPhrases) return SelfCommand.Speed(1)
        if (t in normalPhrases) return SelfCommand.Speed(0)
        if (recallPatterns.any { it.containsMatchIn(t) }) return SelfCommand.Recall

        for (p in forgetPatterns) {
            val m = p.find(t)
            if (m != null) {
                val about = m.groupValues[1].trim()
                if (about in genericAbout) return null
                if (queryWords(about).isEmpty()) return null
                return SelfCommand.Forget(about)
            }
        }
        return null
    }

    /** Awaaz ki speed ka agla kadam (dir: -1 dheere, +1 tez, 0 normal). 0.6 se 1.5 ke beech rehti hai. */
    fun nextSpeed(cur: Float, dir: Int): Float {
        if (dir == 0) return 1.0f
        val n = cur + dir * STEP
        val c = if (n < MIN_SPEED) MIN_SPEED else if (n > MAX_SPEED) MAX_SPEED else n
        return Math.round(c * 100f) / 100f
    }

    class Match(val index: Int, val ambiguous: Boolean)

    /** Matching mein bekaar shabd (jaise "chai WALI BAAT"): inhe query se hata do */
    private val fillers = setOf(
        "wali", "wala", "wale", "vali", "vala", "baat", "baatein", "bare", "baare", "about", "kuch", "kuchh", "jo",
        "maine", "bataya", "batayi", "thing", "stuff"
    )

    private fun queryWords(q: String): Set<String> = MemoryRanker.words(q) - fillers

    /**
     * query se sabse milti-julti yaad dhundo. Sirf tab milti hai jab query ke kam se kam minCoverage hisse
     * ke shabd us yaad mein ho. Do yaadein barabar milti hon to ambiguous=true (galti se galat na mitey).
     */
    fun bestMatch(query: String, texts: List<String>, minCoverage: Double): Match {
        val q = queryWords(query)
        if (q.isEmpty()) return Match(-1, false)
        var best = -1
        var bestScore = 0.0
        var tie = false
        for ((i, t) in texts.withIndex()) {
            val w = MemoryRanker.words(t)
            var overlap = 0
            for (x in q) if (x in w) overlap++
            if (overlap == 0) continue
            val coverage = overlap.toDouble() / q.size
            if (coverage < minCoverage) continue
            val score = coverage
            if (best < 0 || score > bestScore + 1e-9) {
                best = i
                bestScore = score
                tie = false
            } else if (Math.abs(score - bestScore) <= 1e-9) {
                tie = true
            }
        }
        return Match(best, best >= 0 && tie)
    }
}
