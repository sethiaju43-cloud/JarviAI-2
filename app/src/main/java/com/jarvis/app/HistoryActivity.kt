package com.jarvis.app

import androidx.appcompat.app.AlertDialog

/** Purani chats: kholo ya hatao */
class HistoryActivity : ScreenActivity() {

    override fun screenTitle() = "Chat History"

    override fun build() {
        val c = this
        container.addView(UiKit.text(c, "Chats encrypted hokar sirf phone mein hain. Nayi chat shuru karne par abhi ki chat yahan aa jati hai.", 12f, dim = true))
        val list = ChatStore.listArchive(c)
        if (list.isEmpty()) container.addView(UiKit.text(c, "Abhi koi purani chat nahi hai.", 14f, dim = true))
        val fmt = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())
        for (a in list) {
            val card = UiKit.card(c)
            card.addView(UiKit.text(c, a.title, 14f, true))
            card.addView(UiKit.text(c, fmt.format(java.util.Date(a.ts)) + " · " + a.msgs.size + " messages", 12f, dim = true))
            val r = UiKit.row(c)
            r.addView(UiKit.rowButton(c, "📂 Kholo") {
                if (ChatStore.restore(c, a.id)) {
                    Brain.resetWorking(c)
                    UiKit.toast(c, "Chat khul gayi. Wapas jaakar dekho.")
                    finish()
                }
            })
            r.addView(UiKit.rowButton(c, "🗑 Hatao") { ChatStore.deleteArchive(c, a.id); rebuild() })
            card.addView(r)
            container.addView(card)
        }
        if (list.isNotEmpty()) {
            container.addView(UiKit.button(c, "🗑 Poori history hatao") {
                AlertDialog.Builder(c).setTitle("Sab purani chats hatani hain?").setPositiveButton("Haan") { _, _ ->
                    ChatStore.clearArchive(c); rebuild()
                }.setNegativeButton("Nahi", null).show()
            })
        }
    }
}
