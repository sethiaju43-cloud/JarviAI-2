package com.jarvis.app

/*
 * URL ke baare mein safai ke check. Is file mein Android ka koi code nahi hai,
 * isliye ye JVM unit tests mein seedha test hota hai.
 */
object UrlGuard {

    /**
     * Kya ye link sach mein HuggingFace ka (https) hai?
     * Sirf host dekha jata hai, poora link nahi: "https://evil.com/?x=huggingface.co" ya
     * "https://huggingface.co.evil.com" HuggingFace nahi maane jaate.
     * Isse HuggingFace token kisi doosre server ko nahi bheja jata.
     */
    fun isHuggingFace(url: String): Boolean {
        return try {
            val u = java.net.URI(url.trim())
            if (u.scheme?.lowercase() != "https") return false
            val h = u.host?.lowercase() ?: return false
            h == "huggingface.co" || h.endsWith(".huggingface.co")
        } catch (e: Exception) {
            false
        }
    }
}
