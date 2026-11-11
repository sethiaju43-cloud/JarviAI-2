package com.jarvis.app

import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Local AI models: device check, download manager, use/test/update/remove */
class ModelsActivity : ScreenActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private class Holder(val bar: ProgressBar, val status: TextView, val id: String)
    private val holders = ArrayList<Holder>()
    private var wasDownloading = false

    private val ticker = object : Runnable {
        override fun run() {
            val active = ModelDownloader.activeId
            for (h in holders) {
                val st = ModelDownloader.state(h.id) ?: continue
                if (st.status == "running") {
                    val pct = if (st.total > 0) (st.downloaded * 100 / st.total).toInt() else 0
                    h.bar.progress = pct
                    h.status.text = "⬇ " + (st.downloaded / 1048576) + " / " + (if (st.total > 0) (st.total / 1048576).toString() else "?") + " MB (" + pct + "%)"
                }
            }
            if (wasDownloading && active == null) {
                wasDownloading = false
                rebuild()
            }
            if (active != null) wasDownloading = true
            handler.postDelayed(this, 800)
        }
    }

    override fun screenTitle() = "Local AI Models"

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun build() {
        val c = this
        holders.clear()
        val snap = DeviceInfo.snapshot(c)

        val dev = UiKit.card(c)
        dev.addView(UiKit.text(c, "📱 Aapka phone", 15f, true))
        dev.addView(UiKit.text(c, DeviceInfo.describe(snap), 12f, dim = true))
        dev.addView(UiKit.text(c, "Chhote quantized model se shuru karo. 4B+ models experimental hain: 6 GB RAM par chalne ki guarantee nahi. Sab models internal storage mein download hote hain, aur kabhi bhi hata sakte ho.", 12f, dim = true))
        container.addView(dev)

        container.addView(UiKit.text(c, "HuggingFace token (sirf gated/custom models ke liye, optional)", 12f, dim = true))
        val tok = UiKit.input(c, "hf_...", ApiKeyStore.getSecret(c, "hf_token"), password = true)
        container.addView(tok)
        container.addView(UiKit.button(c, "Token save karo") {
            ApiKeyStore.setSecret(c, "hf_token", tok.text.toString())
            UiKit.toast(c, "Save ho gaya ✅")
        })

        val active = LocalLlm.activeModel(c)
        container.addView(UiKit.section(c, "Models"))
        for (m in LocalModelCatalog.all(c)) container.addView(modelCard(m, snap, active))

        container.addView(UiKit.button(c, "➕ Custom model (.litertlm URL)") { addCustomDialog() })
        container.addView(UiKit.text(c, "Runtime: LiteRT-LM (Google). Sirf .litertlm format chalta hai. Model chalte waqt hi RAM leta hai aur idle hone par apne aap hat jata hai.", 12f, dim = true))
    }

    private fun modelCard(m: LocalModel, snap: DeviceSnapshot, active: LocalModel?): android.view.View {
        val c = this
        val installed = ModelDownloader.isInstalled(c, m)
        val partial = ModelDownloader.partialBytes(c, m)
        val downloading = ModelDownloader.activeId == m.id
        val fit = ModelFit.evaluate(m, snap, installed)
        val card = UiKit.card(c)
        card.addView(UiKit.text(c, m.name, 15f, true))
        val size = if (m.sizeMb > 0) "~" + m.sizeMb + " MB download" else "size download par pata chalega"
        card.addView(UiKit.text(c, m.params + " · " + size + " · RAM ~" + m.peakRamMb + " MB · " + m.license, 12f, dim = true))
        card.addView(UiKit.text(c, "Bhasha: " + m.languages, 12f, dim = true))
        card.addView(UiKit.text(c, "Kaam: " + m.strengths, 12f, dim = true))
        if (m.note.isNotBlank()) card.addView(UiKit.text(c, m.note, 11f, dim = true))
        val icon = when (fit.fit) { Fit.OK -> "✅"; Fit.TIGHT -> "⚠️"; else -> "⛔" }
        card.addView(UiKit.text(c, icon + " " + fit.message, 12f))

        val status = UiKit.text(c, "", 12f)
        val bar = UiKit.progress(c)
        UiKit.setVisible(bar, downloading)
        card.addView(bar)
        card.addView(status)
        holders.add(Holder(bar, status, m.id))
        status.text = when {
            downloading -> "Download chal raha hai..."
            installed && active?.id == m.id -> "🟢 Installed aur ACTIVE" + (if (LocalLlm.loadedModelId() == m.id) " (memory mein loaded)" else "")
            installed -> "Installed"
            partial > 0 -> "Adhoora download: " + partial / 1048576 + " MB (resume ho sakta hai)"
            else -> "Installed nahi"
        }
        val err = ModelDownloader.state(m.id)
        if (!installed && !downloading && err != null && err.status == "error") {
            card.addView(UiKit.text(c, "❌ " + err.error, 12f))
        }

        val r1 = UiKit.row(c)
        if (downloading) {
            r1.addView(UiKit.rowButton(c, "⏹ Cancel") { ModelDownloader.cancel() })
        } else if (!installed) {
            r1.addView(UiKit.rowButton(c, if (partial > 0) "⬇ Resume" else "⬇ Download") { confirmDownload(m, fit) })
        } else {
            r1.addView(UiKit.rowButton(c, if (active?.id == m.id) "✔ Active" else "Use karo") {
                Prefs.setStr(c, "local_model_id", m.id); LocalLlm.unload("model badla"); rebuild()
            })
            r1.addView(UiKit.rowButton(c, "🧪 Test") { testModel(m) })
        }
        card.addView(r1)
        if (installed || partial > 0) {
            val r2 = UiKit.row(c)
            if (installed) r2.addView(UiKit.rowButton(c, "🔄 Update?") { checkUpdate(m) })
            r2.addView(UiKit.rowButton(c, "🗑 Hatao") {
                AlertDialog.Builder(c).setTitle("Hatana hai?").setMessage(m.name + " ki file mita di jayegi.")
                    .setPositiveButton("Haan") { _, _ ->
                        ModelDownloader.delete(c, m)
                        if (m.custom) LocalModelCatalog.removeCustom(c, m.id)
                        rebuild()
                    }.setNegativeButton("Nahi", null).show()
            })
            card.addView(r2)
        } else if (m.custom) {
            card.addView(UiKit.button(c, "List se hatao") { LocalModelCatalog.removeCustom(c, m.id); rebuild() })
        }
        return card
    }

    private fun confirmDownload(m: LocalModel, fit: FitResult) {
        val c = this
        if (fit.fit == Fit.UNSUPPORTED || fit.fit == Fit.NO_STORAGE) {
            AlertDialog.Builder(c).setTitle("Download nahi ho sakta").setMessage(fit.message).setPositiveButton("Theek hai", null).show()
            return
        }
        val sb = StringBuilder()
        sb.append("Size: ").append(if (m.sizeMb > 0) "~" + m.sizeMb + " MB" else "pata nahi").append("\n")
        sb.append("RAM (chalte waqt): ~").append(m.peakRamMb).append(" MB\n")
        sb.append("Bhasha: ").append(m.languages).append("\n\n")
        if (fit.fit != Fit.OK) sb.append("⚠️ ").append(fit.message).append("\n\n")
        if (!NetworkMonitor.isUnmetered(c)) sb.append("📶 Aap mobile data par ho. Bada download hai, WiFi behtar rahega.\n\n")
        sb.append("Model internal storage mein save hoga. Download notification mein progress dikhega aur beech mein rok kar baad mein resume kar sakte ho.")
        AlertDialog.Builder(c).setTitle(m.name).setMessage(sb.toString())
            .setPositiveButton("⬇ Download") { _, _ ->
                val e = ModelDownloader.start(c, m)
                if (e != null) UiKit.toast(c, e)
                rebuild()
            }.setNegativeButton("Nahi", null).show()
    }

    private fun testModel(m: LocalModel) {
        val c = this
        UiKit.toast(c, "Test chal raha hai, kuch second lag sakte hain...")
        Thread {
            val t0 = System.currentTimeMillis()
            val ld = LocalLlm.load(c, m)
            val t1 = System.currentTimeMillis()
            var out: String
            if (!ld.ok) {
                out = "❌ Load nahi hua: " + ld.error
            } else {
                val r = LocalLlm.generate(c, m, Prompts.LOCAL_BASE, emptyList(),
                    "Namaste! Apna parichay ek vaakya mein do aur 12 x 12 kitna hota hai batao.", null)
                val t2 = System.currentTimeMillis()
                out = if (r.ok) "✅ Chal raha hai\nLoad: " + (t1 - t0) / 1000.0 + " s, jawab: " + (t2 - t1) / 1000.0 + " s\n\n" + r.text
                else "❌ " + r.error
            }
            runOnUiThread {
                AlertDialog.Builder(c).setTitle("Test: " + m.name).setMessage(out).setPositiveButton("Theek hai", null).show()
                rebuild()
            }
        }.start()
    }

    private fun checkUpdate(m: LocalModel) {
        val c = this
        Thread {
            val msg = ModelDownloader.checkUpdate(c, m)
            runOnUiThread {
                val b = AlertDialog.Builder(c).setTitle("Update check").setMessage(msg)
                if (msg.startsWith("Naya version")) {
                    b.setPositiveButton("Update karo") { _, _ ->
                        if (LocalLlm.loadedModelId() == m.id) LocalLlm.unload("update")
                        ModelDownloader.fileFor(c, m).delete()
                        ModelDownloader.partFor(c, m).delete()
                        val e = ModelDownloader.start(c, m)
                        if (e != null) UiKit.toast(c, e)
                        rebuild()
                    }.setNegativeButton("Baad mein", null)
                } else b.setPositiveButton("Theek hai", null)
                b.show()
            }
        }.start()
    }

    private fun addCustomDialog() {
        val c = this
        val l = android.widget.LinearLayout(c)
        l.orientation = android.widget.LinearLayout.VERTICAL
        l.setPadding(UiKit.dp(c, 16), UiKit.dp(c, 8), UiKit.dp(c, 16), 0)
        val name = android.widget.EditText(c).apply { hint = "Naam (optional)" }
        val url = android.widget.EditText(c).apply { hint = "https://.../model.litertlm" }
        l.addView(name); l.addView(url)
        AlertDialog.Builder(c).setTitle("Custom model").setView(l)
            .setPositiveButton("Jodo") { _, _ ->
                val e = LocalModelCatalog.addCustom(c, name.text.toString(), url.text.toString(), url.text.toString().contains("huggingface.co"))
                if (e != null) UiKit.toast(c, e)
                rebuild()
            }.setNegativeButton("Nahi", null).show()
    }
}
