package com.jarvis.app

/** Model ki limit ke hisaab se purani baatein chhoti karna (Android ka code nahi) */
object ContextTrimmer {

    fun estimateTokens(s: String): Int {
        if (s.isEmpty()) return 0
        var deva = 0
        for (c in s) {
            if (c in '\u0900'..'\u097F') deva++
        }
        // Hindi akshar zyada token khate hain
        val per = if (deva * 4 > s.length) 1.6 else 3.5
        return (s.length / per).toInt() + 1
    }

    /**
     * Sabse nayi baatein rakho jab tak token budget ke andar ho.
     * Pehla item hamesha "user" hoga, aur aakhri item (abhi ka sawaal) kabhi nahi katega
     * (bahut lamba ho to uska ant chhota kar diya jata hai).
     */
    fun trim(
        history: List<Pair<String, String>>,
        budgetTokens: Int,
        maxTurns: Int = 40
    ): List<Pair<String, String>> {
        if (history.isEmpty()) return history
        val out = ArrayList<Pair<String, String>>()
        var used = 0
        val lastIdx = history.size - 1
        var last = history[lastIdx]
        val lastTok = estimateTokens(last.second)
        if (lastTok > budgetTokens) {
            val keepChars = (budgetTokens * 3).coerceAtLeast(200)
            last = Pair(last.first, last.second.takeLast(keepChars))
        }
        used += estimateTokens(last.second)
        out.add(last)
        var i = lastIdx - 1
        while (i >= 0 && out.size < maxTurns) {
            val t = estimateTokens(history[i].second)
            if (used + t > budgetTokens) break
            used += t
            out.add(history[i])
            i--
        }
        out.reverse()
        while (out.size > 1 && out[0].first != "user") out.removeAt(0)
        return out
    }
}
