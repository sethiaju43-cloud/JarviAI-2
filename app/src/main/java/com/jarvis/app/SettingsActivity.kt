package com.jarvis.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class SettingsActivity : ScreenActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceInfo: android.widget.TextView? = null

    override fun screenTitle() = "Settings"

    private val routeNames = listOf(
        RouteModes.AUTO to "Auto (online pehle, na chale to local)",
        RouteModes.ONLINE to "Sirf online",
        RouteModes.LOCAL_FIRST to "Local pehle (privacy/offline)",
        RouteModes.LOCAL_ONLY to "Sirf local (internet kabhi nahi)"
    )

    override fun build() {
        val c = this
        if (tts == null) {
            tts = TextToSpeech(this, TextToSpeech.OnInitListener { st ->
                ttsReady = st == TextToSpeech.SUCCESS
                refreshVoiceInfo()
            })
        }
        val sp = Prefs.sp(this)

        container.addView(UiKit.button(c, "\uD83C\uDFA8 Design / Theme / Wallpaper badlo") {
            startActivity(Intent(c, DesignActivity::class.java))
        })

        // ---------- AI ----------
        container.addView(UiKit.section(c, "🤖 AI aur Models"))
        container.addView(UiKit.text(c, ModelRouter.statusLine(c), 13f, dim = true))
        container.addView(UiKit.button(c, "☁️ Online AI providers + API keys + Search") {
            startActivity(Intent(c, ProvidersActivity::class.java))
        })
        container.addView(UiKit.button(c, "📊 AI Dashboard (local vs online, bachat)") {
            startActivity(Intent(c, DashboardActivity::class.java))
        })
        val cur = routeNames.firstOrNull { it.first == Prefs.routeMode(c) }?.second ?: ""
        container.addView(UiKit.button(c, "🔀 Model chunav tareeka: $cur") {
            val ids = routeNames.map { it.second }.toTypedArray()
            AlertDialog.Builder(c).setTitle("Model kaise chune?")
                .setSingleChoiceItems(ids, routeNames.indexOfFirst { it.first == Prefs.routeMode(c) }) { d, i ->
                    Prefs.setStr(c, "route_mode", routeNames[i].first)
                    d.dismiss()
                    rebuild()
                }.show()
        })
        container.addView(UiKit.switchRow(c, "Mushkil jawab ko double-check karo (online, zyada quota lagta hai)", Prefs.doubleCheck(c)) {
            Prefs.setBool(c, "double_check", it)
        })
        container.addView(UiKit.switchRow(c, "Jarvi mujhe pyaar se 'jaan' bulaye", Prefs.callJaan(c)) {
            Prefs.setBool(c, "call_jaan", it)
        })

        // ---------- Voice ----------
        container.addView(UiKit.section(c, "🎤 Voice"))
        val rgLang = RadioGroup(c)
        val rbHi = RadioButton(c).apply { text = "Hindi / Hinglish"; id = 501; setTextColor(ContextCompat.getColor(c, R.color.text_white)) }
        val rbEn = RadioButton(c).apply { text = "English"; id = 502; setTextColor(ContextCompat.getColor(c, R.color.text_white)) }
        val rbOr = RadioButton(c).apply { text = "Odia (\u0B13\u0B21\u0B3C\u0B3F\u0B06), internet chahiye"; id = 503; setTextColor(ContextCompat.getColor(c, R.color.text_white)) }
        rgLang.addView(rbHi); rgLang.addView(rbEn); rgLang.addView(rbOr)
        when (sp.getString("lang", "hi-IN")) {
            "en-IN" -> rbEn.isChecked = true
            "or-IN" -> rbOr.isChecked = true
            else -> rbHi.isChecked = true
        }
        rgLang.setOnCheckedChangeListener { _, id ->
            val picked = when (id) {
                502 -> "en-IN"
                503 -> "or-IN"
                else -> "hi-IN"
            }
            sp.edit().putString("lang", picked).apply()
            refreshVoiceInfo()
            testVoice()
        }
        container.addView(rgLang)

        val saved = VoiceHelper.index(c)
        val rgVoice = RadioGroup(c)
        for (i in VoiceHelper.names.indices) {
            val rb = RadioButton(c)
            rb.id = 1000 + i
            rb.text = VoiceHelper.names[i]
            rb.setTextColor(ContextCompat.getColor(c, R.color.text_white))
            rgVoice.addView(rb)
            if (i == saved) rb.isChecked = true
        }
        rgVoice.setOnCheckedChangeListener { _, id ->
            sp.edit().putInt("voice_idx", id - 1000).apply()
            testVoice()
        }
        container.addView(rgVoice)
        container.addView(UiKit.button(c, "▶ Awaaz sun ke dekho") { testVoice() })
        val info = UiKit.text(c, "", 12f, dim = true)
        voiceInfo = info
        container.addView(info)
        container.addView(UiKit.text(c, VoiceInput.capabilityText(c), 12f, dim = true))
        container.addView(UiKit.button(c, "📥 Offline voice pack (sunne ke liye) download karo") {
            UiKit.toast(c, VoicePack.openRecognitionDownload(c))
        })
        container.addView(UiKit.button(c, "🔊 Offline awaaz (bolne ke liye) download karo") {
            UiKit.toast(c, VoicePack.openTtsData(c))
        })
        val vosk = UiKit.text(c, VoskModels.statusText(c), 12f, dim = true)
        container.addView(vosk)
        container.addView(UiKit.button(c, "🎙 Offline Hindi + English mic download karo (~80 MB)") {
            if (VoskModels.busy) {
                UiKit.toast(c, "Download pehle se chal raha hai")
            } else {
                vosk.text = "Shuru ho raha hai..."
                Thread {
                    val err = VoskModels.installAll(applicationContext) { msg ->
                        runOnUiThread { vosk.text = msg }
                    }
                    runOnUiThread {
                        vosk.text = VoskModels.statusText(c)
                        UiKit.toast(c, err ?: "Offline Hindi + English mic taiyaar ✅")
                    }
                }.start()
            }
        })
        container.addView(UiKit.switchRow(c, "Jawab bolkar sunao (chat mein)", Prefs.speakReplies(c)) { Prefs.setBool(c, "speak_replies", it) })
        container.addView(UiKit.switchRow(c, "Bolne ke baad seedha bhej do (warna pehle text dikhega)", Prefs.voiceAutoSend(c)) { Prefs.setBool(c, "voice_autosend", it) })
        container.addView(UiKit.switchRow(c, "Sirf offline voice (device par, internet nahi)", Prefs.voiceOffline(c)) { Prefs.setBool(c, "voice_offline", it) })
        container.addView(UiKit.text(c, "Mic sirf tab chalta hai jab aap button dabate ho. Background mein chupke se kabhi nahi sunta. (Wake-word / hamesha sunna: pending, jaan-boojhkar nahi banaya.)", 12f, dim = true))
        refreshVoiceInfo()

        // ---------- Assistant behaviour ----------
        container.addView(UiKit.section(c, "💬 Assistant"))
        container.addView(UiKit.switchRow(c, "Floating Jarvi character", sp.getBoolean("floating", true)) { on ->
            sp.edit().putBoolean("floating", on).apply()
            if (on) {
                if (Settings.canDrawOverlays(c)) {
                    ContextCompat.startForegroundService(c, Intent(c, FloatingService::class.java))
                } else {
                    UiKit.toast(c, "Pehle 'Display over other apps' allow karo")
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            } else stopService(Intent(c, FloatingService::class.java))
        })
        container.addView(UiKit.switchRow(c, "Khud se bolna (idle talk)", sp.getBoolean("idle_talk", true)) { sp.edit().putBoolean("idle_talk", it).apply() })
        container.addView(UiKit.switchRow(c, "Chup rehne par poochna (check-in)", sp.getBoolean("checkin", true)) { sp.edit().putBoolean("checkin", it).apply() })
        container.addView(UiKit.switchRow(c, "SMS/call/WhatsApp bhejne se pehle poochna", sp.getBoolean("confirm_send", true)) { sp.edit().putBoolean("confirm_send", it).apply() })
        container.addView(UiKit.switchRow(c, "Phone chalu hone par khud shuru", sp.getBoolean("auto_start", false)) { sp.edit().putBoolean("auto_start", it).apply() })
        container.addView(UiKit.text(c, "WhatsApp country code (10 ank wale number ke aage)", 13f, dim = true))
        val cc = UiKit.input(c, "91", sp.getString("country_code", "91") ?: "91")
        cc.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {
                sp.edit().putString("country_code", (s?.toString() ?: "").filter { it.isDigit() }.take(4)).apply()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        container.addView(cc)
        container.addView(UiKit.button(c, "🔐 Permissions") { startActivity(Intent(c, PermissionsActivity::class.java)) })

        // ---------- Dikhawat ----------
        container.addView(UiKit.section(c, "\uD83C\uDFA8 Dikhawat"))
        container.addView(UiKit.button(c, "\uD83C\uDFA8 Design badlo: theme, bijli animation, wallpaper, DP") {
            startActivity(Intent(c, DesignActivity::class.java))
        })
        container.addView(UiKit.text(c, "25 theme, 17 animation (bijli, aurora, galaxy...), gallery wallpaper aur DP frame. Battery Saver ON ho to animation apne aap ruk jati hai.", 12f, dim = true))

        // ---------- Memory & privacy ----------
        container.addView(UiKit.section(c, "🧠 Memory aur Privacy"))
        container.addView(UiKit.switchRow(c, "Long-term memory chalu", Prefs.memoryEnabled(c)) { Prefs.setBool(c, "memory_enabled", it) })
        container.addView(UiKit.button(c, "📝 Memory dekho / badlo / hatao") { startActivity(Intent(c, MemoryActivity::class.java)) })
        container.addView(UiKit.button(c, "🕘 Chat history") { startActivity(Intent(c, HistoryActivity::class.java)) })
        container.addView(UiKit.switchRow(c, "Screen ka text AI ko bhejna allow (band = screen padhna band)", Prefs.screenToAi(c)) { Prefs.setBool(c, "screen_to_ai", it) })
        container.addView(UiKit.button(c, "🗑 Abhi ki chat saaf karo") {
            ChatStore.clear(c); Brain.resetWorking(c); UiKit.toast(c, "Chat saaf ho gayi")
        })
        container.addView(UiKit.button(c, "⚠️ Saara local data mitao") {
            AlertDialog.Builder(c).setTitle("Sab mitana hai?")
                .setMessage("Chat, history, memory aur saari API keys hat jayengi. Downloaded models rahenge (Models screen se hatao).")
                .setPositiveButton("Haan, mita do") { _, _ ->
                    ChatStore.clear(c); ChatStore.clearArchive(c); Brain.clearMemory(c)
                    ApiKeyStore.wipeAll(c)
                    UiKit.toast(c, "Sab mita diya")
                    rebuild()
                }.setNegativeButton("Nahi", null).show()
        })

        // ---------- Storage ----------
        container.addView(UiKit.section(c, "💾 Storage usage"))
        val snap = DeviceInfo.snapshot(c)
        container.addView(UiKit.text(c,
            "Local models: " + ModelDownloader.totalInstalledMb(c) + " MB\n" +
                "Chat + history: " + ChatStore.sizeBytes(c) / 1024 + " KB\n" +
                "Memory: " + MemoryStore.sizeBytes(c) / 1024 + " KB\n" +
                "Phone mein khali jagah: " + snap.freeStorageMb + " MB", 13f, dim = true))

        container.addView(UiKit.button(c, "⏹ Jarvis band karo") {
            stopService(Intent(c, FloatingService::class.java)); finishAffinity()
        })
    }

    private fun testVoice() {
        val t = tts
        if (t == null || !ttsReady) { UiKit.toast(this, "Awaaz abhi taiyaar nahi, thodi der baad try karo"); return }
        val en = VoiceHelper.speakLang(this, "") == "en"
        val sample = if (en) "Hello, I am Jarvi. How does my voice sound?" else "Hello, main Jarvi hun. Meri awaaz kaisi lag rahi hai?"
        VoiceHelper.apply(this, t, sample)
        t.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "test")
    }

    private fun refreshVoiceInfo() {
        val t = tts
        val v = voiceInfo
        if (t == null || !ttsReady || v == null) return
        val lang = VoiceHelper.speakLang(this, "")
        val name = if (lang == "en") "English" else "Hindi"
        val st = VoiceHelper.voiceStats(t, lang)
        val how = "Zyada awaaz ke liye phone Settings → Text-to-speech → Google engine mein $name voice data download karo."
        val odiaNote = if (Prefs.sp(this).getString("lang", "hi-IN") == "or-IN")
            "Odia chuni hai: mic Odia sunega (internet chahiye, offline mic sirf Hindi/English). " +
                "Jawab Odia akshar mein ho to Odia awaaz (agar phone mein ho) mein bolega, warna theek se nahi bol payega. "
        else ""
        v.text = odiaNote + (if (st.first + st.second > 0)
            "$name awaaz: " + st.first + " offline (internet ke bina) + " + st.second + " online (internet chahiye). " + how
        else "$name awaaz nahi mili. $how")
    }

    override fun onDestroy() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        super.onDestroy()
    }
}
