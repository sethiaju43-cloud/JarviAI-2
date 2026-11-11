package com.jarvis.app

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/**
 * AI Usage Dashboard: kitni request local gayi, kitni online, kitni online call bachi.
 * "Asli ginti" aur "andaaza" alag likhe jate hain, taaki jhoota hisaab na lage.
 */
class DashboardActivity : ScreenActivity() {

    private var days = 1

    override fun screenTitle() = "AI Dashboard"

    private fun usd(v: Double): String = String.format(Locale.US, "$%.4f", v)

    private fun num(v: Long): String = String.format(Locale.US, "%,d", v)

    override fun build() {
        val c = this

        // ---------- Abhi ki sthiti ----------
        val online = NetworkMonitor.isOnline(c)
        val snap = DeviceInfo.snapshot(c)
        container.addView(UiKit.section(c, "📡 Abhi ki sthiti"))
        val st = UiKit.card(c)
        st.addView(UiKit.text(c, if (online) "🟢 Internet: chalu" else "🔴 Internet: nahi (offline mode)", 14f, true))
        st.addView(UiKit.text(c, "Model chunav tareeka: " + routeLabel(Prefs.routeMode(c)), 13f, dim = true))
        st.addView(UiKit.text(c,
            "RAM: " + snap.availRamMb + " / " + snap.totalRamMb + " MB khali" +
                (if (snap.batteryPct >= 0) "\nBattery: " + snap.batteryPct + "%" + (if (snap.charging) " (charging)" else "") + ", " + snap.tempC + " C" else "") +
                "\nStorage khali: " + snap.freeStorageMb + " MB", 13f, dim = true))
        container.addView(st)

        // ---------- Local models ----------
        container.addView(UiKit.section(c, "📱 Local models (offline)"))
        val installed = try { LocalModelCatalog.all(c).filter { ModelDownloader.isInstalled(c, it) } } catch (e: Exception) { emptyList() }
        val lm = UiKit.card(c)
        if (installed.isEmpty()) {
            lm.addView(UiKit.text(c, "Abhi koi local model installed nahi. Settings → Local AI models se download karo; tab online calls kam hongi.", 13f, dim = true))
        } else {
            val loaded = try { LocalLlm.loadedModelId() } catch (e: Exception) { null }
            for (m in installed) {
                lm.addView(UiKit.text(c, "✅ " + m.name + " (" + m.sizeMb + " MB)" + (if (m.id == loaded) " · abhi memory mein" else ""), 13f))
            }
        }
        container.addView(lm)

        // ---------- Samay chuno ----------
        container.addView(UiKit.section(c, "📊 Hisaab"))
        val r = UiKit.row(c)
        r.addView(UiKit.rowButton(c, (if (days == 1) "● " else "") + "Aaj") { days = 1; rebuild() })
        r.addView(UiKit.rowButton(c, (if (days == 7) "● " else "") + "7 din") { days = 7; rebuild() })
        r.addView(UiKit.rowButton(c, (if (days == 30) "● " else "") + "30 din") { days = 30; rebuild() })
        container.addView(r)

        val s = UsageStats.snapshot(c, days)
        val rate = UsageStats.rate(c)

        // ---------- Asli ginti ----------
        container.addView(UiKit.section(c, "✅ Asli ginti (measured)"))
        val real = UiKit.card(c)
        real.addView(UiKit.text(c, "Kul AI requests: " + num(s.totalCalls), 15f, true))
        real.addView(UiKit.text(c, "📱 Local (phone par): " + num(s.localCalls), 14f))
        real.addView(UiKit.text(c, "☁️ Online (cloud): " + num(s.cloudCalls), 14f))
        real.addView(UiKit.text(c, "⚠️ Online jo fail hui: " + num(s.failedCalls), 13f, dim = true))
        if (s.totalCalls > 0) {
            val pct = (s.localCalls * 100 / s.totalCalls).toInt()
            val p = UiKit.progress(c)
            p.progress = pct
            real.addView(p)
            real.addView(UiKit.text(c, "$pct% jawab phone par hi bane", 12f, dim = true))
        }
        real.addView(UiKit.text(c, "🔁 Duplicate request roki (double-tap): " + num(s.duplicatesAvoided), 13f, dim = true))
        if (s.cloudHistoryCapped) {
            real.addView(UiKit.text(c, "Dhyan: online calls ki ginti sirf pichhle 7 din ki rakhi jati hai, isliye 30 din mein ye kam dikh sakti hai.", 11f, dim = true))
        }
        container.addView(real)

        // ---------- Budget ----------
        container.addView(UiKit.section(c, "🚦 Online budget (limit)"))
        val bud = UiKit.card(c)
        val b = Budget.state(c)
        if (b.dailyLimit > 0) {
            bud.addView(UiKit.text(c, levelIcon(b.dailyLevel) + " Aaj: " + num(b.callsToday) + " / " + num(b.dailyLimit.toLong()) + " online calls", 14f))
            val pb = UiKit.progress(c)
            pb.progress = (b.callsToday * 100 / b.dailyLimit).toInt().coerceIn(0, 100)
            bud.addView(pb)
        } else {
            bud.addView(UiKit.text(c, "Roz ki call limit: band", 13f, dim = true))
        }
        if (b.monthlyLimit > 0.0) {
            bud.addView(UiKit.text(c, levelIcon(b.monthlyLevel) + " 30 din ka kharch (andaaza): " + usd(b.usdMonth) + " / " + usd(b.monthlyLimit), 14f))
            val pm = UiKit.progress(c)
            pm.progress = (b.usdMonth / b.monthlyLimit * 100).toInt().coerceIn(0, 100)
            bud.addView(pm)
        } else {
            bud.addView(UiKit.text(c, "Mahine ki kharch limit: band", 13f, dim = true))
        }
        bud.addView(UiKit.text(c,
            "Limit poori hone par online model band ho jata hai aur local model (agar installed ho) se jawab aata hai. " +
                "80% par ek baar chetavni milti hai. Kharch asli bill nahi, tokens × aapka rate ka andaaza hai. 0 = limit band.", 11f, dim = true))
        bud.addView(UiKit.button(c, "✏️ Roz ki call limit") {
            numberDialog("Roz ki online calls (0 = band)", Budget.dailyCallLimit(c).toString(), false) { v ->
                Budget.setDailyCallLimit(c, v.toInt())
            }
        })
        bud.addView(UiKit.button(c, "✏️ Mahine ki kharch limit (USD andaaza)") {
            numberDialog("30 din ka kharch, USD (0 = band)", Budget.monthlyUsdLimit(c).toString(), true) { v ->
                Budget.setMonthlyUsdLimit(c, v)
            }
        })
        container.addView(bud)

        // ---------- Andaaza ----------
        container.addView(UiKit.section(c, "🧮 Andaaza (estimate)"))
        val est = UiKit.card(c)
        est.addView(UiKit.text(c, "Online call bachi: ~" + num(s.callsAvoided), 14f))
        est.addView(UiKit.text(c, "Tokens bache: ~" + num(s.tokensSaved), 14f))
        est.addView(UiKit.text(c, "Paise bache: ~" + usd(s.moneySavedUsd(rate)), 14f))
        est.addView(UiKit.text(c, "Online mein kharch hue tokens: ~" + num(s.tokensSpentCloud) + " (~" + usd(s.moneySpentUsd(rate)) + ")", 13f, dim = true))
        est.addView(UiKit.text(c,
            "Ye andaaze hain: token = text ki lambai / 4 (Hindi mein asli token zyada hote hain). " +
                "Paise = tokens × aapka rate ($" + rate + " per 10 lakh tokens). Har provider ka asli rate alag hota hai. " +
                "Local model bhi bilkul muft nahi: ye phone ki CPU, RAM aur battery use karta hai.", 11f, dim = true))
        est.addView(UiKit.button(c, "✏️ Rate badlo") { rateDialog() })
        container.addView(est)

        // ---------- Provider-wise ----------
        container.addView(UiKit.section(c, "☁️ Provider ke hisaab se (online)"))
        val pv = UiKit.card(c)
        if (s.cloudByProvider.isEmpty()) pv.addView(UiKit.text(c, "Koi online call nahi.", 13f, dim = true))
        for ((k, v) in s.cloudByProvider.entries.sortedByDescending { it.value }) {
            val f = s.failedByProvider[k] ?: 0L
            pv.addView(UiKit.text(c, k + ": " + num(v) + (if (f > 0) " (fail: $f)" else ""), 13f))
        }
        container.addView(pv)

        // ---------- Model-wise ----------
        container.addView(UiKit.section(c, "📱 Model ke hisaab se (local)"))
        val mv = UiKit.card(c)
        if (s.localByModel.isEmpty()) mv.addView(UiKit.text(c, "Koi local jawab nahi.", 13f, dim = true))
        for ((k, v) in s.localByModel.entries.sortedByDescending { it.value }) {
            mv.addView(UiKit.text(c, k + ": " + num(v), 13f))
        }
        container.addView(mv)

        // ---------- Jo abhi track nahi hota ----------
        container.addView(UiKit.section(c, "ℹ️ Abhi track nahi hota"))
        container.addView(UiKit.text(c,
            "Speech-to-text, text-to-speech aur image generation ka alag hisaab abhi nahi rakha jata. " +
                "Ye tab judega jab inke naye local engine (voice stages) banenge.", 12f, dim = true))

        container.addView(UiKit.button(c, "🗑 Ye saara hisaab mita do") {
            AlertDialog.Builder(c).setTitle("Hisaab mitana hai?")
                .setPositiveButton("Haan") { _, _ -> UsageStats.clear(c); rebuild() }
                .setNegativeButton("Nahi", null).show()
        })
    }

    private fun levelIcon(l: BudgetLevel): String = when (l) {
        BudgetLevel.EXCEEDED -> "🛑"
        BudgetLevel.WARN -> "⚠️"
        else -> "🟢"
    }

    /** Ek number poochne wala chhota dialog. Galat number par kuch save nahi hota. */
    private fun numberDialog(title: String, current: String, decimal: Boolean, onSave: (Double) -> Unit) {
        val c = this
        val et = EditText(c)
        et.setText(current)
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            (if (decimal) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        AlertDialog.Builder(c).setTitle(title).setView(et)
            .setPositiveButton("Save") { _, _ ->
                val v = et.text.toString().trim().toDoubleOrNull()
                if (v == null || v < 0) UiKit.toast(c, "Sahi number daalo") else { onSave(v); rebuild() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun routeLabel(m: String): String = when (m) {
        RouteModes.ONLINE -> "Sirf online"
        RouteModes.LOCAL_FIRST -> "Local pehle"
        RouteModes.LOCAL_ONLY -> "Sirf local"
        else -> "Auto (online pehle, na chale to local)"
    }

    private fun rateDialog() {
        val c = this
        val et = EditText(c)
        et.setText(UsageStats.rate(c).toString())
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        AlertDialog.Builder(c).setTitle("USD per 10 lakh tokens").setView(et)
            .setPositiveButton("Save") { _, _ ->
                val v = et.text.toString().trim().toDoubleOrNull()
                if (v == null || v < 0) UiKit.toast(c, "Sahi number daalo") else { UsageStats.setRate(c, v); rebuild() }
            }.setNegativeButton("Cancel", null).show()
    }
}
