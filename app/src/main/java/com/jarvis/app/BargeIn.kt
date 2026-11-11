package com.jarvis.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Process
import android.util.Log
import kotlin.math.sqrt

/**
 * EXPERIMENTAL: Jarvi bol rahi ho aur user bolna shuru kare to bolna rokne ke liye.
 *
 * Ye asli "full-duplex" voice nahi hai. Ye sirf mic ki awaaz ka zor (volume) naapta hai, ye samajhta nahi
 * ki kisne bola. Isliye:
 * - Earphones ke saath sabse achha chalta hai. Speaker par Jarvi ki apni awaaz mic mein aa sakti hai.
 * - Phone ka echo-cancel (AcousticEchoCanceler) ho to use kiya jata hai, par har phone mein nahi hota.
 * - Galat jagah trigger ho sakta hai, ya dheemi awaaz miss ho sakti hai.
 *
 * Sirf tab chalta hai jab Jarvi bol rahi ho (battery bachane ke liye) aur user ne ise Menu se ON kiya ho.
 * Trigger hone par mic pehle release hota hai, phir onVoice() bulaya jata hai (taaki SpeechRecognizer mic le sake).
 */
class BargeIn(private val onVoice: () -> Unit) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        val t = Thread { loop() }
        thread = t
        t.start()
    }

    fun stop() {
        running = false
        val t: Thread?
        synchronized(this) {
            t = thread
            thread = null
        }
        // Mic jaldi chhoot jaye (frame sirf 20 ms ka hai), isliye thoda sa intezaar
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(150)
            } catch (e: InterruptedException) {
            }
        }
    }

    private fun loop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (e: Exception) {
        }
        val rate = 16000
        val frame = rate / 50 // 20 ms
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            running = false
            return
        }
        var rec: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var triggered = false
        try {
            rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, frame * 2 * 4)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                running = false
                return
            }
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(rec.audioSessionId)
                    aec?.enabled = true
                }
            } catch (e: Exception) {
            }
            rec.startRecording()
            val buf = ShortArray(frame)
            var calib = 0.0
            var calibN = 0
            var loud = 0
            val begin = System.currentTimeMillis()
            while (running) {
                // Atke hue loop se mic hamesha na khula rahe: 60 second ki had
                if (System.currentTimeMillis() - begin > 60000L) break
                val n = rec.read(buf, 0, frame)
                if (n < 0) break
                if (n == 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i].toDouble()
                val rms = sqrt(sum / n)
                // Shuru ke 600 ms: Jarvi ki apni awaaz ka (echo) level naapo
                if (calibN < 30) {
                    calib += rms
                    calibN++
                    continue
                }
                val base = calib / calibN
                val threshold = maxOf(base * 2.2, 1500.0)
                if (rms > threshold) loud++ else loud = maxOf(0, loud - 1)
                // 10 frame (~200 ms) lagaataar tez awaaz = user bol raha hai
                if (loud >= 10) {
                    triggered = true
                    break
                }
            }
        } catch (e: SecurityException) {
            Log.w("JarviBarge", "mic permission nahi")
        } catch (e: Exception) {
            Log.w("JarviBarge", "failed: " + e.javaClass.simpleName)
        } finally {
            try {
                aec?.release()
            } catch (e: Exception) {
            }
            try {
                rec?.stop()
            } catch (e: Exception) {
            }
            try {
                rec?.release()
            } catch (e: Exception) {
            }
        }
        val fire = triggered && running
        running = false
        if (fire) onVoice()
    }
}
