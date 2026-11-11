package com.jarvis.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech

/** Offline voice (sunna aur bolna) ke language pack download wali screens seedha kholta hai */
object VoicePack {

    private fun tryStart(ctx: Context, i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Offline speech recognition (sunne wala) pack. Jo message dikhana hai wo wapas deta hai. */
    fun openRecognitionDownload(ctx: Context): String {
        val google = "com.google.android.googlequicksearchbox"
        val tries = listOf(
            Intent().setComponent(
                ComponentName(google, "com.google.android.voicesearch.greco3.languagepack.InstallActivity")
            ),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for ((idx, i) in tries.withIndex()) {
            if (tryStart(ctx, i)) {
                return when (idx) {
                    0 -> "Hindi (India) aur English (India) download karo"
                    1 -> "'Offline speech recognition' kholo, Hindi (India) aur English (India) download karo"
                    else -> "Settings mein Google → Voice → Offline speech recognition dhundo, Hindi aur English download karo"
                }
            }
        }
        return "Settings nahi khul payi. Phone Settings mein 'Offline speech recognition' khud dhundo."
    }

    /** Offline awaaz (bolne wala) pack, yaani Text-to-speech ka voice data */
    fun openTtsData(ctx: Context): String {
        val tries = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for ((idx, i) in tries.withIndex()) {
            if (tryStart(ctx, i)) {
                return if (idx == 0) "Hindi (India) awaaz download karo"
                else "Text-to-speech settings mein Hindi (India) awaaz download karo"
            }
        }
        return "Settings nahi khul payi. Phone Settings mein 'Text-to-speech' khud dhundo."
    }
}
