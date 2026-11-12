package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Auto-backup: user ek baar folder chunta hai. Uske baad app khulne par agar 7 din se zyada ho gaye
 * to backup file us folder mein khud ban jati hai. (Ye settings is file mein hain, backup mein nahi jaati.)
 */
object AutoBackup {

    private const val PREFS = "jarvis_autobackup"
    private const val EVERY_MS = 7L * 24L * 60L * 60L * 1000L

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = sp(ctx).getBoolean("on", false)

    fun setEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("on", v).apply()
    }

    fun folder(ctx: Context): String? = sp(ctx).getString("uri", null)

    fun lastTime(ctx: Context): Long = sp(ctx).getLong("last", 0L)

    /** Chuna hua folder hamesha ke liye yaad rakho */
    fun setFolder(ctx: Context, uri: Uri) {
        try {
            ctx.applicationContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
        }
        sp(ctx).edit().putString("uri", uri.toString()).apply()
    }

    /** Agar chalu hai, folder chuna hai aur 7 din ho gaye to backup banao */
    fun runIfDue(ctx: Context) {
        if (!enabled(ctx) || folder(ctx) == null) return
        if (System.currentTimeMillis() - lastTime(ctx) < EVERY_MS) return
        runNow(ctx)
    }

    /** Abhi backup banao. Ban gaya to true */
    fun runNow(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val saved = folder(app) ?: return false
        try {
            val tree = Uri.parse(saved)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val doc = DocumentsContract.createDocument(
                app.contentResolver, parent, "application/json", "jarvis-auto-backup-" + stamp
            )
            if (doc == null) return false
            val out = app.contentResolver.openOutputStream(doc, "wt")
            if (out == null) return false
            out.use { it.write(BackupManager.export(app).toByteArray(Charsets.UTF_8)) }
            sp(app).edit().putLong("last", System.currentTimeMillis()).apply()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
