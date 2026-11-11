package com.jarvis.app

import java.util.Calendar

/**
 * Offline (local model) ke liye phone-control commands.
 * Chhota local model OPEN:/ACTION: format follow nahi kar paata, isliye aam commands
 * yahan rules se pehchaan kar seedha "ACTION: ..." line bana dete hain (model ke bina).
 * parse() null de to baat local model ko hi jaati hai.
 */
object OfflineCommands {

    private val callStop = setOf(
        "call", "karo", "kar", "do", "dena", "de", "ko", "ki", "ka", "ke", "lagao", "laga", "lagana",
        "mujhe", "mera", "meri", "please", "plz", "pls", "ek", "phone", "dial", "kijiye", "kardo",
        "krdo", "kro", "ab", "abhi", "jaldi", "se", "par", "pe", "mere", "hai", "jarvi", "jarvis"
    )

    private val musicStop = setOf(
        "gaana", "gana", "gaane", "song", "songs", "music", "chalao", "bajao", "play", "lagao", "laga",
        "do", "karo", "ka", "ki", "ko", "se", "mujhe", "sunao", "suna", "ek", "koi", "please", "plz",
        "kar", "de", "dena", "abhi", "jarvi", "jarvis"
    )

    fun parse(raw: String): String? {
        val base = raw.substringBefore("\n\n[").trim()
        if (base.isBlank() || base.startsWith("[")) return null
        if (CommandHandler.hasMultiTask(base)) return null

        val t = base.lowercase()
            .replace(Regex("[?!.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val words = t.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 9) return null

        fun has(vararg w: String): Boolean = w.any { t.contains(it) }
        fun hasWord(vararg w: String): Boolean = words.any { x -> w.any { it == x } }

        // ---- Settings ----
        if (has("setting")) {
            return when {
                has("wifi", "wi-fi", "वाईफाई") -> "ACTION: SETTINGS WIFI"
                has("bluetooth") -> "ACTION: SETTINGS BLUETOOTH"
                has("display", "brightness") -> "ACTION: SETTINGS DISPLAY"
                has("sound", "volume", "awaaz", "awaz") -> "ACTION: SETTINGS SOUND"
                has("location") -> "ACTION: SETTINGS LOCATION"
                has("airplane", "flight mode") -> "ACTION: SETTINGS AIRPLANE"
                has("battery") -> "ACTION: SETTINGS BATTERY"
                hasWord("apps", "app") -> "ACTION: SETTINGS APPS"
                else -> null
            }
        }

        // ---- Torch ----
        if (has("torch", "flashlight", "flash light", "टॉर्च")) {
            val off = has("off", "band", "bandh", "bujha", "बंद")
            return if (off) "ACTION: FLASH OFF" else "ACTION: FLASH ON"
        }

        // ---- Volume ----
        if (has("volume", "awaaz", "awaz", "आवाज")) {
            val num = Regex("(\\d{1,3})").find(t)?.groupValues?.get(1)?.toIntOrNull()
            return when {
                has("mute", "silent", "chup") -> "ACTION: VOLUME MUTE"
                has("max", "full", "poori", "puri", "sabse") -> "ACTION: VOLUME MAX"
                num != null && num in 0..100 -> "ACTION: VOLUME $num"
                has("badha", "zyada", "jyada", "tez", "increase") || hasWord("up") -> "ACTION: VOLUME UP"
                hasWord("kam", "ghata", "ghatao", "dheema", "dhima", "down") || has("decrease") -> "ACTION: VOLUME DOWN"
                else -> null
            }
        }

        // ---- Battery ----
        if (has("battery") && has("kitni", "kitna", "percent", "%", "level", "status", "bata", "charge", "kya")) {
            return "ACTION: BATTERY"
        }

        // ---- Notifications / lock / screenshot ----
        if (has("notification") && !has("band", "bandh", "off")) return "ACTION: NOTIFICATIONS"
        if (hasWord("lock") && !has("unlock") && has("phone", "screen", "karo", "kar do", "kardo")) return "ACTION: LOCK"
        if (has("screenshot", "screen shot") && !has("dekh")) return "ACTION: SCREENSHOT"

        // ---- Alarm ----
        if (has("alarm") && !has("band", "bandh", "cancel", "hata", "delete", "off")) {
            val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(t) ?: return null
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h > 23 || min > 59) return null
            val pm = Regex("\\bpm\\b").containsMatchIn(t) ||
                has("shaam", "sham", "dopahar", "evening")
            val night = has("raat", "night")
            val am = Regex("\\bam\\b").containsMatchIn(t) || has("subah", "savere", "morning")
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            fun delta(h24: Int): Int = ((h24 * 60 + min) - nowMin + 1440) % 1440
            val h24: Int = when {
                h >= 13 -> h
                night && h == 12 -> 0
                night && h in 1..5 -> h
                night && h in 6..11 -> h + 12
                pm && h < 12 -> h + 12
                pm -> 12
                am && h == 12 -> 0
                am -> h
                h == 0 -> 0
                else -> {
                    val a = h % 12
                    val b = a + 12
                    if (delta(a) <= delta(b)) a else b
                }
            }
            return String.format(java.util.Locale.ENGLISH, "ACTION: ALARM %02d:%02d", h24, min)
        }

        // ---- Timer ----
        if (has("timer") && !has("band", "bandh", "cancel", "hata", "delete")) {
            val m = Regex("(\\d+)\\s*(second|sec|minute|mint|min|ghanta|ghante|hour|hr)").find(t) ?: return null
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val unit = m.groupValues[2]
            val secs = when {
                unit.startsWith("sec") -> n
                unit.startsWith("min") -> n * 60
                else -> n * 3600
            }
            if (secs <= 0 || secs > 86400) return null
            return "ACTION: TIMER $secs"
        }

        // ---- Camera ----
        if (has("selfie")) return "ACTION: SELFIE"
        if (has("photo") && hasWord("le", "lo", "kheench", "kheencho", "khencho", "click", "capture", "khicho") &&
            !has("dekh", "gallery", "bhej")
        ) return "ACTION: PHOTO"

        // ---- Call ----
        if (hasWord("call", "dial", "कॉल") && !has("missed", "history", "record", "video", "log")) {
            val digits = t.filter { it.isDigit() }
            if (digits.length >= 6) return "ACTION: CALL $digits"
            val name = words.filter { it !in callStop }.joinToString(" ").trim()
            if (name.isBlank() || name.split(" ").size > 3) return null
            return "ACTION: CALL $name"
        }

        // ---- Music ----
        if (t.startsWith("play ")) {
            val q = t.removePrefix("play ").trim()
            if (q.isNotBlank()) return "ACTION: MUSIC $q"
        }
        if (has("gaana", "gana", "gaane", "song", "music") && has("chalao", "bajao", "play", "lagao", "sunao")) {
            val q = words.filter { it !in musicStop }.joinToString(" ").trim()
            if (q.isNotBlank()) return "ACTION: MUSIC $q"
        }

        return null
    }
}
