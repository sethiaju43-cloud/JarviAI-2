package com.jarvis.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

/** Jarvi ne kya yaad rakha hai: dekho, badlo, hatao. Sensitive baatein yahan 'Allow' maangti hain. */
class MemoryActivity : ScreenActivity() {

    override fun screenTitle() = "Memory"

    private var pendingSave = false

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            if (pendingSave) writeBackup(uri) else readBackup(uri)
        }
    }

    private fun startSave() {
        pendingSave = true
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, "jarvis-backup-" + stamp + ".json")
        pick.launch(i)
    }

    private fun startLoad() {
        pendingSave = false
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        pick.launch(i)
    }

    private fun writeBackup(uri: Uri) {
        val app = applicationContext
        Thread {
            val msg = try {
                val data = BackupManager.export(app)
                val out = contentResolver.openOutputStream(uri, "wt")
                if (out == null) {
                    "File nahi khul payi"
                } else {
                    out.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                    "Backup save ho gaya ✅ File manager mein dekho"
                }
            } catch (e: Exception) {
                "Backup nahi ban paya"
            }
            runOnUiThread { UiKit.toast(this@MemoryActivity, msg) }
        }.start()
    }

    private fun readBackup(uri: Uri) {
        val app = applicationContext
        Thread {
            val res = try {
                val ins = contentResolver.openInputStream(uri)
                val bytes = if (ins == null) ByteArray(0) else ins.use { it.readBytes() }
                if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) {
                    BackupManager.Outcome(false, "File khali ya bahut badi hai")
                } else {
                    BackupManager.import(app, String(bytes, Charsets.UTF_8))
                }
            } catch (e: Exception) {
                BackupManager.Outcome(false, "File padh nahi payi")
            }
            runOnUiThread {
                UiKit.toast(this@MemoryActivity, res.message)
                rebuild()
            }
        }.start()
    }
