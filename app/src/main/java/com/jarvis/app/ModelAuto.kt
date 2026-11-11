package com.jarvis.app

/**
 * Models KHUD chunna (automatic). Provider ki /models list se chat wale models chunta hai,
 * unhe ek line mein lagata hai (tagda pehle ya tez pehle), aur sirf kuch hi (max) rakhta hai.
 * Is line ko ModelChain tier ke hisaab se sab providers mein milata hai.
 *
 * Dhyan: ye aapki key ki list se hi chunta hai. Agar aapke account par billing on hai to bade models paid ho sakte hain.
 * OpenRouter par sirf ':free' wale models liye jaate hain.
 */
object ModelAuto {

    // Ye naam wale models chat ke liye nahi hote (awaaz, image, embedding, safety...)
    private val skip = listOf(
        "embed", "tts", "whisper", "audio", "imagen", "image", "veo", "live", "transcribe", "moderation",
        "guard", "safeguard", "robotics", "aqa", "learnlm", "computer-use", "dall", "rerank", "ocr",
        "deep-research", "speech", "vision-preview"
    )

    private val versionRegex = Regex("(\\d+(?:\\.\\d+)?)")

    // "Sochne wale" (reasoning) models pehle lamba sochte hain, isliye roz ki tez baat-cheet mein nahi chunte
    private val thinkerWords = listOf("reason", "think", "qwq", "deepseek-r", "-r1", "/r1", "magistral")
    private val thinkerRegex = Regex("(^|[-_/:.])(o1|o3|o4)([-_.:]|$)")

    fun isThinker(name: String): Boolean {
        val n = name.lowercase()
        return thinkerWords.any { n.contains(it) } || thinkerRegex.containsMatchIn(n)
    }

    /**
     * Tez baat-cheet ke liye line: bina-soch wale, na bahut bhaari na bahut chhote (score 1 se 3) models,
     * chhota/halka pehle. Asli tez wala order baad mein naapi hui speed se banta hai (ModelRouter).
     */
    private fun fastOrder(providerId: String, all: List<String>, max: Int): List<String> {
        val noThink = all.filter { !isThinker(it) }.ifEmpty { all }
        val band = noThink.filter { score(providerId, it) in 1..3 }
        val pool = if (band.isNotEmpty()) band else noThink
        return pool.sortedWith(Comparator<String> { a, b ->
            val sa = score(providerId, a)
            val sb = score(providerId, b)
            if (sa != sb) sa.compareTo(sb) else versionOf(b).compareTo(versionOf(a))
        }).take(max)
    }

    fun isChatModel(providerId: String, name: String): Boolean {
        val n = name.lowercase().trim()
        if (n.isEmpty()) return false
        if (skip.any { n.contains(it) }) return false
        if (providerId == "openrouter" && !n.endsWith(":free")) return false
        // Gemini API par Gemma models system-instruction nahi leti, isliye yahan nahi
        if (providerId == "gemini" && n.contains("gemma")) return false
        return true
    }

    private fun versionOf(name: String): Double {
        val m = versionRegex.find(name) ?: return 0.0
        return m.groupValues[1].toDoubleOrNull() ?: 0.0
    }

    private fun score(providerId: String, name: String): Int {
        val n = name.lowercase()
        var s = ModelRank.score(n)
        // Gemini ke 'pro' models free key par aksar quota 0 / available nahi hote: sabse peeche
        // (flash aur flash-lite pehle chalenge, jo aam taur par free par chalte hain)
        if (providerId == "gemini" && n.contains("pro")) s -= 4
        // Preview/experimental models aksar kam bharosemand hote hain: thoda peeche
        if (n.contains("preview") || n.contains("exp")) s -= 2
        return s
    }

    /**
     * names = provider ki list. strongFirst=true: tagda model pehle, phir halka. false: halka/tez pehle.
     * max = ek provider ke kitne models (tier) rakhne hain.
     */
    fun order(providerId: String, names: List<String>, strongFirst: Boolean, max: Int): List<String> {
        val cands = names.map { it.trim() }.filter { isChatModel(providerId, it) }.distinct()
        if (!strongFirst) return fastOrder(providerId, cands, max)
        val cmp = Comparator<String> { a, b ->
            val sa = score(providerId, a)
            val sb = score(providerId, b)
            when {
                sa != sb -> if (strongFirst) sb.compareTo(sa) else sa.compareTo(sb)
                else -> versionOf(b).compareTo(versionOf(a)) // barabar ho to naya version pehle
            }
        }
        // Hamesha sabse behtar 'max' models chuno; "tez pehle" mein bas unhi ko ulta (halka pehle) laga do.
        // Isse bilkul chhote/bekaar models nahi chunte, sirf order badalta hai.
        val topStrong = cands.sortedWith(Comparator<String> { a, b ->
            val sa = score(providerId, a)
            val sb = score(providerId, b)
            if (sa != sb) sb.compareTo(sa) else versionOf(b).compareTo(versionOf(a))
        }).take(max)
        return if (strongFirst) topStrong else topStrong.sortedWith(cmp)
    }
}
