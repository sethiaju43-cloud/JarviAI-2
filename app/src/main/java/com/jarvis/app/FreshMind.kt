package com.jarvis.app

import android.content.Context

/**
 * Fresh Mind Mode: koi jaadui upgrade nahi. Ye baat-cheet ko saaf-suthra summarise karta hai,
 * kaam ke facts alag karta hai, asli sawaal dobara dekhta hai, pichle jawab mein galti dhundta hai,
 * vikalp tolta hai aur behtar jawab deta hai.
 */
object FreshMind {

    class Result(val ok: Boolean, val text: String, val summary: String?)

    private const val PROMPT =
        "Neeche user ke saath hui baat-cheet hai. Isko naye sire se, saaf dimaag se dekhkar 'Fresh Mind' karo. " +
        "Ye 7 hisse likho (har ek ka chhota heading rakho, simple bhasha mein):\n" +
        "1. SAAR: abhi ka kaam ya problem 2-3 line mein.\n" +
        "2. ZAROORI BAATEN: sirf wo facts jo kaam ke hain.\n" +
        "3. FALTU: kaun si baatein ab kaam ki nahi.\n" +
        "4. ASLI SAWAAL: original problem ko dobara apne shabdon mein likho.\n" +
        "5. PICHLE JAWAB MEIN GALTI?: pichle jawab ko jaanch kar batao ki koi galti, contradiction ya adhoori baat hai ya nahi. " +
        "Pakka na ho to 'pakka nahi' likho.\n" +
        "6. VIKALP: 2-3 alag tareeke aur har ek ka risk.\n" +
        "7. BEHTAR JAWAB: ab improved final jawab.\n" +
        "Sabse aakhir mein nayi line par sirf ye likho: SAVE: <updated summary, max 3 chhote vaakya>\n" +
        "Apna chhupa hua chain-of-thought mat likho, sirf in headings ke jawab do. Jo pakka nahi pata wo saaf bolo. " +
        "Star ya hash symbols mat likho."

    private val saveRegex = Regex("(?m)^\\s*SAVE:\\s*(.+)$")

    private fun transcript(msgs: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (m in msgs) {
            sb.append(if (m.isUser) "USER: " else "JARVI: ").append(m.text.take(700)).append("\n")
        }
        return sb.toString().takeLast(6000)
    }

    /** Peeche ke thread par bulao */
    fun run(ctx: Context): Result {
        val msgs = ChatStore.load(ctx).filter { it.text != "..." }.takeLast(16)
        if (msgs.count { it.isUser } == 0) {
            return Result(false, "Abhi koi baat-cheet nahi hai. Pehle kuch poochho, phir Fresh Mind dabao 🙂", null)
        }
        val facts = MemoryStore.contextLine(ctx, msgs.lastOrNull { it.isUser }?.text ?: "")
        val user = PROMPT + "\n\n" + facts + "\n[BAAT-CHEET]\n" + transcript(msgs)
        val sysOn = Prompts.chatOnly(ctx, Task.REASONING, "", "")
        val sysLoc = Prompts.local(ctx, Task.REASONING, "", "")
        val r = Brain.rawAsk(ctx, Task.REASONING, sysOn, sysLoc, listOf(Pair("user", user)))
        if (!r.ok) return Result(false, r.text, null)

        var text = Honesty.cleanMarkdown(r.text)
        var summary: String? = null
        val m = saveRegex.find(text)
        if (m != null) {
            summary = m.groupValues[1].trim()
            text = text.replace(saveRegex, "").trim()
        }
        val issues = ArithmeticChecker.find(text)
        if (issues.isNotEmpty()) text += "\n\n" + Honesty.correctionNote(issues)
        if (Honesty.hasCode(text)) text += "\n\n" + Honesty.CODE_NOTICE
        val route = Brain.lastRoute
        if (route?.backend?.isLocal == true) text += "\n\n" + Honesty.OFFLINE_NOTICE

        // Context saaf: lambi baat ki jagah summary
        if (!summary.isNullOrBlank()) {
            Brain.replaceWorking(ctx, summary, text.take(450))
        }
        return Result(true, "🧠 Fresh Mind\n\n" + text, summary)
    }
}
