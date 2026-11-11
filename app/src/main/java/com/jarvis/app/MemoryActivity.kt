package com.jarvis.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

/** Jarvi ne kya yaad rakha hai aur kaun si skills sikhi hain: dekho, badlo, hatao. Sensitive baatein yahan 'Allow' maangti hain. */
class MemoryActivity : ScreenActivity() {

    override fun screenTitle() = "Memory aur Skills"

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
