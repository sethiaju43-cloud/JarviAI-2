package com.jarvis.app

/** User ke feedback se jawab ka andaaz seekhna (pure logic) */
class ReplyStyle(val length: String, val lang: String, val simple: Boolean) {
    companion object {
        val DEFAULT = ReplyStyle("normal", "auto", false)
    }
}

object StyleLogic {
    private val shortWords = listOf(
        "chhota jawab", "chota jawab", "short mein", "short me", "brief", "concise",
        "kam shabd", "itna lamba mat", "bahut lamba", "chhota karo", "छोटा जवाब", "संक्षेप"
    )
    private val longWords = listOf(
        "detail mein", "detail me", "vistar se", "in detail", "step by step", "poora samjhao",
        "pura samjhao", "lamba jawab", "विस्तार से", "डिटेल में"
    )
    private val hiWords = listOf("hindi mein bolo", "hindi me bolo", "hindi mein jawab", "हिंदी में", "हिन्दी में")
    private val enWords = listOf("english mein", "english me", "in english", "speak english")
    private val hinglishWords = listOf("hinglish mein", "hinglish me")
    private val simpleWords = listOf("aasan bhasha", "asan bhasha", "simple bhasha", "simple words", "easy language", "आसान भाषा")
    private val resetWords = listOf("normal jawab", "pehle jaisa", "style reset", "normal style")

    private fun has(low: String, l: List<String>) = l.any { low.contains(it) }

    /** Naya style lautao (badla na ho to wahi purana object) */
    fun detect(text: String, cur: ReplyStyle): ReplyStyle {
        if (text.length > 160) return cur
        val low = text.lowercase()
        if (has(low, resetWords)) return ReplyStyle.DEFAULT
        var len = cur.length
        var lang = cur.lang
        var simple = cur.simple
        if (has(low, shortWords)) len = "short"
        if (has(low, longWords)) len = "detailed"
        if (has(low, hiWords)) lang = "hi"
        if (has(low, enWords)) lang = "en"
        if (has(low, hinglishWords)) lang = "hinglish"
        if (has(low, simpleWords)) simple = true
        if (len == cur.length && lang == cur.lang && simple == cur.simple) return cur
        return ReplyStyle(len, lang, simple)
    }

    fun promptLine(s: ReplyStyle): String {
        val sb = StringBuilder()
        when (s.length) {
            "short" -> sb.append("User ko bahut chhote jawab pasand hain (1-2 vaakya), jab tak woh detail na maange. ")
            "detailed" -> sb.append("User ko detail wale, step-by-step jawab pasand hain. ")
        }
        when (s.lang) {
            "hi" -> sb.append("User chahta hai ki tum Hindi mein jawab do. ")
            "en" -> sb.append("User chahta hai ki tum English mein jawab do. ")
            "hinglish" -> sb.append("User chahta hai ki tum Hinglish (Roman Hindi) mein jawab do. ")
        }
        if (s.simple) sb.append("Bahut aasan shabdon mein samjhao. ")
        return sb.toString()
    }
}
