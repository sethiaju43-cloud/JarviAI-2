package com.jarvis.app

/**
 * Models ki line (tier). Har provider ka apna Model box hota hai (Providers screen), jaise:
 *   gemini-3.8-flash, gemini-3.5-flash-lite, gemini-3.1-flash-lite
 * Box mein pehla naam = tier 1, doosra = tier 2, teesra = tier 3.
 *
 * Router ka order TIER ke hisaab se hota hai:
 *   tier 1 : Gemini#1 -> Groq#1 -> OpenRouter#1 -> ...  (jo jo configured ho)
 *   tier 2 : Gemini#2 -> Groq#2 -> OpenRouter#2 -> ...
 *   tier 3 : ...
 * Jis model ka limit/quota khatam ho wo rest par jata hai (RouterCore ka cooldown) aur agla try hota hai.
 */
object ModelChain {

    fun parse(raw: String, defaults: List<String>): List<String> {
        val items = raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            items.isEmpty() -> defaults.distinct()
            items.size == 1 -> (items + defaults).distinct()
            else -> items.distinct() // user ne apni poori line di hai, use waisa hi maano
        }
    }

    /** Router/health mein har (provider, model) ki alag pehchaan */
    fun key(providerId: String, model: String): String = providerId + "|" + model

    /**
     * Tier ke hisaab se milao: pehle sabhi providers ka #1, phir sabhi ka #2, ...
     * Providers ka order har tier mein wahi rehta hai jo input mein tha.
     * Input: (providerId, uske models ki line). Output: (providerId, model) is order mein.
     */
    fun interleave(chains: List<Pair<String, List<String>>>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var maxN = 0
        for (c in chains) if (c.second.size > maxN) maxN = c.second.size
        for (tier in 0 until maxN) {
            for (c in chains) {
                if (tier < c.second.size) out.add(Pair(c.first, c.second[tier]))
            }
        }
        return out
    }
}
