package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject

class SearchHit(val title: String, val url: String, val snippet: String)

/** API ke jawab padhne aur request banane ke pure functions (testable) */
object OnlineParsers {

    private val keyLike = Regex("[A-Za-z0-9_\\-]{20,}")

    /** Error message mein kahin key jaisa lamba token ho to chhupa do */
    fun redact(s: String): String = s.replace(keyLike, "***")

    fun errorMessage(raw: String, code: Int): String {
        var msg = ""
        try {
            val o = JSONObject(raw)
            val e = o.opt("error")
            msg = when (e) {
                is JSONObject -> e.optString("message", "")
                is String -> e
                else -> o.optString("message", "")
            }
        } catch (e: Exception) {
        }
        if (msg.isBlank()) msg = "Error $code"
        return redact(msg).take(300)
    }

    fun retryAfter(header: String?): Int = header?.trim()?.toIntOrNull() ?: 0

    fun parseGemini(raw: String): Pair<String, List<Source>> {
        try {
            val cand = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            val parts = cand.getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            val src = ArrayList<Source>()
            val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
            if (chunks != null) {
                for (i in 0 until chunks.length()) {
                    val web = chunks.getJSONObject(i).optJSONObject("web") ?: continue
                    val uri = web.optString("uri", "")
                    if (uri.isNotBlank()) src.add(Source(web.optString("title", ""), uri))
                }
            }
            return Pair(sb.toString().trim(), src)
        } catch (e: Exception) {
            return Pair("", emptyList())
        }
    }

    fun parseOpenAi(raw: String): String {
        try {
            val msg = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val c = msg.opt("content")
            val text = when (c) {
                is String -> c
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until c.length()) {
                        val o = c.optJSONObject(i) ?: continue
                        sb.append(o.optString("text", ""))
                    }
                    sb.toString()
                }
                else -> ""
            }
            return ThinkStripper.strip(text)
        } catch (e: Exception) {
            return ""
        }
    }

    fun parseGeminiModels(raw: String): List<String> {
        val out = ArrayList<String>()
        try {
            val arr = JSONObject(raw).optJSONArray("models") ?: return out
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = methods == null
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "generateContent") ok = true
                    }
                }
                if (ok) out.add(m.optString("name", "").removePrefix("models/"))
            }
        } catch (e: Exception) {
        }
        return out.filter { it.isNotBlank() }
    }

    fun parseOpenAiModels(raw: String): List<String> {
        val out = ArrayList<String>()
        try {
            val arr = JSONObject(raw).optJSONArray("data") ?: return out
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank()) out.add(id)
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun buildOpenAiMessages(
        system: String,
        history: List<Pair<String, String>>,
        imageB64: String?
    ): JSONArray {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for (idx in history.indices) {
            val role = if (history[idx].first == "model") "assistant" else "user"
            val text = history[idx].second
            if (imageB64 != null && idx == history.size - 1) {
                val content = JSONArray()
                content.put(JSONObject().put("type", "text").put("text", text))
                content.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$imageB64")
                    )
                )
                msgs.put(JSONObject().put("role", role).put("content", content))
            } else {
                msgs.put(JSONObject().put("role", role).put("content", text))
            }
        }
        return msgs
    }

    fun buildGeminiBody(
        history: List<Pair<String, String>>,
        system: String,
        useSearch: Boolean,
        image: String?
    ): JSONObject {
        val contents = JSONArray()
        for (idx in history.indices) {
            val parts = JSONArray()
            parts.put(JSONObject().put("text", history[idx].second))
            if (image != null && idx == history.size - 1) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", "image/jpeg").put("data", image)
                    )
                )
            }
            contents.put(JSONObject().put("role", history[idx].first).put("parts", parts))
        }
        val body = JSONObject()
            .put("contents", contents)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put(
                "generationConfig",
                JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            )
        if (useSearch) {
            body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
        return body
    }
}

object SearchFormat {

    fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").trim()

    fun promptBlock(hits: List<SearchHit>): String {
        val sb = StringBuilder()
        for ((i, h) in hits.withIndex()) {
            sb.append("[").append(i + 1).append("] ").append(h.title.take(120)).append("\n")
            sb.append(h.url).append("\n")
            sb.append(h.snippet.take(350)).append("\n\n")
        }
        return sb.toString().trim()
    }

    fun toSources(hits: List<SearchHit>): List<Source> = hits.map { Source(it.title, it.url) }

    fun parseTavily(raw: String): List<SearchHit> {
        val out = ArrayList<SearchHit>()
        try {
            val arr = JSONObject(raw).optJSONArray("results") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isNotBlank()) out.add(SearchHit(o.optString("title", ""), url, o.optString("content", "")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun parseBrave(raw: String): List<SearchHit> {
        val out = ArrayList<SearchHit>()
        try {
            val arr = JSONObject(raw).optJSONObject("web")?.optJSONArray("results") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isNotBlank()) {
                    out.add(SearchHit(stripHtml(o.optString("title", "")), url, stripHtml(o.optString("description", ""))))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun parseWikipedia(raw: String, lang: String): List<SearchHit> {
        val out = ArrayList<SearchHit>()
        try {
            val arr = JSONObject(raw).optJSONObject("query")?.optJSONArray("search") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title", "")
                if (title.isBlank()) continue
                val url = "https://" + lang + ".wikipedia.org/wiki/" + java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
                out.add(SearchHit(title, url, stripHtml(o.optString("snippet", ""))))
            }
        } catch (e: Exception) {
        }
        return out
    }
}
