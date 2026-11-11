package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Bolkar sawaal poochna (sirf mic button dabane par chalta hai, kabhi chupke se nahi).
 * Partial text turant dikhta hai (preview). Main thread se bulao.
 */
class VoiceInput(private val ctx: Context, private val cb: Callback) {

    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(msg: String)
        fun onState(listening: Boolean)
    }

    private var sr: SpeechRecognizer? = null
    private var vosk: OfflineVoice? = null

    var listening = false
        private set

    companion object {
        fun onDeviceAvailable(ctx: Context): Boolean {
            return Build.VERSION.SDK_INT >= 31 && try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
            } catch (e: Exception) {
                false
            }
        }

        /** Settings mein dikhane ke liye: voice input online hai ya offline */
        fun capabilityText(ctx: Context): String {
            if (VoskModels.isInstalled(ctx, VoskModels.hi)) {
                return "Voice input: 🟢 offline Hindi + English mic taiyaar (internet na ho tab apne aap chalega)."
            }
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                return "Voice input: is phone mein speech service nahi mili."
            }
            return if (onDeviceAvailable(ctx)) {
                "Voice input: 🟢 offline (device par) chal sakta hai."
            } else {
                "Voice input: 🌐 online (Google service, internet chahiye). Offline ke liye phone Settings mein offline speech pack (Hindi/English) download karo."
            }
        }
    }

    private fun startVosk() {
        val v = OfflineVoice(ctx, object : Callback {
            override fun onPartial(text: String) {
                cb.onPartial(text)
            }

            override fun onFinal(text: String) {
                listening = false
                vosk = null
                cb.onState(false)
                cb.onFinal(text)
            }

            override fun onError(msg: String) {
                listening = false
                vosk = null
                cb.onState(false)
                cb.onError(msg)
            }

            override fun onState(listening: Boolean) {}
        })
        vosk = v
        listening = true
        cb.onState(true)
        v.start()
    }

    fun start() {
        if (listening) return
        // Internet na ho (ya "Sirf offline voice" on ho) aur offline Hindi+English mic installed ho
        val offlineNow = Prefs.voiceOffline(ctx) || !NetworkMonitor.isOnline(ctx)
        if (offlineNow && VoskModels.isInstalled(ctx, VoskModels.hi)) {
            startVosk()
            return
        }
        // Internet na ho to apne aap offline (device par) recogniser, agar phone mein pack hai
        val strict = Prefs.voiceOffline(ctx) || !NetworkMonitor.isOnline(ctx)
        val strictOk = onDeviceAvailable(ctx)
        if (strict && Build.VERSION.SDK_INT >= 31 && !strictOk) {
            cb.onError(
                if (Prefs.voiceOffline(ctx))
                    "Offline voice pack nahi mila. Phone Settings mein offline speech download karo, ya Settings mein 'Sirf offline voice' band karo."
                else
                    "Internet nahi hai aur offline voice pack bhi nahi mila. Phone Settings mein offline speech (Hindi/English) download karo, tab tak type karke poochho."
            )
            return
        }
        val r: SpeechRecognizer? = try {
            if (strict && strictOk) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            else if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx)
            else null
        } catch (e: Exception) {
            null
        }
        if (r == null) {
            cb.onError("Is phone mein voice sunne ki service nahi mili.")
            return
        }
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) cb.onPartial(t)
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val t = list?.firstOrNull()
                // Baaki vikalp aur confidence yaad rakho (AI ko behtar samajhne mein madad)
                if (!t.isNullOrBlank()) {
                    val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: -1f
                    VoiceHints.record(t, list.orEmpty().drop(1), conf)
                }
                finish()
                if (t.isNullOrBlank()) cb.onError("Kuch sunai nahi diya 🥺") else cb.onFinal(t)
            }

            override fun onError(error: Int) {
                if (!listening) return
                finish()
                cb.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch sunai nahi diya 🥺"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone allow karo 🎤"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Voice ke liye internet chahiye (offline pack nahi mila) 🌐"
                        // 12 = bhasha support nahi, 13 = bhasha ka offline pack install nahi
                        12, 13 ->
                            "Is bhasha ka offline voice pack nahi mila 📥 Settings → Voice mein 'Offline voice pack download karo' dabao"
                        else -> "Mic mein dikkat aayi (code $error) 😕"
                    }
                )
            }
        })
        val lang = Prefs.str(ctx, "lang", "hi-IN")
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, strict)
        listening = true
        cb.onState(true)
        try {
            r.startListening(i)
        } catch (e: Exception) {
            finish()
            cb.onError("Mic shuru nahi hua 😕")
        }
    }

    private fun finish() {
        listening = false
        cb.onState(false)
        val old = sr
        sr = null
        try {
            old?.destroy()
        } catch (e: Exception) {
        }
    }

    /** Bolna band karo aur jo suna wo de do */
    fun stop() {
        vosk?.stop()
        try {
            sr?.stopListening()
        } catch (e: Exception) {
        }
    }

    fun cancel() {
        if (!listening) return
        vosk?.cancel()
        vosk = null
        finish()
    }

    fun destroy() {
        vosk?.cancel()
        vosk = null
        listening = false
        try {
            sr?.destroy()
        } catch (e: Exception) {
        }
        sr = null
    }
}
