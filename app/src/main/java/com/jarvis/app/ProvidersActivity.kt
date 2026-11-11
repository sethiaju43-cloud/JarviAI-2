package com.jarvis.app

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog

/** Online AI providers (Gemini zaroori nahi), API keys, model chunav, availability check, search tools */
class ProvidersActivity : ScreenActivity() {

    override fun screenTitle() = "Online AI Providers"

    override fun build() {
        val c = this
        val online = NetworkMonitor.isOnline(c)
        container.addView(UiKit.text(c, if (online) "🟢 Online" else "🔴 Offline: provider check/search abhi nahi chalenge", 13f, dim = true))
        container.addView(UiKit.text(c,
            "Koi bhi ek provider kaafi hai. Key phone ke secure storage mein encrypted rehti hai, kabhi code ya logs mein nahi. Free limit provider ke rules par hai, unlimited ki guarantee nahi.",
            12f, dim = true))
        container.addView(UiKit.text(c,
            "⚡ Smart: roz ki chhoti baat-cheet sabse tez model se hoti hai (app khud naapta hai kaun tez hai). " +
                "Mushkil sawaal, code ya 'soch ke batao' bolne par sabse tagda model chalta hai.",
            12f, dim = true))
        container.addView(UiKit.button(c,
            "Auto order: " + (if (ProviderRegistry.strongFirst(c)) "Tagda model pehle, phir halka" else "Tez/halka model pehle, phir tagda") +
                "  (badalne ke liye dabao)") {
            ProviderRegistry.setStrongFirst(c, !ProviderRegistry.strongFirst(c)); rebuild()
        })
        container.addView(UiKit.button(c, "🎙 Natural awaaz (ladki) sunkar chuno") {
            startActivity(Intent(c, VoiceStudioActivity::class.java))
        })

        container.addView(UiKit.section(c, "🏷 AI ka naam"))
        container.addView(UiKit.text(c,
            "AI ko jo naam dena chahte ho yahan likho. Online aur offline dono model yahi naam bolenge.",
            12f, dim = true))
        val nameEdit = UiKit.input(c, "Jarvi", Prefs.aiName(c))
        container.addView(nameEdit)
        container.addView(UiKit.button(c, "💾 Naam save karo") {
            Prefs.setAiName(c, nameEdit.text.toString())
            UiKit.toast(c, "Naam save ho gaya ✅")
            rebuild()
        })

        for (p in ProviderRegistry.presets) container.addView(providerCard(p, online))

        container.addView(UiKit.section(c, "🔎 Internet Search tools"))
        container.addView(UiKit.text(c,
            "Gemini ka search built-in hai. Doosre providers ke liye yahan koi search tool chuno. Tavily/Brave ke liye apni key chahiye (free plan mil sakta hai, provider ke rules par). Wikipedia bina key chalta hai par sirf encyclopedia jaankari deta hai, news/live nahi.",
            12f, dim = true))
        val names = listOf("auto" to "Auto", "tavily" to "Tavily", "brave" to "Brave", "wikipedia" to "Wikipedia (bina key)", "off" to "Band")
        val cur = names.firstOrNull { it.first == Prefs.searchProvider(c) }?.second ?: "Auto"
        container.addView(UiKit.button(c, "Search tool: $cur") {
            AlertDialog.Builder(c).setTitle("Search tool")
                .setItems(names.map { it.second }.toTypedArray()) { _, i ->
                    Prefs.setStr(c, "search_provider", names[i].first); rebuild()
                }.show()
        })
        for ((id, label, url) in listOf(
            Triple("tavily", "Tavily API key", "https://app.tavily.com"),
            Triple("brave", "Brave Search API key", "https://brave.com/search/api/")
        )) {
            val has = ApiKeyStore.hasSecret(c, "search_$id")
            container.addView(UiKit.text(c, label + (if (has) "  ✅ saved" else "  (nahi hai)"), 13f))
            val e = UiKit.input(c, if (has) "naya key daalo to badal jayegi" else "key", password = true)
            container.addView(e)
            val r = UiKit.row(c)
            r.addView(UiKit.rowButton(c, "Save") {
                if (e.text.isNotBlank()) { ApiKeyStore.setSecret(c, "search_$id", e.text.toString()); UiKit.toast(c, "Save ho gaya ✅"); rebuild() }
            })
            r.addView(UiKit.rowButton(c, "Hatao") { ApiKeyStore.setSecret(c, "search_$id", ""); rebuild() })
            r.addView(UiKit.rowButton(c, "Key kahan?") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) })
            container.addView(r)
        }
    }

    private fun providerCard(p: ProviderPreset, online: Boolean): android.view.View {
        val c = this
        val card = UiKit.card(c)
        card.addView(UiKit.text(c, p.name, 16f, true))
        val plat = ProviderRegistry.platformName(c, p.id)
        if (p.custom && plat.isNotBlank()) {
            card.addView(UiKit.text(c, "🔎 Platform: $plat (key se khud pehchana)", 13f))
        }
        card.addView(UiKit.text(c, p.freeNote, 12f, dim = true))

        card.addView(UiKit.switchRow(c, "Use karo", ProviderRegistry.enabled(c, p.id)) { ProviderRegistry.setEnabled(c, p.id, it) })
        card.addView(UiKit.button(c, "Kaam: " + ProviderRegistry.roleLabel(ProviderRegistry.role(c, p.id)) + "  (badalne ke liye dabao)") {
            ProviderRegistry.setRole(c, p.id, ProviderRegistry.nextRole(ProviderRegistry.role(c, p.id))); rebuild()
        })
        if (!p.custom) {
            card.addView(UiKit.switchRow(c, "🤖 Models khud chuno (automatic)", ProviderRegistry.auto(c, p.id)) {
                ProviderRegistry.setAuto(c, p.id, it); rebuild()
            })
            card.addView(UiKit.text(c, "Order: " + ProviderRegistry.chain(c, p.id).joinToString("  →  "), 12f, dim = true))
        }

        val baseBefore = ProviderRegistry.baseUrl(c, p.id)
        val detectedBefore = plat.isNotBlank()
        var baseEdit: android.widget.EditText? = null
        if (p.custom) {
            card.addView(UiKit.text(c, "Server ka link: KHALI chhodo (key se app khud pehchan lega). Apna server ho to https:// link daalo.", 12f, dim = true))
            baseEdit = UiKit.input(c, "khali = khud pehchanega", baseBefore)
            card.addView(baseEdit)
            val picked = ProviderRegistry.chain(c, p.id)
            card.addView(UiKit.text(c,
                if (ProviderRegistry.model(c, p.id).isNotBlank()) "Model: aapka likha hua use hoga"
                else if (picked.isEmpty()) "Model: khud pakdega (key daal kar Save ya 'Check + models' dabao)"
                else "Model (khud pakde): " + picked.joinToString("  →  "),
                12f, dim = true))
        }
        val hasKey = ProviderRegistry.key(c, p.id).isNotBlank()
        card.addView(UiKit.text(c,
            "API key: " + (if (hasKey) "✅ saved" else if (p.custom) "yahan kisi bhi platform ki key paste karo" else "❌ nahi hai"), 13f))
        val keyEdit = UiKit.input(c, if (hasKey) "naya key daalo to badal jayegi" else "API key", password = true)
        card.addView(keyEdit)
        if (p.custom) {
            card.addView(UiKit.text(c, "Model (OPTIONAL: khali chhodo to app khud server se pakad lega)", 12f, dim = true))
        } else {
            card.addView(UiKit.text(c, "Model (automatic band ho tab; ya comma se apni poori line likho)", 12f, dim = true))
        }
        val modelEdit = UiKit.input(c, if (p.custom) "khali = khud pakdega" else "model ka naam", ProviderRegistry.model(c, p.id))
        card.addView(modelEdit)
        if (p.kind != ProviderKind.GEMINI) {
            card.addView(UiKit.switchRow(c, "Is model mein photo samajhne (vision) ki shakti hai", ProviderRegistry.vision(c, p.id)) {
                ProviderRegistry.setVision(c, p.id, it)
            })
        }

        val status = ProviderRegistry.lastCheck[p.id] ?: ""
        val rests = ModelRouter.restLines(c, p.id)
        val sb = StringBuilder()
        sb.append(
            if (ProviderRegistry.isConfigured(c, p.id)) "✅ Router ke liye available"
            else if (p.custom && hasKey) "⛔ Platform abhi pehchana nahi (Save ya 'Check + models' dabao)"
            else if (p.custom) "⛔ Available nahi (key paste karo, ya server ka https:// link daalo)"
            else "⛔ Available nahi (key/model/URL poora karo)"
        )
        if (status.isNotBlank()) sb.append("\n").append(status)
        for (rl in rests) sb.append("\n⏳ Abhi rest par: ").append(rl)
        card.addView(UiKit.text(c, sb.toString(), 12f))

        fun saveAll() {
            val keyChanged = keyEdit.text.isNotBlank()
            if (keyChanged) ProviderRegistry.setKey(c, p.id, keyEdit.text.toString())
            // Custom mein Model box khali chhodna allowed hai (khud pakdega); baaki mein khali ho to purana rahega
            if (p.custom || modelEdit.text.isNotBlank()) ProviderRegistry.setModel(c, p.id, modelEdit.text.toString())
            val be = baseEdit
            if (be != null) {
                val u = ProviderRegistry.normalizeBase(be.text.toString())
                val userEditedLink = u != baseBefore
                // Khud pehchane platform ki nayi key aayi aur link user ne nahi badla: purana link wapas mat likho
                val skip = detectedBefore && keyChanged && !userEditedLink
                if (!skip) {
                    if (u.isNotEmpty() && !Net.isHttps(u)) UiKit.toast(c, "Sirf https:// link allowed hai") else ProviderRegistry.setBaseUrl(c, p.id, u)
                }
            }
        }

        val r = UiKit.row(c)
        r.addView(UiKit.rowButton(c, "💾 Save") {
            saveAll()
            if (p.custom && ProviderRegistry.baseUrl(c, p.id).isBlank() && ProviderRegistry.key(c, p.id).isNotBlank()) {
                // Link khali hai, key hai: platform khud pehchano
                UiKit.toast(c, "Platform pehchan raha hoon...")
                Thread {
                    val res = ProviderRegistry.autoDetect(c, p.id)
                    runOnUiThread {
                        if (res != null && res.ok) UiKit.toast(c, "✅ " + res.message)
                        else if (res != null) {
                            AlertDialog.Builder(c).setTitle(p.name).setMessage("❌ " + res.message)
                                .setPositiveButton("Theek hai", null).show()
                        }
                        rebuild()
                    }
                }.start()
            } else {
                UiKit.toast(c, "Save ho gaya ✅")
                rebuild()
            }
        })
        r.addView(UiKit.rowButton(c, if (online) "🔍 Check + models" else "Offline") {
            if (!online) { UiKit.toast(c, "Internet nahi hai"); return@rowButton }
            saveAll()
            UiKit.toast(c, "Check ho raha hai...")
            Thread {
                // Custom slot mein link khali ho to pehle key se platform pehchano, warna seedha check
                val res = ProviderRegistry.autoDetect(c, p.id) ?: ProviderRegistry.check(c, p.id)
                runOnUiThread {
                    if (p.custom && res.ok && res.models.isNotEmpty()) {
                        // Custom: koi dialog nahi, app ne khud model chun liye
                        UiKit.toast(c, "✅ " + res.models.size + " models mile, khud chun liye")
                        rebuild()
                    } else if (res.ok && res.models.isNotEmpty()) pickModel(p, res.models) else {
                        val extra = if (p.custom) "\n\nAgar server /models list nahi deta to upar Model box mein model ka naam khud likho aur Save dabao." else ""
                        AlertDialog.Builder(c).setTitle(p.name).setMessage((if (res.ok) "Server theek hai par models ki list khali aayi." else "❌ ") + res.message + extra)
                            .setPositiveButton("Theek hai", null).show()
                        rebuild()
                    }
                }
            }.start()
        })
        card.addView(r)
        if (hasKey || p.custom) {
            card.addView(UiKit.button(c, "🧪 Chhota test (kitni der lagi, kya error)") {
                UiKit.toast(c, "Test ho raha hai...")
                Thread {
                    val m = ProviderRegistry.ping(c, p.id)
                    runOnUiThread {
                        AlertDialog.Builder(c).setTitle(p.name).setMessage(m)
                            .setPositiveButton("Theek hai", null).show()
                        rebuild()
                    }
                }.start()
            })
        }
        if (hasKey) card.addView(UiKit.button(c, "🗑 Key hatao") { ProviderRegistry.setKey(c, p.id, ""); rebuild() })
        if (p.keyUrl.isNotBlank()) card.addView(UiKit.button(c, "🔑 Key kahan milegi") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyUrl))) })
        return card
    }

    private fun pickModel(p: ProviderPreset, models: List<String>) {
        val c = this
        val list = models.take(200)
        AlertDialog.Builder(c).setTitle(p.name + ": " + models.size + " models mile (chuno)")
            .setItems(list.toTypedArray()) { _, i ->
                ProviderRegistry.setModel(c, p.id, list[i]); rebuild()
            }.setNegativeButton("Band", null).setOnDismissListener { rebuild() }.show()
    }
}
