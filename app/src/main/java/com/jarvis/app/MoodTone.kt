package com.jarvis.app

/*
 * User ke message se uska mood aur sawaal ki gehraai ka ANDAAZA lagata hai (sirf phone par, bina internet, bina API).
 * Is file mein Android ka koi code nahi hai, isliye ye JVM unit tests mein seedha test hota hai.
 *
 * Ye keyword aur chinh (emoji, !!, ??) par chalta hai, isliye galat bhi ho sakta hai. Isiliye prompt mein
 * isse sirf "hint" ki tarah diya jata hai, pakki baat ki tarah nahi. Ye AI ke asli jazbaat ka dawa nahi hai:
 * sirf jawab ka lehja user ke hisaab se badalta hai.
 */

enum class Mood { NEUTRAL, HAPPY, EXCITED, FRUSTRATED, SAD, CONFUSED, URGENT }

enum class Depth { SHORT, NORMAL, DEEP, TECHNICAL }

class Tone(val mood: Mood, val depth: Depth)

object MoodTone {

    /** Shabd ke shuru se match (stem chalta hai: "confus" se "confused" bhi pakda jata hai) */
    private fun words(vararg w: String): List<Regex> =
        w.map { Regex("(?<![a-z0-9])" + Regex.escape(it)) }

    /** Poora shabd match (chhote shabd jo kisi doosre shabd ke shuru mein aate hain, jaise "sad" aur "sadak") */
    private fun exact(vararg w: String): List<Regex> =
        w.map { Regex("(?<![a-z0-9])" + Regex.escape(it) + "(?![a-z0-9])") }

    private val sad = words(
        "udaas", "udas", "dukhi", "ro raha", "ro rahi", "akela", "akeli", "tanha", "depress",
        "dil toot", "bura lag raha", "feeling low", "miss kar", "man nahi lag", "तनहा", "उदास", "दुखी", "अकेला", "अकेली"
    ) + exact("sad", "rona")
    private val urgent = words(
        "jaldi", "turant", "abhi ke abhi", "urgent", "emergency", "fatafat", "quickly", "जल्दी", "तुरंत"
    ) + exact("asap")
    private val frustrated = words(
        "bakwas", "faltu", "pagal", "kaam nahi kar", "nahi chal raha", "kyu nahi", "kyun nahi", "phir se galat",
        "phir galat", "bekaar", "tang aa gaya", "tang aa gayi", "irritat", "annoy", "stupid", "useless", "not working",
        "wtf", "kitni baar", "gussa", "pareshan", "hadd hai", "गुस्सा", "बकवास", "परेशान", "बेकार"
    )
    private val confused = words(
        "samajh nahi", "samjha nahi", "samjhi nahi", "confus", "matlab kya", "kya matlab", "kaise karte", "kuch samajh",
        "समझ नहीं", "मतलब क्या"
    )
    private val excited = words(
        "kamaal", "zabardast", "amazing", "superb", "jeet gaya", "jeet gayi", "pass ho gaya", "pass ho gayi",
        "selection ho gaya", "selection ho gayi", "job lag gayi", "job lag gaya", "कमाल", "ज़बरदस्त", "जबरदस्त"
    ) + exact("wow", "yay")
    private val happy = words(
        "khush", "badhiya", "shukriya", "thanks", "thank you", "dhanyavaad", "dhanyawad", "maza aa", "achha laga",
        "accha laga", "love it", "thank u", "शुक्रिया", "धन्यवाद", "खुश", "बढ़िया"
    ) + exact("mast", "great")
    private val technical = words(
        "error", "exception", "gradle", "kotlin", "python", "api key", "function", "compile", "stack trace",
        "crash", "null pointer", "build fail", "github", "android studio", "database"
    ) + exact("java", "bug", "bugs", "json", "sql", "code", "codes", "coding", "server")
    private val deep = words(
        "research", "detail", "poori jaankari", "puri jaankari", "vistaar", "step by step", "step-by-step", "gehrai",
        "kaise kaam karta", "kaise kaam karti", "samjhao", "explain", "विस्तार", "समझाओ"
    )

    private fun has(t: String, list: List<Regex>): Boolean = list.any { it.containsMatchIn(t) }

    private fun emojiHas(t: String, vararg e: String): Boolean = e.any { t.contains(it) }

    fun detect(text: String): Tone {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return Tone(Mood.NEUTRAL, Depth.NORMAL)

        val mood = when {
            has(t, sad) || emojiHas(t, "😢", "😭", "💔", "😞") -> Mood.SAD
            has(t, urgent) -> Mood.URGENT
            has(t, frustrated) || emojiHas(t, "😡", "🤬", "😤") -> Mood.FRUSTRATED
            has(t, confused) || emojiHas(t, "😕", "🤔") -> Mood.CONFUSED
            has(t, excited) || t.contains("!!") -> Mood.EXCITED
            has(t, happy) || emojiHas(t, "😊", "😍", "🥰", "😄", "😁", "❤") -> Mood.HAPPY
            else -> Mood.NEUTRAL
        }

        val wordCount = t.split(Regex("\\s+")).size
        val depth = when {
            has(t, technical) -> Depth.TECHNICAL
            has(t, deep) -> Depth.DEEP
            wordCount <= 4 && mood == Mood.NEUTRAL -> Depth.SHORT
            else -> Depth.NORMAL
        }
        return Tone(mood, depth)
    }

    /** Prompt mein jodne wali line. Neutral + normal par khali (kuch nahi jodta). */
    fun promptLine(tone: Tone): String {
        val sb = StringBuilder()
        when (tone.mood) {
            Mood.HAPPY -> sb.append("User khush lag raha hai: tum bhi halke, positive aur natural andaaz mein jawab do. ")
            Mood.EXCITED -> sb.append("User josh mein lag raha hai: tum bhi utsaah se jawab do (ek se zyada emoji nahi). ")
            Mood.FRUSTRATED -> sb.append(
                "User pareshan ya jhunjhlaya hua lag raha hai: shaant raho, bahas ya lecture mat do, " +
                    "pehle ek chhoti si samajhdari wali line, phir seedha masle ka hal batao. "
            )
            Mood.SAD -> sb.append(
                "User udaas lag raha hai: pehle pyaar se uski baat suno aur halki hamdardi dikhao, " +
                    "turant salah ya lambi list mat do, mazaak mat karo. "
            )
            Mood.CONFUSED -> sb.append("User uljha hua lag raha hai: aasan bhasha mein chhote numbered steps mein, ek ek karke samjhao. ")
            Mood.URGENT -> sb.append("User ko jaldi lag rahi hai: sabse pehle seedha jawab ya kaam, uske baad sirf zaroori baat, bahut chhota rakho. ")
            Mood.NEUTRAL -> {}
        }
        when (tone.depth) {
            Depth.TECHNICAL -> sb.append("Ye technical sawaal hai: sahi technical shabd use karo, seedha aur precise raho. ")
            Depth.DEEP -> sb.append("User gehri jaankari chahta hai: poori tarah, saboot ya wajah ke saath samjhao. ")
            Depth.SHORT -> sb.append("Sawaal chhota hai: 1 ya 2 vaakya mein jawab do. ")
            Depth.NORMAL -> {}
        }
        if (sb.isEmpty()) return ""
        return "[Lehje ka hint, ye sirf andaaza hai, user ko ye line mat batana: " + sb.toString().trim() + "] "
    }

    /** Seedha: user ke text se prompt ki line */
    fun line(userText: String): String = promptLine(detect(userText))
}
