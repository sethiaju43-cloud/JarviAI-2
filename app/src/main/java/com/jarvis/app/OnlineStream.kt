package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject

/** Online jawab shabd-shabd aane (streaming / SSE) ko padhne ke pure functions (testable) */
object OnlineStream {

    /** SSE ki ek line se "data:" ke baad ka hissa, warna null (comment/event line chhod do) */
    fun dataOf(line: String): String? {
        if (!line.startsWith("data:")) return null
        return line.substring(5).trim()
    }

    /** OpenAI-compatible: ek data chunk se naya text (khali ho sakta hai) */
    fun openAiDelta(data: String): String {
        if (data.isEmpty() || data == "[DONE]") return ""
        return try {
            val choices = JSONObject(data).optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
            when (val c = delta.opt("content")) {
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
        } catch (e: Exception) {
            ""
        }
    }

    /** Gemini: ek data chunk se naya text ("thought" wala hissa chhodkar) */
    fun geminiDelta(data: String): String {
        if (data.isEmpty()) return ""
        return try {
            val cands = JSONObject(data).optJSONArray("candidates") ?: return ""
            if (cands.length() == 0) return ""
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    /** Stream ke beech mein error object aaya (HTTP 200 ke baad bhi aa sakta hai)? Message ya null. */
    fun errorOf(data: String): String? {
        if (!data.startsWith("{")) return null
        return try {
            val e = JSONObject(data).opt("error") ?: return null
            when (e) {
                is JSONObject -> e.optString("message", "Error")
                is String -> e
                else -> null
            }
        } catch (ex: Exception) {
            null
        }
    }
}
