package com.jarvis.app

/** Natural awaaz (ladki) sunkar chunne ki screen. Online (Gemini TTS), internet aur Gemini key chahiye. */
class VoiceStudioActivity : ScreenActivity() {

    override fun screenTitle() = "Natural awaaz (ladki)"

    override fun build() {
        val c = this
        container.addView(UiKit.text(c,
            "Ye Google Gemini ki natural awaazein hain. Ye online chalti hain (internet aur Gemini API key chahiye), " +
                "phone par download nahi hoti, aur Google ke free limit ke andar chalti hain. " +
                "Har awaaz ke saath ▶ Sunao dabao, jo pasand aaye usse ✅ Ye chuno.",
            12f, dim = true))
        val model = GeminiVoice.ttsModel(c)
        container.addView(UiKit.text(c,
            if (model == null) "⚠️ Abhi awaaz wala (TTS) model nahi mila. Online AI providers → Gemini → 'Check + models' dabao."
            else "Awaaz ka model: $model",
            12f))
        container.addView(UiKit.text(c,
            "Abhi ye sirf sunne aur chunne ke liye hai. Jawab mein lagana agla step hai, jab ye preview aapke phone par theek chal jaye.",
            12f, dim = true))

        val cur = GeminiVoice.voice(c)
        for (v in GeminiVoice.femaleVoices) {
            val card = UiKit.card(c)
            card.addView(UiKit.text(c, v + (if (v == cur) "   ✅ chuni hui" else ""), 15f, true))
            val r = UiKit.row(c)
            r.addView(UiKit.rowButton(c, "▶ Sunao") { preview(v) })
            r.addView(UiKit.rowButton(c, "✅ Ye chuno") {
                GeminiVoice.setVoice(c, v)
                UiKit.toast(c, v + " chun li ✅")
                rebuild()
            })
            card.addView(r)
            container.addView(card)
        }
        container.addView(UiKit.button(c, "⏹ Rokho") { GeminiVoice.stop() })
    }

    private fun preview(v: String) {
        UiKit.toast(this, v + " bana rahi hun... (2 se 6 second lag sakte hain)")
        Thread {
            val r = GeminiVoice.say(applicationContext, GeminiVoice.SAMPLE, v)
            runOnUiThread { if (!r.ok) UiKit.toast(this, "❌ " + r.message) }
        }.start()
    }

    override fun onPause() {
        GeminiVoice.stop()
        super.onPause()
    }
}
