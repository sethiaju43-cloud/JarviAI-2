package com.jarvis.app

import kotlin.math.sqrt

/** Memory ki ek entry. kind: pref (pasand), project, fact, summary */
class MemoryEntry(
    val id: String,
    var text: String,
    val kind: String,
    val createdAt: Long,
    val auto: Boolean
)

/** Sensitive (nijji) jaankari pehchano: aisi cheez bina permission ke permanent memory mein nahi jayegi */
object SensitiveFilter {

    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private val keywords = listOf(
        "password", "passcode", "otp", "pin number", "my pin", "cvv", "aadhaar", "aadhar", "adhar",
        "pan card", "pan number", "account number", "ifsc", "upi id", "credit card", "debit card",
        "card number", "ssn", "passport", "पासवर्ड", "ओटीपी", "आधार", "खाता संख्या", "पिन",
        "home address", "ghar ka pata", "घर का पता", "my address",
        "diabetes", "cancer", "depression", "hiv", "bimari", "बीमारी", "religion", "dharm", "धर्म"
    )

    fun reasons(text: String): List<String> {
        val out = ArrayList<String>()
        val low = text.lowercase()
        val digits = low.replace(Regex("[\\s\\-]"), "")
        if (Regex("[0-9०-९]{8,}").containsMatchIn(digits)) out.add("lamba number (phone/ID/card jaisa)")
        if (emailRegex.containsMatchIn(text)) out.add("email address")
        for (k in keywords) {
            if (low.contains(k)) {
                out.add("'" + k + "' se judi jaankari")
                break
            }
        }
        return out
    }

    fun isSensitive(text: String): Boolean = reasons(text).isNotEmpty()
}

/** Sawaal se milti-julti saved yaadein chunna */
object MemoryRanker {
    private val splitter = Regex("[^\\p{L}\\p{M}\\p{Nd}]+")
    private val stop = setOf(
        "the", "is", "a", "an", "of", "to", "and", "in", "on", "for", "me", "my", "i", "you",
        "hai", "ka", "ki", "ke", "ko", "se", "mein", "me", "ye", "wo", "to", "aur", "kya", "hain",
        "tha", "thi", "kar", "do", "na", "ek", "user", "है", "का", "की", "के", "को", "से", "में",
        "और", "क्या"
    )

    fun words(s: String): Set<String> =
        s.lowercase().split(splitter).filter { it.length > 1 && it !in stop }.toSet()

    fun rank(query: String, entries: List<MemoryEntry>, k: Int): List<MemoryEntry> {
        if (entries.isEmpty() || k <= 0) return emptyList()
        val q = words(query)
        val scored = ArrayList<Pair<MemoryEntry, Double>>()
        for (e in entries) {
            val w = words(e.text)
            var overlap = 0
            for (x in q) if (x in w) overlap++
            var score = if (w.isEmpty()) 0.0 else overlap / sqrt(w.size.toDouble())
            if (e.kind == "pref") score += 0.6
            if (score > 0.0) scored.add(Pair(e, score))
        }
        scored.sortWith(compareByDescending<Pair<MemoryEntry, Double>> { it.second }
            .thenByDescending { it.first.createdAt })
        return scored.take(k).map { it.first }
    }
}

/** "yaad rakho ki ..." jaise seedhe commands */
object ExplicitMemory {
    private val patterns = listOf(
        Regex("(?i)^\\s*(?:jarvi\\s+|jarvis\\s+)?yaad\\s+rakh(?:o|na|iye)?\\s+(?:ki\\s+)?(.{4,})$"),
        Regex("(?i)^\\s*(?:jarvi\\s+|jarvis\\s+)?remember\\s+(?:that\\s+)?(.{4,})$"),
        Regex("^\\s*याद\\s+रख(?:ो|ना|िए)?\\s+(?:कि\\s+)?(.{4,})$")
    )

    fun extract(text: String): String? {
        val t = text.trim().trimEnd('.', '!', '।')
        for (p in patterns) {
            val m = p.find(t)
            if (m != null) return m.groupValues[1].trim()
        }
        return null
    }
}
