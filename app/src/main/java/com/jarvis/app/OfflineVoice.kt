package com.jarvis.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream

/**
 * Bina internet ke Hindi + English (Hinglish) sunna, Vosk se.
 * Pehle Hindi model sunta hai. Agar bharosa kam ho to wahi awaaz English model se bhi
 * parakhta hai aur jo behtar ho wo deta hai. Callbacks main thread par aate hain.
 */
class OfflineVoice(private val ctx: Context, private val cb: VoiceInput.Callback) {

    @Volatile
    private var running = false

    @Volatile
    private var cancelled = false

    private class Heard(val text: String, val conf: Double)

    companion object {
        private const val RATE = 16000
        private val lock = Any()
        private var hiCache: Model? = null
        private var inUse = 0
        private val main = Handler(Looper.getMainLooper())

        private val releaseTask = Runnable {
            synchronized(lock) {
                if (inUse == 0) {
                    try {
                        hiCache?.close()
                    } catch (e: Throwable) {
                    }
                    hiCache = null
                }
            }
        }

        private fun acquireHi(ctx: Context): Model {
            synchronized(lock) {
                inUse++
                main.removeCallbacks(releaseTask)
                val c = hiCache
                if (c != null) return c
                try {
                    val m = Model(VoskModels.dir(ctx, VoskModels.hi).absolutePath)
                    hiCache = m
                    return m
                } catch (e: Throwable) {
                    inUse--
                    throw e
                }
            }
        }

        /** now=true: Hindi model abhi RAM se hata do (English model ke liye jagah) */
        private fun releaseHi(now: Boolean) {
            synchronized(lock) {
                if (inUse > 0) inUse--
                if (now && inUse == 0) {
                    try {
                        hiCache?.close()
                    } catch (e: Throwable) {
                    }
                    hiCache = null
                } else {
                    main.postDelayed(releaseTask, 45_000L)
                }
            }
        }
    }

    private fun post(r: () -> Unit) {
        main.post { if (!cancelled) r() }
    }

    private fun fail(msg: String) {
        post { cb.onError(msg) }
    }

    fun start() {
        if (running) return
        running = true
        cancelled = false
        Thread { work() }.start()
    }

    /** Bolna band: jo ab tak suna uska jawab do */
    fun stop() {
        running = false
    }

    fun cancel() {
        cancelled = true
        running = false
    }

    private fun partialOf(json: String): String {
        return try {
            JSONObject(json).optString("partial", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun parse(json: String): Heard {
        return try {
            val o = JSONObject(json)
            val text = o.optString("text", "").trim()
            val arr = o.optJSONArray("result")
            var sum = 0.0
            var n = 0
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    sum += arr.getJSONObject(i).optDouble("conf", 0.0)
                    n++
                }
            }
            Heard(text, if (n > 0) sum / n else 0.0)
        } catch (e: Exception) {
            Heard("", 0.0)
        }
    }

    private fun englishPass(bytes: ByteArray): Heard? {
        var m: Model? = null
        var rc: Recognizer? = null
        try {
            m = Model(VoskModels.dir(ctx, VoskModels.en).absolutePath)
            rc = Recognizer(m, RATE.toFloat())
            rc.setWords(true)
            var off = 0
            while (off < bytes.size) {
                val len = minOf(4096, bytes.size - off)
                rc.acceptWaveForm(bytes.copyOfRange(off, off + len), len)
                off += len
            }
            return parse(rc.finalResult)
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                rc?.close()
            } catch (e: Throwable) {
            }
            try {
                m?.close()
            } catch (e: Throwable) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun work() {
        var hiHeld = false
        var rec: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            val model = acquireHi(ctx)
            hiHeld = true
            val r = Recognizer(model, RATE.toFloat())
            r.setWords(true)
            rec = r

            val minBuf = AudioRecord.getMinBufferSize(
                RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf * 2, 8192)
            val rcd = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
            ar = rcd
            if (rcd.state != AudioRecord.STATE_INITIALIZED) {
                fail("Mic shuru nahi hua 😕")
                return
            }
            rcd.startRecording()

            val pcm = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val t0 = System.currentTimeMillis()
            var heard = false
            var ended = false
            while (running && !cancelled) {
                val n = rcd.read(buf, 0, buf.size)
                if (n < 0) {
                    fail("Mic mein dikkat aayi 😕")
                    return
                }
                if (n == 0) continue
                pcm.write(buf, 0, n)
                if (r.acceptWaveForm(buf, n)) {
                    ended = true
                    break
                }
                val p = partialOf(r.partialResult)
                if (p.isNotBlank()) {
                    heard = true
                    post { cb.onPartial(p) }
                }
                val now = System.currentTimeMillis()
                if (!heard && now - t0 > 8000L) break
                if (now - t0 > 25000L) break
            }
            if (cancelled) return
            try {
                rcd.stop()
            } catch (e: Exception) {
            }

            var best = parse(if (ended) r.result else r.finalResult)
            val pcmBytes = pcm.toByteArray()

            // Bharosa kam ho to wahi awaaz English model se bhi parakho
            if (best.conf < 0.85 && pcmBytes.size > 8000 && VoskModels.isInstalled(ctx, VoskModels.en)) {
                try {
                    r.close()
                } catch (e: Throwable) {
                }
                rec = null
                releaseHi(true)
                hiHeld = false
                val en = englishPass(pcmBytes)
                if (en != null && en.text.isNotBlank() && (best.text.isBlank() || en.conf > best.conf)) {
                    best = en
                }
            }

            if (cancelled) return
            val finalText = best.text
            if (finalText.isBlank()) fail("Kuch sunai nahi diya 🥺") else post { cb.onFinal(finalText) }
        } catch (e: Throwable) {
            fail("Offline mic chalu nahi hua: " + OnlineParsers.redact(e.message ?: e.javaClass.simpleName))
        } finally {
            try {
                rec?.close()
            } catch (e: Throwable) {
            }
            try {
                ar?.stop()
            } catch (e: Throwable) {
            }
            try {
                ar?.release()
            } catch (e: Throwable) {
            }
            if (hiHeld) releaseHi(false)
            running = false
        }
    }
}
