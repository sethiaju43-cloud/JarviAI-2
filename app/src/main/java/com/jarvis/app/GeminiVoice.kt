package com.jarvis.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gemini ki natural awaaz (Text-to-Speech). ONLINE hai: internet + Gemini API key chahiye, aur Google ke
 * free quota/limit ke andar chalti hai. Awaaz phone par download nahi hoti, har baar Google se banti hai.
 *
 * Request: generateContent, responseModalities=["AUDIO"], speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName
 * (Google docs ke hisaab se). Jawab mein audio inlineData (base64) aati hai; aam taur par raw PCM 16-bit mono.
 * Format mimeType se dekhte hain (rate=...); PCM na ho to MediaPlayer se chalate hain.
 */
object GeminiVoice {

    /** Google ke Gemini-TTS docs ne inhe ladki ki awaaz bataya hai. Sab aapki key par milengi, ye pakka nahi. */
    val femaleVoices = listOf(
        "Kore", "Zephyr", "Leda", "Aoede", "Despina", "Sulafat", "Achernar",
        "Autonoe", "Callirrhoe", "Erinome", "Gacrux", "Laomedeia", "Pulcherrima", "Vindemiatrix"
    )

    const val SAMPLE = "नमस्ते, मैं जार्वी हूँ। आप कैसे हैं? Aaj main aapki kya madad karun?"

    class Result(val ok: Boolean, val message: String)

    private class Audio(val bytes: ByteArray, val mime: String)

    fun enabled(ctx: Context): Boolean = Prefs.bool(ctx, "gv_on", false)
    fun setEnabled(ctx: Context, v: Boolean) = Prefs.setBool(ctx, "gv_on", v)
    fun voice(ctx: Context): String = Prefs.str(ctx, "gv_voice", "Kore")
    fun setVoice(ctx: Context, v: String) = Prefs.setStr(ctx, "gv_voice", v)

    /** Provider ki model list mein se awaaz (TTS) wala model. Flash wale pehle (tez). Nahi mile to null. */
    fun ttsModel(ctx: Context): String? {
        val all = ProviderRegistry.found(ctx, "gemini").filter { it.lowercase().contains("tts") }
        return all.sortedWith(compareBy<String>({ if (it.lowercase().contains("flash")) 0 else 1 }, { it })).firstOrNull()
    }

    @Volatile
    private var stopFlag = false

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var player: MediaPlayer? = null

    fun stop() {
        stopFlag = true
        try {
            track?.pause()
            track?.flush()
        } catch (e: Exception) {
        }
        try {
            player?.stop()
        } catch (e: Exception) {
        }
    }

    /**
     * Bolkar sunao. Ye ruk kar chalta hai (bolna khatam hone tak), isliye peeche ke thread se bulao.
     * onStart: awaaz aana shuru hone par. Result.ok=false ho to message mein saaf wajah.
     */
    fun say(ctx: Context, text: String, voiceName: String = voice(ctx), onStart: () -> Unit = {}): Result {
        stopFlag = false
        val clean = text.trim().take(700)
        if (clean.isEmpty()) return Result(false, "Bolne ko kuch nahi")
        val app = ctx.applicationContext
        val (audio, err) = synth(app, clean, voiceName)
        if (audio == null) return Result(false, err)
        if (stopFlag) return Result(true, "roka gaya")
        onStart()
        return try {
            play(app, audio)
            Result(true, "ok")
        } catch (e: Exception) {
            Result(false, "Awaaz chalane mein dikkat: " + e.javaClass.simpleName)
        }
    }

    private fun synth(ctx: Context, text: String, voiceName: String): Pair<Audio?, String> {
        val key = ProviderRegistry.key(ctx, "gemini")
        if (key.isBlank()) return Pair(null, "Gemini API key nahi hai")
        if (!NetworkMonitor.isOnline(ctx)) return Pair(null, "Internet nahi hai")
        val model = ttsModel(ctx)
            ?: return Pair(null, "Is key par awaaz (TTS) wala model nahi mila. Online AI providers mein Gemini ka 'Check + models' dabao.")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put(
                                "prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)
                            )
                        )
                    )
            )
        val url = ProviderRegistry.baseUrl(ctx, "gemini") + "/models/" + model + ":generateContent"
        return try {
            val rq = Request.Builder().url(url).header("x-goog-api-key", key)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            Net.api.newCall(rq).execute().use { r ->
                val raw = r.body?.string() ?: ""
                if (!r.isSuccessful) {
                    val msg = OnlineParsers.errorMessage(raw, r.code)
                    return Pair(null, ProviderRegistry.friendly(ErrorClassifier.classify(r.code, msg), msg))
                }
                val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                    ?: return Pair(null, "Awaaz nahi aayi (khali jawab)")
                for (i in 0 until parts.length()) {
                    val o = parts.optJSONObject(i) ?: continue
                    val inl = o.optJSONObject("inlineData") ?: o.optJSONObject("inline_data") ?: continue
                    val data = inl.optString("data", "")
                    if (data.isBlank()) continue
                    val mime = inl.optString("mimeType", inl.optString("mime_type", ""))
                    return Pair(Audio(Base64.decode(data, Base64.DEFAULT), mime), "")
                }
                Pair(null, "Awaaz nahi aayi (audio part nahi mila)")
            }
        } catch (e: Exception) {
            ModelRouter.lastNetError = e.javaClass.simpleName
            Pair(null, "Internet ya server dikkat [" + e.javaClass.simpleName + "]")
        }
    }

    private fun rateOf(mime: String): Int =
        Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000

    private fun play(ctx: Context, a: Audio) {
        val m = a.mime.lowercase()
        if (m.isBlank() || m.contains("l16") || m.contains("pcm")) playPcm(a.bytes, rateOf(m))
        else playFile(ctx, a.bytes)
    }

    private fun playPcm(pcm: ByteArray, rate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            var off = 0
            while (off < pcm.size && !stopFlag) {
                val n = t.write(pcm, off, minOf(4096, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            val frames = pcm.size / 2
            val totalMs = frames * 1000L / rate
            var waited = 0L
            while (!stopFlag && t.playbackHeadPosition < frames && waited < totalMs + 1500L) {
                Thread.sleep(40)
                waited += 40
            }
        } finally {
            try {
                t.stop()
            } catch (e: Exception) {
            }
            t.release()
            track = null
        }
    }

    private fun playFile(ctx: Context, bytes: ByteArray) {
        val f = File(ctx.cacheDir, "gv_tmp.audio")
        f.writeBytes(bytes)
        val latch = CountDownLatch(1)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { latch.countDown() }
            mp.setOnErrorListener { _, _, _ ->
                latch.countDown()
                true
            }
            mp.prepare()
            mp.start()
            latch.await(mp.duration.toLong() + 2000L, TimeUnit.MILLISECONDS)
        } finally {
            try {
                mp.release()
            } catch (e: Exception) {
            }
            player = null
            f.delete()
        }
    }
}
