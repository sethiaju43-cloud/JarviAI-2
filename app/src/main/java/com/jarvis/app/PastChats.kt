package com.jarvis.app

import android.content.Context

/**
 * Purani (archive ki hui) chats mein se abhi ke sawaal se milti-julti baatein dhundhna.
 * Sab phone mein hi hota hai. Sensitive lagne wali baatein (number, OTP...) kabhi nahi li jaati.
 * Poori history prompt mein nahi jaati, sirf 1-2 chhote tukde.
 */
object PastChats {

    /** Har chat ke messages (user ka sawaal, phir jawab, waqt ke order mein). k tukde lautata hai. */
    fun pick(query: String, chats: List<List<ChatMessage>>, k: Int): List<String> {
        val q = MemoryRanker.words(query)
        if (q.size < 2 || k <= 0) return emptyList()
        class Hit(val score: Int, val text: String)
        val hits = ArrayList<Hit>()
        for (chat in chats) {
            for (i in chat.indices) {
                val m = chat[i]
                if (!m.isUser) continue
                val w = MemoryRanker.words(m.text)
                var overlap = 0
                for (x in q) if (x in w) overlap++
                if (overlap < 2) continue
                val reply = if (i + 1 < chat.size && !chat[i + 1].isUser) chat[i + 1].text else ""
                val snip = "User ne kaha: " + m.text.replace("\n", " ").take(120) +
                    (if (reply.isNotBlank()) " / Jarvi ne: " + reply.replace("\n", " ").take(160) else "")
                if (SensitiveFilter.isSensitive(snip)) continue
                hits.add(Hit(overlap, snip))
            }
        }
        hits.sortByDescending { it.score }
        return hits.take(k).map { it.text }
    }

    /** Prompt mein jodne wali line. "" = kuch milti-julti baat nahi mili (ya memory band hai) */
    fun line(ctx: Context, query: String): String {
        if (!Prefs.memoryEnabled(ctx)) return ""
        val chats = try {
            ChatStore.listArchive(ctx).map { it.msgs }
        } catch (e: Exception) {
            return ""
        }
        val picks = pick(query, chats, 2)
        if (picks.isEmpty()) return ""
        return "Purani baat-cheet se kuch milti-julti baatein (sirf kaam aaye to naturally use karo, " +
            "pakka yaad hone ka dawa mat karo): " + picks.joinToString(" | ") + ". "
    }
}
