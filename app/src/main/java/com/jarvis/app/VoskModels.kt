package com.jarvis.app

import android.content.Context
import android.os.StatFs
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Offline Hindi + English mic (Vosk) ke models: download, unzip, check */
object VoskModels {

    class Spec(val key: String, val label: String, val url: String, val sizeBytes: Long)

    val hi = Spec(
        "hi", "Hindi",
        "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", 44458845L
    )
    val en = Spec(
        "en", "English (India)",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip", 37573330L
    )

    @Volatile
    var busy = false
        private set

    private fun root(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "vosk").apply { mkdirs() }

    fun dir(ctx: Context, s: Spec): File = File(root(ctx), s.key)

    fun isInstalled(ctx: Context, s: Spec): Boolean {
        val d = dir(ctx, s)
        return File(d, "am").isDirectory && File(d, "conf").isDirectory
    }

    fun statusText(ctx: Context): String {
        val h = isInstalled(ctx, hi)
        val e = isInstalled(ctx, en)
        return when {
            h && e -> "Offline mic: ✅ Hindi + English taiyaar"
            h -> "Offline mic: Hindi taiyaar ✅, English abhi nahi"
            else -> "Offline mic: abhi install nahi (~80 MB, ek baar internet chahiye)"
        }
    }

    /** null = sab theek, warna error message. Background thread se bulao. */
    fun installAll(ctx: Context, onProgress: (String) -> Unit): String? {
        if (busy) return "Download pehle se chal raha hai"
        busy = true
        try {
            if (!NetworkMonitor.isOnline(ctx)) return "Internet nahi hai. Download ke liye WiFi chahiye."
            var missing = 0L
            for (s in listOf(hi, en)) if (!isInstalled(ctx, s)) missing += s.sizeBytes
            val free = StatFs(root(ctx).path).availableBytes
            if (free < missing * 3 + 100L * 1024 * 1024) {
                return "Storage kam hai (" + (free / 1048576) + " MB khali). Thodi jagah khali karo."
            }
            for (s in listOf(hi, en)) {
                if (isInstalled(ctx, s)) continue
                val err = install(ctx, s, onProgress)
                if (err != null) return err
            }
            return null
        } finally {
            busy = false
        }
    }

    private fun install(ctx: Context, s: Spec, onProgress: (String) -> Unit): String? {
        val zip = File(root(ctx), s.key + ".zip")
        val tmp = File(root(ctx), s.key + "_tmp")
        try {
            val req = Request.Builder().url(s.url).build()
            Net.download.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "Server error " + resp.code
                val body = resp.body ?: return "Khali jawab aaya"
                val len = body.contentLength()
                val total = if (len > 0) len else s.sizeBytes
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    val inp = body.byteStream()
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 700) {
                            last = now
                            onProgress(s.label + " model download: " + (done * 100 / total) + "%")
                        }
                    }
                }
            }
            onProgress(s.label + " model khol rahi hun (unzip)...")
            tmp.deleteRecursively()
            unzip(zip, tmp)
            if (!(File(tmp, "am").isDirectory && File(tmp, "conf").isDirectory)) {
                return "Model file adhoori ya galat hai, dobara try karo"
            }
            val dest = dir(ctx, s)
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) return "Model ko sahi jagah rakh nahi paye"
            return null
        } catch (e: Exception) {
            return "Download nahi hua: " + OnlineParsers.redact(e.message ?: e.javaClass.simpleName)
        } finally {
            zip.delete()
            if (tmp.exists()) tmp.deleteRecursively()
        }
    }

    /** Zip ka sabse upar wala folder hata kar andar ka saaman outDir mein rakhta hai */
    private fun unzip(zipFile: File, outDir: File) {
        outDir.mkdirs()
        val base = outDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val name = e.name.substringAfter('/', "")
                if (name.isNotEmpty()) {
                    val f = File(outDir, name)
                    if (!f.canonicalPath.startsWith(base)) throw SecurityException("zip ka raasta galat")
                    if (e.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        FileOutputStream(f).use { out -> zis.copyTo(out) }
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }
}
