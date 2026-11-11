package com.jarvis.app

import androidx.appcompat.app.AlertDialog

/** Jarvi ne kya yaad rakha hai: dekho, badlo, hatao. Sensitive baatein yahan 'Allow' maangti hain. */
class MemoryActivity : ScreenActivity() {

    override fun screenTitle() = "Memory"

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
