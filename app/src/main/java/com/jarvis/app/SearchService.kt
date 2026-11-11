package com.jarvis.app

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SearchResult(val hits: List<SearchHit>, val error: String, val provider: String)

/**
 * Internet search (Gemini ke alawa doosre providers ke liye).
 * Tavily/Brave: apni key chahiye. Wikipedia: bina key, par sirf encyclopedia jaankari (news/live nahi).
 */
object SearchService {

    const val TAVILY = "tavily"
    const val BRAVE = "brave"
    const val WIKI = "wikipedia"

    fun hasKeyedProvider(ctx: Context): Boolean =
        ApiKeyStore.hasSecret(ctx, "search_tavily") || ApiKeyStore.hasSecret(ctx, "search_brave")

    private fun chosen(ctx: Context): String {
        val pref = Prefs.searchProvider(ctx)
        if (pref == "off") return "off"
        if (pref == TAVILY && ApiKeyStore.hasSecret(ctx, "search_tavily")) return TAVILY
        if (pref == BRAVE && ApiKeyStore.hasSecret(ctx, "search_brave")) return BRAVE
        if (pref == WIKI) return WIKI
        // auto
        if (ApiKeyStore.hasSecret(ctx, "search_tavily")) return TAVILY
        if (ApiKeyStore.hasSecret(ctx, "search_brave")) return BRAVE
        return WIKI
    }

    fun search(ctx: Context, queryIn: String): SearchResult {
        val q = queryIn.trim().take(200)
        if (q.isBlank()) return SearchResult(emptyList(), "sawaal khali", "")
        val which = chosen(ctx)
        if (which == "off") return SearchResult(emptyList(), "search band hai (Settings)", "")
        if (!NetworkMonitor.isOnline(ctx)) return SearchResult(emptyList(), "internet nahi hai", which)
        return try {
            when (which) {
                TAVILY -> tavily(ctx, q)
                BRAVE -> brave(ctx, q)
                else -> wiki(q)
            }
        } catch (e: Exception) {
            SearchResult(emptyList(), "search error: " + OnlineParsers.redact(e.message ?: ""), which)
        }
    }

    private fun tavily(ctx: Context, q: String): SearchResult {
        val key = ApiKeyStore.getSecret(ctx, "search_tavily")
        val body = JSONObject().put("query", q).put("max_results", 5)
        val rq = Request.Builder().url("https://api.tavily.com/search")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        Net.api.newCall(rq).execute().use { r ->
            val raw = r.body?.string() ?: ""
            if (!r.isSuccessful) return SearchResult(emptyList(), "Tavily error " + r.code, TAVILY)
            return SearchResult(SearchFormat.parseTavily(raw), "", TAVILY)
        }
    }

    private fun brave(ctx: Context, q: String): SearchResult {
        val key = ApiKeyStore.getSecret(ctx, "search_brave")
        val url = "https://api.search.brave.com/res/v1/web/search?count=5&q=" + java.net.URLEncoder.encode(q, "UTF-8")
        val rq = Request.Builder().url(url).header("Accept", "application/json")
            .header("X-Subscription-Token", key).get().build()
        Net.api.newCall(rq).execute().use { r ->
            val raw = r.body?.string() ?: ""
            if (!r.isSuccessful) return SearchResult(emptyList(), "Brave error " + r.code, BRAVE)
            return SearchResult(SearchFormat.parseBrave(raw), "", BRAVE)
        }
    }

    private fun wiki(q: String): SearchResult {
        val lang = if (q.any { it in '\u0900'..'\u097F' }) "hi" else "en"
        val url = "https://" + lang + ".wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=4&srsearch=" +
            java.net.URLEncoder.encode(q, "UTF-8")
        val rq = Request.Builder().url(url).header("User-Agent", "JarviAI/2.0 (Android)").get().build()
        Net.api.newCall(rq).execute().use { r ->
            val raw = r.body?.string() ?: ""
            if (!r.isSuccessful) return SearchResult(emptyList(), "Wikipedia error " + r.code, WIKI)
            return SearchResult(SearchFormat.parseWikipedia(raw, lang), "", WIKI)
        }
    }
}
