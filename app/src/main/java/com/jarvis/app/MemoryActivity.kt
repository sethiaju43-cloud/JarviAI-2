package com.jarvis.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

/** Jarvi ne kya yaad rakha hai aur kaun si skills sikhi hain: dekho, badlo, hatao. Sensitive baatein yahan 'Allow' maangti hain. */
class MemoryActivity : ScreenActivity() {

    override fun screenTitle() = "Memory aur Skills"

    // 0 = backup wapas daalna, 1 = backup file save karna, 2 = auto-backup folder chunna
    private var mode = 0

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            if (mode == 1) {
                writeBackup(uri)
            } else if (mode == 2) {
                AutoBackup.setFolder(applicationContext, uri)
                UiKit.toast(this, "Folder chun liya ✅")
                rebuild()
            } else {
                readBackup(uri)
            }
        }
    }

    private fun startSave() {
        mode = 1
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, "jarvis-backup-" + stamp + ".json")
        pick.launch(i)
    }

    private fun startFolder() {
        mode = 2
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        pick.launch(i)
    }

    private fun runAutoNow() {
        val app = applicationContext
        Thread {
            val ok = AutoBackup.runNow(app)
            runOnUiThread {
                UiKit.toast(this@MemoryActivity, if (ok) "Auto-backup ban gaya ✅" else "Pehle folder chuno, phir try karo")
                rebuild()
            }
        }.start()
    }

    private fun startLoad() {
        mode = 0
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        pick.launch(i)
    }

    private fun writeBackup(uri: Uri) {
        val app = applicationContext
        Thread {
            val msg = try {
                val data = BackupManager.exportSecure(app)
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
            var text = ""
            var err = ""
            try {
                val ins = contentResolver.openInputStream(uri)
                val bytes = if (ins == null) ByteArray(0) else ins.use { it.readBytes() }
                if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) {
                    err = "File khali ya bahut badi hai"
                } else {
                    text = String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                err = "File padh nahi payi"
            }
            if (err.isNotEmpty()) {
                runOnUiThread { UiKit.toast(this@MemoryActivity, err) }
            } else {
                runImport(app, text, null)
            }
        }.start()
    }

    /** Import karo. Password chahiye to dialog dikhao aur wahi text dobara try karo */
    private fun runImport(app: android.content.Context, text: String, password: String?) {
        val res = try {
            BackupManager.import(app, text, password)
        } catch (e: Exception) {
            BackupManager.Outcome(false, "File padh nahi payi")
        }
        runOnUiThread {
            UiKit.toast(this@MemoryActivity, res.message)
            if (res.needsPassword) {
                askPassword(text)
            } else {
                rebuild()
            }
        }
    }

    private fun askPassword(text: String) {
        val c = this
        val app = applicationContext
        val et = android.widget.EditText(c)
        et.hint = "Backup ka password"
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(c).setTitle("Password daalo").setView(et)
            .setPositiveButton("Kholo") { _, _ ->
                val pw = et.text.toString()
                Thread { runImport(app, text, pw) }.start()
            }
            .setNegativeButton("Nahi", null).show()
    }

    private fun passwordDialog() {
        val c = this
        val et = android.widget.EditText(c)
        et.hint = "Naya password (kam se kam 4 akshar)"
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(c).setTitle("Backup password").setView(et)
            .setPositiveButton("Save") { _, _ ->
                val pw = et.text.toString().trim()
                if (pw.length < 4) {
                    UiKit.toast(c, "Kam se kam 4 akshar likho")
                } else {
                    AutoBackup.setPassword(c, pw)
                    UiKit.toast(c, "Password lag gaya 🔐 Isse yaad rakho")
                    rebuild()
                }
            }
            .setNegativeButton("Nahi", null).show()
    }

    override fun build() {
        val c = this
        container.addView(UiKit.switchRow(c, "Long-term memory chalu", Prefs.memoryEnabled(c)) { Prefs.setBool(c, "memory_enabled", it) })
        container.addView(UiKit.text(c,
            "Ye encrypted hokar sirf aapke phone mein rehti hai. Password, OTP, card, phone number jaisi nijji baatein bina aapki 'Allow' ke kabhi save nahi hotin.",
            12f, dim = true))

        val pend = MemoryStore.pendingList()
        if (pend.isNotEmpty()) {
            container.addView(UiKit.section(c, "🔒 Aapki permission chahiye"))
            for ((i, p) in pend.withIndex()) {
                val card = UiKit.card(c)
                card.addView(UiKit.text(c, p.text, 14f))
                card.addView(UiKit.text(c, "Sensitive lagta hai: " + p.reason, 12f, dim = true))
                val r = UiKit.row(c)
                r.addView(UiKit.rowButton(c, "✅ Allow") { MemoryStore.approvePending(c, i); rebuild() })
                r.addView(UiKit.rowButton(c, "❌ Mat rakho") { MemoryStore.rejectPending(i); rebuild() })
                card.addView(r)
                container.addView(card)
            }
        }

        container.addView(UiKit.section(c, "Saved yaadein"))
        val list = MemoryStore.list(c).sortedByDescending { it.createdAt }
        if (list.isEmpty()) container.addView(UiKit.text(c, "Abhi kuch save nahi hai. Bolo: 'yaad rakho ki ...'", 13f, dim = true))
        for (e in list) {
            val card = UiKit.card(c)
            val tag = when (e.kind) { "pref" -> "⭐ Pasand"; "project" -> "📁 Project"; "summary" -> "🧠 Saar"; else -> "📝 Baat" }
            card.addView(UiKit.text(c, tag + (if (e.auto) " · auto" else ""), 11f, dim = true))
            card.addView(UiKit.text(c, e.text, 14f))
            val r = UiKit.row(c)
            r.addView(UiKit.rowButton(c, "✏️ Badlo") { editDialog(e) })
            r.addView(UiKit.rowButton(c, "🗑 Hatao") { MemoryStore.remove(c, e.id); rebuild() })
            card.addView(r)
            container.addView(card)
        }
        container.addView(UiKit.button(c, "➕ Nayi yaad jodo") { addDialog() })
        if (list.isNotEmpty()) {
            container.addView(UiKit.button(c, "🗑 Sab yaadein hatao") {
                AlertDialog.Builder(c).setTitle("Sab hatana hai?").setPositiveButton("Haan") { _, _ ->
                    MemoryStore.clear(c); rebuild()
                }.setNegativeButton("Nahi", null).show()
            })
        }

        container.addView(UiKit.section(c, "🎓 Skills (jo kaam Jarvi ko sikhaya)"))
        container.addView(UiKit.text(c,
            "Yahan likho ki kaun sa kaam kaise karna hai. Jab aap milta-julta kaam bologe, Jarvi isi tareeke se karegi.",
            12f, dim = true))
        val skills = SkillStore.list(c)
        if (skills.isEmpty()) container.addView(UiKit.text(c, "Abhi koi skill nahi hai.", 13f, dim = true))
        for (k in skills) {
            val card = UiKit.card(c)
            card.addView(UiKit.text(c, "🎓 " + k.name, 14f, bold = true))
            if (k.trigger.isNotEmpty()) card.addView(UiKit.text(c, "Jab bolun: " + k.trigger, 12f, dim = true))
            card.addView(UiKit.text(c, k.steps, 13f))
            val r = UiKit.row(c)
            r.addView(UiKit.rowButton(c, "✏️ Badlo") { skillDialog(k) })
            r.addView(UiKit.rowButton(c, "🗑 Hatao") { SkillStore.remove(c, k.id); rebuild() })
            card.addView(r)
            container.addView(card)
        }
        container.addView(UiKit.button(c, "➕ Nayi skill sikhao") { skillDialog(null) })

        container.addView(UiKit.section(c, "💾 Backup / Restore"))
        container.addView(UiKit.text(c,
            "Settings, yaadein, skills aur chats ek file mein save hoti hain (file manager mein). Naye phone par wahi file wapas daalo. API keys file mein nahi jaati, wo naye phone par dobara daalni hongi.",
            12f, dim = true))
        container.addView(UiKit.button(c, "💾 Backup banao (file save)") { startSave() })
        container.addView(UiKit.button(c, "📥 Backup wapas daalo (file chuno)") { startLoad() })

        container.addView(UiKit.section(c, "🔐 Backup password"))
        container.addView(UiKit.text(c,
            "Password lagaoge to backup file (aur auto-backup file) band ho jayegi, bina password ke koi nahi khol sakega. Password bhool gaye to backup kabhi nahi khulega, isliye kahin likh lo. Wapas daalte waqt app khud password poochhegi.",
            12f, dim = true))
        val hasPw = AutoBackup.password(c).isNotEmpty()
        container.addView(UiKit.text(c, if (hasPw) "Password: laga hua hai 🔐" else "Password: nahi lagaya", 12f, dim = true))
        container.addView(UiKit.button(c, if (hasPw) "🔐 Password badlo" else "🔐 Password lagao") { passwordDialog() })
        if (hasPw) {
            container.addView(UiKit.button(c, "🔓 Password hatao") {
                AutoBackup.setPassword(c, "")
                UiKit.toast(c, "Password hata diya")
                rebuild()
            })
        }

        container.addView(UiKit.section(c, "🔁 Auto-backup (har 7 din mein)"))
        container.addView(UiKit.text(c,
            "Ek baar folder chuno (jaise Downloads ya Documents). Phir app kholne par agar 7 din ho gaye to backup khud ban jayega. Purani auto-backup files file manager se kabhi bhi hata sakte ho.",
            12f, dim = true))
        container.addView(UiKit.switchRow(c, "Auto-backup chalu", AutoBackup.enabled(c)) { on ->
            AutoBackup.setEnabled(c, on)
            if (on && AutoBackup.folder(c) == null) UiKit.toast(c, "Neeche se pehle folder chuno")
        })
        val folderText = if (AutoBackup.folder(c) == null) "Folder: abhi nahi chuna" else "Folder: chuna hua hai ✅"
        container.addView(UiKit.text(c, folderText, 12f, dim = true))
        val last = AutoBackup.lastTime(c)
        if (last > 0L) {
            val whenText = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last))
            container.addView(UiKit.text(c, "Aakhri auto-backup: " + whenText, 12f, dim = true))
        }
        container.addView(UiKit.button(c, "📁 Backup folder chuno") { startFolder() })
        container.addView(UiKit.button(c, "▶️ Abhi auto-backup chalao") { runAutoNow() })
    }

    private fun skillDialog(k: SkillStore.Skill?) {
        val c = this
        val box = android.widget.LinearLayout(c)
        box.orientation = android.widget.LinearLayout.VERTICAL
        val pad = UiKit.dp(c, 16)
        box.setPadding(pad, pad / 2, pad, 0)
        val etName = android.widget.EditText(c)
        etName.hint = "Skill ka naam (jaise: WhatsApp message)"
        val etWhen = android.widget.EditText(c)
        etWhen.hint = "Jab main bolun (jaise: detective ko message karo)"
        val etSteps = android.widget.EditText(c)
        etSteps.hint = "Kya karna hai (jaise: WhatsApp kholo, chat chuno, likho, Send dabao)"
        if (k != null) {
            etName.setText(k.name)
            etWhen.setText(k.trigger)
            etSteps.setText(k.steps)
        }
        box.addView(etName)
        box.addView(etWhen)
        box.addView(etSteps)
        AlertDialog.Builder(c).setTitle(if (k == null) "Nayi skill" else "Skill badlo").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val a = etName.text.toString()
                val b = etWhen.text.toString()
                val d = etSteps.text.toString()
                val ok = if (k == null) SkillStore.add(c, a, b, d) else SkillStore.update(c, k.id, a, b, d)
                if (!ok) UiKit.toast(c, "Naam aur 'kya karna hai' zaroor likho")
                rebuild()
            }
            .setNegativeButton("Nahi", null).show()
    }

    private fun editDialog(e: MemoryEntry) {
        val c = this
        val et = android.widget.EditText(c)
        et.setText(e.text)
        AlertDialog.Builder(c).setTitle("Badlo").setView(et)
            .setPositiveButton("Save") { _, _ -> MemoryStore.update(c, e.id, et.text.toString()); rebuild() }
            .setNegativeButton("Nahi", null).show()
    }

    private fun addDialog() {
        val c = this
        val et = android.widget.EditText(c)
        et.hint = "Jaise: Mujhe chhote jawab pasand hain"
        AlertDialog.Builder(c).setTitle("Nayi yaad").setView(et)
            .setPositiveButton("Save") { _, _ ->
                // User ne khud likha hai, isliye force: par sensitive ho to bhi user ki marzi
                val t = et.text.toString()
                val r = MemoryStore.add(c, t, "fact", auto = false, force = true)
                if (r == MemoryStore.AddResult.TOO_SHORT) UiKit.toast(c, "Thoda aur likho")
                rebuild()
            }.setNegativeButton("Nahi", null).show()
    }
}
