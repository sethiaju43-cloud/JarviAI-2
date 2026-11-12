package com.jarvis.app

import android.content.Context

/**
 * Seedhe-steps wali skill (bina AI ke, turant chalti hai).
 * Skill ke "Kya karna hai" mein agar har step OPEN: ya ACTION: se shuru hota hai (steps ';' ya nayi line se alag),
 * aur user ne bilkul wahi "Jab main bolun" wala vaakya bola, to steps seedha chala diye jaate hain.
 * Warna skill pehle ki tarah AI ko hint ban kar jaati hai.
 */
object MacroRunner {

    @Volatile
    private var running = false

    @Volatile
    private var stopFlag = false

    private val filler = setOf(
        "jarvi", "jarvis", "please", "plz", "pls", "zara", "ek", "bhai", "yaar", "na", "hey", "ji"
    )

    private fun norm(s: String): String {
        val words = s.lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{Nd}]+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() && it !in filler }
        return words.joinToString(" ")
    }

    /** Skill ke steps agar sab OPEN:/ACTION: wale hain to unki list, warna null */
    fun stepsOf(text: String): List<String>? {
        val out = ArrayList<String>()
        for (part in text.split("\n", ";")) {
            var p = part.trim()
            if (p.isEmpty()) continue
            p = p.replace(Regex("\\s*\\|?\\s*MORE\\s*$"), "").trim()
            val up = p.uppercase()
            if (!(up.startsWith("OPEN:") || up.startsWith("ACTION:"))) return null
            out.add(p)
        }
        return if (out.isEmpty()) null else out
    }

    /** User ne skill ka trigger bola ho to steps chalao aur turant jawab lautao, warna null */
    fun tryRun(context: Context, raw: String): String? {
        val heard = norm(raw.substringBefore("\n\n["))
        if (heard.isEmpty()) return null
        for (k in SkillStore.list(context)) {
            if (k.trigger.isBlank()) continue
            if (norm(k.trigger) != heard) continue
            val steps = stepsOf(k.steps) ?: continue
            if (running) return "Ek skill pehle se chal rahi hai. 'Ruko' bolo ya thoda ruko 🙂"
            start(context.applicationContext, steps)
            return "Theek hai, '" + k.name + "' chala rahi hun 🎯"
        }
        return null
    }

    /** Test button ke liye: skill abhi chalao. Seedhe steps na hon to samjha do */
    fun runSkill(context: Context, k: SkillStore.Skill): String {
        val steps = stepsOf(k.steps)
            ?: return "Is skill ke steps seedhe (OPEN:/ACTION:) nahi hain, ye AI ke liye salah hai 🤖"
        if (running) return "Ek skill pehle se chal rahi hai. 'Ruko' bolo ya thoda ruko 🙂"
        start(context.applicationContext, steps)
        return "Chala rahi hun 🎯"
    }

    private fun start(app: Context, steps: List<String>) {
        running = true
        stopFlag = false
        Thread {
            try {
                for ((i, line) in steps.withIndex()) {
                    if (stopFlag) break
                    val res = PhoneActions.interpret(app, line)
                    if (res.startsWith("Accessibility")) break
                    if (i < steps.size - 1) {
                        val wait = if (line.uppercase().startsWith("OPEN:")) 2500L else 1500L
                        sleepUnlessStopped(wait)
                    }
                }
            } catch (e: Exception) {
            } finally {
                running = false
            }
        }.start()
    }

    private fun sleepUnlessStopped(ms: Long) {
        var left = ms
        while (left > 0 && !stopFlag) {
            try {
                Thread.sleep(200)
            } catch (e: Exception) {
            }
            left -= 200
        }
    }

    /** "Ruko" bolne par chalti skill roko */
    fun stop() {
        stopFlag = true
    }
}
