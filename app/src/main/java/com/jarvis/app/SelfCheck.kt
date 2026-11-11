package com.jarvis.app

import kotlin.math.abs

/** Calculation ki galti pakadne wala (jawab mein "a + b = c" jaisi line jaanchta hai) */
object ArithmeticChecker {

    class Issue(val expr: String, val claimed: Double, val actual: Double)

    private class P(val s: String) {
        var pos = 0

        fun ws() {
            while (pos < s.length && s[pos] == ' ') pos++
        }

        fun expr(): Double {
            var v = term()
            while (true) {
                ws()
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                    val op = s[pos++]
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else break
            }
            return v
        }

        fun term(): Double {
            var v = factor()
            while (true) {
                ws()
                if (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                    val op = s[pos++]
                    val r = factor()
                    if (op == '/') {
                        if (r == 0.0) throw ArithmeticException("div0")
                        v /= r
                    } else v *= r
                } else break
            }
            return v
        }

        fun factor(): Double {
            ws()
            if (pos >= s.length) throw IllegalArgumentException("end")
            val c = s[pos]
            if (c == '-') {
                pos++
                return -factor()
            }
            if (c == '+') {
                pos++
                return factor()
            }
            if (c == '(') {
                pos++
                val v = expr()
                ws()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("paren")
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("num")
            return s.substring(start, pos).toDouble()
        }
    }

    fun eval(expr: String): Double? {
        return try {
            val p = P(expr)
            val v = p.expr()
            p.ws()
            if (p.pos != expr.length) null
            else if (v.isNaN() || v.isInfinite()) null
            else v
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(t: String): String {
        var s = t.replace("×", "*").replace("÷", "/").replace("−", "-")
        s = s.replace(Regex("(?<=[0-9)])\\s*[xX]\\s*(?=[0-9(])"), "*")
        s = s.replace(Regex("(?<=[0-9]),(?=[0-9]{3}(?![0-9]))"), "")
        return s
    }

    private val claimRegex =
        Regex("=\\s*(-?[0-9]+(?:\\.[0-9]+)?)(?![0-9]|\\.[0-9]|\\s*[-+*/(])")
    private const val ALLOWED = "0123456789+-*/(). "

    fun find(textIn: String): List<Issue> {
        val text = normalize(textIn)
        val out = ArrayList<Issue>()
        for (m in claimRegex.findAll(text)) {
            val claimedStr = m.groupValues[1]
            val claimed = claimedStr.toDoubleOrNull() ?: continue
            val from = (m.range.first - 80).coerceAtLeast(0)
            val before = text.substring(from, m.range.first)
            var st = before.length
            while (st > 0 && ALLOWED.indexOf(before[st - 1]) >= 0) st--
            val cand = before.substring(st)
            var found: Pair<String, Double>? = null
            for (i in cand.indices) {
                val c = cand[i]
                val startOk = c.isDigit() || c == '(' || (c == '-' && i + 1 < cand.length && cand[i + 1].isDigit())
                if (!startOk) continue
                if (i > 0 && cand[i - 1] != ' ' && cand[i - 1] != '(') continue
                val piece = cand.substring(i).trim()
                if (!Regex("[0-9)]\\s*[-+*/]").containsMatchIn(piece)) continue
                val v = eval(piece) ?: continue
                found = Pair(piece, v)
                break
            }
            val f = found ?: continue
            val decimals = claimedStr.substringAfter('.', "").length
            val tol = 0.5 * Math.pow(10.0, -decimals.toDouble())
            if (abs(f.second - claimed) >= tol) {
                out.add(Issue(f.first, claimed, f.second))
            }
        }
        return out
    }

    fun fmt(v: Double): String {
        return if (v == Math.floor(v) && abs(v) < 1e12) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
    }
}

/** <think>...</think> jaisa andar ka sochna jawab se hata do */
object ThinkStripper {
    private const val OPEN = "<think>"
    private const val CLOSE = "</think>"

    fun strip(raw: String): String {
        var t = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = t.indexOf(OPEN)
        if (open >= 0) t = t.substring(0, open)
        val close = t.lastIndexOf(CLOSE)
        if (close >= 0) t = t.substring(close + CLOSE.length)
        for (k in OPEN.length - 1 downTo 1) {
            if (t.endsWith(OPEN.substring(0, k))) {
                t = t.substring(0, t.length - k)
                break
            }
        }
        return t.trim()
    }
}

/** Imaandaari wale notice, markdown saaf karna, bolne ke liye text banana */
object Honesty {
    private const val WARN = "\u26A0\uFE0F"
    private const val INFO = "\u2139\uFE0F"

    const val CODE_NOTICE =
        WARN + " Ye code maine yahan chalakar test nahi kiya. Chalane se pehle ek baar khud check kar lena."
    const val OFFLINE_NOTICE =
        INFO + " Offline hun: ye jawab local model ki apni knowledge par hai, latest ya pakka na bhi ho sakta hai."
    const val NO_SEARCH_NOTICE =
        WARN + " Live search nahi ho paya, ye jawab model ki purani knowledge par hai."

    fun hasCode(text: String): Boolean = text.contains("```")

    fun sourcesBlock(sources: List<Source>): String {
        if (sources.isEmpty()) return ""
        val sb = StringBuilder("\n\nSources:")
        var n = 1
        val seen = HashSet<String>()
        for (s in sources) {
            if (s.url.isBlank() || !seen.add(s.url)) continue
            val title = if (s.title.isBlank()) s.url else s.title
            sb.append("\n").append(n).append(". ").append(title.take(80)).append(" - ").append(s.url)
            n++
            if (n > 5) break
        }
        return if (n == 1) "" else sb.toString()
    }

    fun correctionNote(issues: List<ArithmeticChecker.Issue>): String {
        val sb = StringBuilder(WARN + " Calculation check: ")
        for ((i, iss) in issues.withIndex()) {
            if (i > 0) sb.append("; ")
            sb.append(iss.expr.trim()).append(" = ").append(ArithmeticChecker.fmt(iss.actual))
                .append(" hota hai (upar ").append(ArithmeticChecker.fmt(iss.claimed)).append(" likha tha)")
        }
        return sb.toString()
    }

    fun forSpeech(text: String): String {
        var t = text
        val si = t.indexOf("\n\nSources:")
        if (si >= 0) t = t.substring(0, si)
        t = t.replace(Regex("```[\\s\\S]*?```"), " (code chat mein likha hai) ")
        t = t.lines().filter {
            val s = it.trim()
            !(s.startsWith("\u26A0") || s.startsWith("\u2139"))
        }.joinToString("\n")
        return t.trim()
    }

    private fun cleanPlain(p: String): String {
        var t = p
        t = t.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        t = t.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        t = t.replace(Regex("(?m)^(\\s*)[*-]\\s+"), "$1\u2022 ")
        return t
    }

    fun cleanMarkdown(text: String): String {
        if (!text.contains("**") && !text.contains("#") && !text.contains("* ") && !text.contains("- ")) {
            return text
        }
        val parts = text.split("```")
        val sb = StringBuilder()
        for (i in parts.indices) {
            if (i > 0) sb.append("```")
            sb.append(if (i % 2 == 0) cleanPlain(parts[i]) else parts[i])
        }
        return sb.toString()
    }

    private val correctionWords = listOf(
        "galat", "ghalat", "wrong", "incorrect", "not correct", "sahi nahi", "sahi nahin",
        "galti", "mistake", "not right", "गलत", "ग़लत", "सही नहीं", "गलती",
        "jhooth", "jhuth", "झूठ", "not true", "that's not", "thats not",
        "aisa nahi hai", "aisa nahin hai", "ऐसा नहीं है", "nahi nahi", "nahin nahin"
    )

    /** User bol raha hai ki pichla jawab galat tha? */
    fun isUserCorrection(text: String): Boolean {
        if (text.length > 220) return false
        val low = text.lowercase()
        return correctionWords.any { low.contains(it) }
    }

    /** Pichli ek baat-cheet: user ka sawaal aur Jarvi ka jawab */
    class Exchange(val user: String, val answer: String)

    /** history (user/model, text) mein se sabse nayi sawaal-jawab jodi nikaalo, na mile to null */
    fun lastExchange(history: List<Pair<String, String>>): Exchange? {
        var i = history.size - 1
        while (i >= 0 && history[i].first != "model") i--
        if (i < 0) return null
        var j = i - 1
        while (j >= 0 && history[j].first != "user") j--
        if (j < 0) return null
        return Exchange(history[j].second, history[i].second)
    }

    /**
     * User ke "galat" kehne par model ko di jaane wali hidayat (ek hi AI call mein, doosra call nahi).
     * Pichla sawaal aur jawab saath mein diya jata hai taaki model ko andaaza na lagana pade.
     */
    fun correctionRequest(ex: Exchange?): String {
        val sb = StringBuilder("\n\n[NOTE: User tumhare pichle jawab par sawaal utha raha hai.")
        if (ex != null) {
            sb.append(" Pichla sawaal: \"").append(ex.user.take(500)).append("\".")
            sb.append(" Tumhara pichla jawab: \"").append(ex.answer.take(900)).append("\".")
        }
        sb.append(" Karna ye hai: (1) Pehle pichla jawab sach mein dobara jaancho (tathya, calculation, matlab). ")
        sb.append("(2) Agar galti thi: ek baar saaf maano, batao kya galat tha aur kyun, phir sahi jawab do. ")
        sb.append("(3) Agar jawab sahi tha: pyaar se wajah ke saath samjhao; sirf user ke kehne par 'haan galti thi' mat bolo. ")
        sb.append("(4) Agar samajh nahi aa raha ki user kaun sa hissa galat keh raha hai, ya tumhe khud pakka nahi pata: ")
        sb.append("ek chhota sawaal poochho ya saaf bolo ki pakka nahi pata, koi tathya gadho mat. ")
        sb.append("(5) Baar baar maafi mat maango.]")
        return sb.toString()
    }

    /**
     * Bolne ke liye text: notice/sources/code hatao, emoji aur symbols hatao,
     * web link ko sirf 'link' bolo, nayi line par thoda ruko.
     * Khali string lautaye to bolne ko kuch nahi hai.
     */
    fun spokenText(text: String): String {
        var t = forSpeech(text)
        t = t.replace(Regex("https?://\\S+"), " link ")
        t = t.replace(Regex("[\\p{So}\\p{Cn}\\uFE0F\\u200D]"), " ")
        t = t.replace("*", "").replace("#", "").replace("_", " ")
        t = t.replace(Regex("(?m)^[ \\t]*[\u2022\\-][ \\t]+"), "")
        t = t.replace(Regex("([.!?\u0964,:;])[ \\t]*\\n+[ \\t]*"), "$1 ")
        t = t.replace(Regex("[ \\t]*\\n+[ \\t]*"), ". ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    /** Jawab sirf galti/dikkat ka ho to bolne ke liye chhoti, aam bhasha wali line (technical text nahi) */
    const val SPOKEN_ERROR = "Abhi jawab nahi mil paya. Thodi der baad dobara bolo."
}
