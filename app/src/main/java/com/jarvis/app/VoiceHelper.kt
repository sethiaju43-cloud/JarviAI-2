package com.jarvis.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

object VoiceHelper {

    val names = listOf(
        "Awaaz 1: Cute", "Awaaz 2: Narm", "Awaaz 3: Khush",
        "Awaaz 4: Shant", "Awaaz 5: Tez", "Awaaz 6: Mithi"
    )
    // Pitch/rate sirf halka sa farak dete hain. Bahut zyada pitch awaaz ko nakli (chipmunk jaisa) bana deta hai
    // aur "female" awaaz nahi banata. Asli farak engine ki alag-alag awaazon (neeche engineVoices) se aata hai.
    private val pitches = floatArrayOf(1.08f, 1.04f, 1.12f, 1.0f, 1.06f, 1.1f)
    private val rates = floatArrayOf(0.97f, 0.93f, 1.02f, 0.9f, 1.05f, 0.95f)

    /** User ki chuni hui bolne ki speed ("dheere bolo" / "tez bolo"): 1.0 = normal, 0.6 se 1.5 ke beech */
    fun speed(ctx: Context): Float =
        (Prefs.str(ctx, "voice_speed", "1.0").toFloatOrNull() ?: 1.0f).coerceIn(0.6f, 1.5f)

    fun setSpeed(ctx: Context, v: Float) = Prefs.setStr(ctx, "voice_speed", v.toString())

    fun index(ctx: Context): Int {
        val i = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE).getInt("voice_idx", 0)
        return i.coerceIn(0, names.size - 1)
    }

    /**
     * Kis bhasha ki awaaz mein bolna hai: "hi", "en" ya "or" (Odia).
     * Jawab mein Hindi akshar (देवनागरी) ho to "hi", Odia akshar ho to "or",
     * warna Settings ki Language ke hisaab se (Odia chuni ho par jawab Roman mein ho to Hindi awaaz).
     */
    fun speakLang(ctx: Context, text: String): String {
        for (c in text) {
            if (c in 'ऀ'..'ॿ') return "hi"
            if (c in '\u0B00'..'\u0B7F') return "or"
        }
        val lang = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString("lang", "hi-IN") ?: "hi-IN"
        return if (lang.startsWith("en")) "en" else "hi"
    }

    private fun engineVoices(tts: TextToSpeech, lang: String): List<Voice> {
        val out = ArrayList<Voice>()
        try {
            val all = tts.voices
            if (all != null) {
                for (v in all) {
                    if (v.locale.language != lang) continue
                    val f = v.features
                    val missing = f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                    if (!missing) out.add(v)
                }
            }
        } catch (e: Exception) {
        }
        // India wali awaaz pehle, phir jinke naam se female ka andaaza ho, phir behtar quality, phir naam
        out.sortWith(
            compareBy<Voice>(
                { if (it.locale.country == "IN") 0 else 1 },
                { femaleHint(it) },
                { -it.quality },
                { it.name }
            )
        )
        return out
    }

    /**
     * Android mein awaaz ka gender poochhne ka koi official tareeka nahi hai. Kuch engines naam mein
     * "female"/"male" likhte hain; sirf wahi pakde jaate hain. Baaki awaazon ke liye Settings mein
     * Awaaz 1-6 chunkar test karo. 0 = naam mein female, 1 = pata nahi, 2 = naam mein male.
     */
    private fun femaleHint(v: Voice): Int {
        val n = v.name.lowercase()
        return when {
            n.contains("female") -> 0
            n.contains("male") -> 2
            else -> 1
        }
    }

    /** (offline awaaz, online awaaz) - online wali ko internet chahiye */
    fun voiceStats(tts: TextToSpeech, lang: String): Pair<Int, Int> {
        var off = 0
        var on = 0
        for (v in engineVoices(tts, lang)) {
            if (v.isNetworkConnectionRequired) on++ else off++
        }
        return Pair(off, on)
    }

    fun voiceCount(tts: TextToSpeech, lang: String = "hi"): Int {
        return engineVoices(tts, lang).size
    }

    /**
     * Awaaz lagao. true = chahi hui bhasha ki awaaz mil gayi,
     * false = nahi mili (Hindi ya phone ki bhasha par wapas aa gaye).
     */
    fun apply(ctx: Context, tts: TextToSpeech, text: String = ""): Boolean {
        val idx = index(ctx)
        val lang = speakLang(ctx, text)
        var ok = false
        val offline = !NetworkMonitor.isOnline(ctx)
        val vs = engineVoices(tts, lang).filter { !offline || !it.isNetworkConnectionRequired }
        if (vs.isNotEmpty()) {
            try {
                tts.voice = vs[idx % vs.size]
                ok = true
            } catch (e: Exception) {
            }
        }
        if (!ok) {
            val want = when (lang) {
                "en" -> Locale("en", "IN")
                "or" -> Locale("or", "IN")
                else -> Locale("hi", "IN")
            }
            val r = tts.setLanguage(want)
            ok = !(r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
            if (!ok) {
                val r2 = tts.setLanguage(Locale("hi", "IN"))
                if (r2 == TextToSpeech.LANG_MISSING_DATA || r2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault())
                }
            }
        }
        tts.setPitch(pitches[idx])
        tts.setSpeechRate(rates[idx] * speed(ctx))
        return ok
    }
}
