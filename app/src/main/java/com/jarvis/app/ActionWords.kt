package com.jarvis.app

/**
 * Message mein phone ka koi kaam (kholo, chalao, click...) maanga gaya hai ya nahi.
 * "kaun banaya aur naam kya hai" jaise kai SAWAAL ko 'kai kaam' mat samjho: unka jawab dena hai, command nahi.
 */
object ActionWords {
    private val words = listOf(
        "khol", "open", "search", "dhundo", "dhundh", "type", "likho", "click", "dabao", "dabaao", "tap",
        "select", "play", "chalao", "chala do", "chala de", "laga do", "laga de", "lagao", "scroll", "call",
        "alarm", "timer", "photo", "selfie", "bhejo", "bhej do", "send", "fullscreen", "full screen", "bada",
        "back", "home", "recent", "volume", "torch", "flash", "wifi", "bluetooth", "screenshot", "lock",
        "खोल", "चलाओ", "चला दो", "लगा दो", "दबाओ", "भेजो", "स्क्रॉल", "कॉल", "अलार्म"
    )

    fun any(text: String): Boolean {
        val t = text.lowercase()
        return words.any { t.contains(it) }
    }
}
