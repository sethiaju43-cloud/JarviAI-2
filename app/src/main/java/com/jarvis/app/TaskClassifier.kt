package com.jarvis.app

/** User ke message se andaaza: ye kaisa kaam hai? (Android ka koi code nahi, testable) */
object TaskClassifier {

    private val splitter = Regex("[^\\p{L}\\p{M}\\p{Nd}+#]+")

    fun tokens(text: String): Set<String> =
        text.lowercase().split(splitter).filter { it.isNotBlank() }.toSet()

    private val searchTokens = setOf(
        "search", "सर्च", "news", "न्यूज", "समाचार", "samachar", "mausam", "मौसम", "weather",
        "score", "स्कोर", "ढूंढ", "ढूँढ", "dhundo", "dhoondo", "latest", "price", "daam", "दाम",
        "current", "currently", "headlines"
    )
    private val searchPhrases = listOf(
        "aaj ka", "आज का", "aaj ki", "आज की", "abhi ka", "stock price", "share price",
        "live score", "kaun jeeta", "who won", "who is the current", "kitne ka hai"
    )

    private val complexTokens = setOf(
        "kyun", "kyu", "kyon", "kaise", "why", "how", "explain", "samjhao", "समझाओ", "compare",
        "tulna", "difference", "antar", "solve", "calculate", "hisab", "derive", "prove", "plan",
        "strategy", "step", "logic", "reason", "analyse", "analyze", "optimize", "algorithm",
        "pros", "cons", "behtar", "better", "decide", "equation", "puzzle", "riddle", "probability"
    )

    private val codeTokens = setOf(
        "code", "program", "function", "python", "java", "kotlin", "javascript", "js", "html",
        "css", "sql", "bug", "error", "exception", "compile", "script", "api", "regex", "class",
        "array", "loop", "c++", "debug", "gradle", "android", "stacktrace"
    )

    private val summarizeTokens = setOf(
        "summarize", "summarise", "summary", "saransh", "सारांश", "rewrite", "rephrase",
        "paraphrase", "translate", "anuvad", "अनुवाद"
    )
    private val summarizePhrases = listOf("short mein", "dobara likho", "mail likh", "email likh")

    private val phoneTokens = setOf(
        "kholo", "khol", "open", "call", "sms", "whatsapp", "alarm", "timer", "torch", "flashlight",
        "volume", "battery", "photo", "selfie", "scroll", "click", "tap", "screenshot", "lock",
        "brightness", "wifi", "bluetooth", "hotspot", "navigate", "maps", "gaana", "music",
        "dabao", "chalao", "back", "home", "recents", "notification", "notifications"
    )

    private val questionTokens = setOf(
        "who", "what", "when", "where", "which", "kaun", "kya", "kab", "kahan", "kitna", "kitne",
        "kaunsa", "capital", "president", "ceo", "population", "year"
    )

    private fun hasPhrase(t: String, list: List<String>): Boolean {
        val low = t.lowercase()
        return list.any { low.contains(it) }
    }

    fun needsSearch(text: String): Boolean {
        val tk = tokens(text)
        if (tk.any { it in searchTokens }) return true
        return hasPhrase(text, searchPhrases)
    }

    fun isPhone(text: String): Boolean {
        if (text.length > 160) return false
        return tokens(text).any { it in phoneTokens }
    }

    fun isCode(text: String): Boolean {
        if (text.contains("```")) return true
        val tk = tokens(text)
        if (tk.any { it in codeTokens }) return true
        return text.contains("{") && text.contains(";") && text.contains("(")
    }

    fun isSummarize(text: String): Boolean {
        val tk = tokens(text)
        if (tk.any { it in summarizeTokens }) return true
        return hasPhrase(text, summarizePhrases)
    }

    private val mathRegex = Regex("\\d+\\s*[+\\-*/x×÷^]\\s*\\d+")

    fun isComplex(text: String): Boolean {
        val len = text.trim().length
        if (len >= 220) return true
        if (mathRegex.containsMatchIn(text)) return true
        val tk = tokens(text)
        if (len >= 35 && tk.any { it in complexTokens }) return true
        return text.count { it == '?' } >= 2
    }

    fun looksFactual(text: String): Boolean {
        val tk = tokens(text)
        if (tk.none { it in questionTokens }) return false
        return text.trim().endsWith("?") || text.trim().length >= 15
    }

    // User khud bole "dhyan se / soch ke batao" to seedha tagda model (mushkil kaam) chalega
    private val deepPhrases = listOf(
        "soch ke bata", "soch kar bata", "sochke bata", "soch ke jawab", "dhyan se bata", "dhyan se jawab",
        "gehrai se", "gahrai se", "detail mein", "detail me ", "deep think", "think hard", "think step",
        "सोच के बता", "सोचकर बता", "ध्यान से बता", "गहराई से"
    )

    fun wantsDeep(text: String): Boolean = hasPhrase(text, deepPhrases)

    fun classify(text: String, hasImage: Boolean = false): Task {
        if (hasImage) return Task.VISION
        if (isPhone(text)) return Task.PHONE
        if (wantsDeep(text)) return Task.REASONING
        if (needsSearch(text)) return Task.SEARCH
        if (isCode(text)) return Task.CODE
        if (isSummarize(text)) return Task.SUMMARIZE
        if (isComplex(text)) return Task.REASONING
        return Task.CHAT
    }
}
