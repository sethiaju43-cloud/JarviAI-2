package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** User ki chuni hui photo/file safely padhna (sirf user ke chune hue Uri, koi bhi file apne aap nahi) */
object Attachments {

    private const val MAX_FILE_BYTES = 1_000_000

    /** pdfUri null nahi hai to ye PDF hai: text nahi, PdfReader se pages padhe jayenge */
    class FileText(val name: String, val text: String, val pdfUri: Uri? = null)

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0)
                        if (!n.isNullOrBlank()) return n
                    }
                }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment ?: "file"
    }

    /** Chuni hui file PDF hai? (type ya naam se pehchano) */
    fun isPdf(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.getType(uri) == "application/pdf" ||
                displayName(ctx, uri).endsWith(".pdf", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /** Text file padho (1 MB tak). Binary ho to null. PDF ke liye PdfReader hai. */
    fun readText(ctx: Context, uri: Uri): FileText? {
        return try {
            val name = displayName(ctx, uri)
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_FILE_BYTES) }
                ?: return null
            // Binary pehchano: zyada NUL bytes
            val nul = bytes.count { it.toInt() == 0 }
            if (bytes.isNotEmpty() && nul * 50 > bytes.size) return null
            FileText(name, String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < max) {
            val n = read(buf, 0, minOf(buf.size, max - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    /** Photo ko chhota karke (memory bachane ke liye) base64 mein badlo */
    fun imageToBase64(ctx: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            Brain.toBase64(bmp, 1000)
        } catch (e: Throwable) {
            null
        }
    }

    fun bitmapToBase64(bmp: Bitmap): String = Brain.toBase64(bmp, 1000)
}
