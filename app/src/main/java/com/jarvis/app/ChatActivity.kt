package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.app.databinding.ActivityChatBinding

/** Main chat screen: online + offline AI ek hi jagah */
class ChatActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var b: ActivityChatBinding
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: MessageAdapter
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceWarned = false
    private var ttsFailed = false
    private var voice: VoiceInput? = null

    // Baat-cheet (hands-free) mode: Jarvi bolna khatam kare to apne aap sunna shuru
    private var autoListening = false
    private var screenResumed = false
    private var autoListenRunnable: Runnable? = null

    // Jawab aane se pehle user ne naya message bheja: purana roka, naya uske baad jayega
    private var queuedText: String? = null

    // Experimental: Jarvi bol rahi ho aur user bole to bolna ruk jaye
    private val bargeIn = BargeIn { runOnUiThread { onBargeIn() } }
    private var netCb: ConnectivityManager.NetworkCallback? = null

    // Kitne jawab abhi aane baaki hain ("..." dikh raha hai)
    private var inFlight = 0
    private var curPh: ChatMessage? = null

    // Jodi hui photo/file (agle message ke saath jayegi)
    private var pendingImage: String? = null
    private var pendingFile: Attachments.FileText? = null

    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startVoice() else Toast.makeText(this, "Mic ki permission ke bina bol nahi sakte 🎤", Toast.LENGTH_LONG).show()
    }
    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) cameraLauncher.launch(null) else Toast.makeText(this, "Camera permission chahiye", Toast.LENGTH_LONG).show()
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attachImageUri(uri)
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) attachBitmap(bmp)
    }
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attachFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)

        tts = TextToSpeech(this, this)
        adapter = MessageAdapter(messages)
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter

        b.btnBack.setOnClickListener { finish() }
        b.btnSend.setOnClickListener { sendFromInput() }
        b.btnMic.setOnClickListener { onMicClicked() }
        b.btnFresh.setOnClickListener { runFreshMind() }
        b.btnMore.setOnClickListener { showMenu(it) }
        b.btnAttach.setOnClickListener { showAttachMenu(it) }
        b.btnStopSpeak.setOnClickListener {
            tts?.stop()
            b.btnStopSpeak.visibility = View.GONE
        }
        b.tvModel.setOnClickListener { startActivity(Intent(this, ModelsActivity::class.java)) }
        b.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput()
                true
            } else false
        }

        voice = VoiceInput(this, object : VoiceInput.Callback {
            override fun onPartial(text: String) {
                b.etInput.setText(text)
                b.etInput.setSelection(text.length)
            }

            override fun onFinal(text: String) {
                b.etInput.setText(text)
                b.etInput.setSelection(text.length)
                autoListening = false
                if (Prefs.voiceAutoSend(this@ChatActivity) || chatModeOn()) {
                    sendFromInput()
                } else {
                    showHint("✏️ Jo suna wo upar likha hai. Theek ho to ➤ dabao, ya badal lo.")
                }
            }

            override fun onError(msg: String) {
                if (autoListening) {
                    // Apne aap shuru hui sunai mein kuch nahi mila: chupchaap ruk jao (loop nahi chalega)
                    autoListening = false
                    showHint("🗣 Kuch sunai nahi diya, baat-cheet ruk gayi. Mic dabakar dobara shuru karo.")
                } else {
                    Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_LONG).show()
                    showHint(null)
                }
            }

            override fun onState(listening: Boolean) {
                b.btnMic.text = if (listening) "🔴" else "🎤"
                if (listening) {
                    val off = Prefs.voiceOffline(this@ChatActivity) && VoiceInput.onDeviceAvailable(this@ChatActivity)
                    showHint("🎤 Sun rahi hun... (" + (if (off) "offline voice" else "online voice") + ") dobara dabao rukne ke liye")
                }
            }
        })

        netCb = NetworkMonitor.register(this) { runOnUiThread { refreshStatus() } }
    }

    override fun onResume() {
        super.onResume()
        screenResumed = true
        if (inFlight == 0) reloadFromStore()
        refreshStatus()
    }

    override fun onPause() {
        screenResumed = false
        // Screen se jaate hi mic aur bolna band, taaki peeche se sunna/bolna na chalta rahe
        cancelAutoListen()
        autoListening = false
        bargeIn.stop()
        if (voice?.listening == true) voice?.cancel()
        tts?.stop()
        super.onPause()
    }

    private fun refreshStatus() {
        if (!::b.isInitialized) return
        val online = NetworkMonitor.isOnline(this)
        b.tvNet.text = if (online) "🟢 Online" else "🔴 Offline"
        Thread {
            val label = if (ModelRouter.lastLabel.isNotBlank() && inFlight == 0 && messages.size > 1)
                ModelRouter.lastLabel else ModelRouter.previewLabel(this)
            runOnUiThread { b.tvModel.text = "Model: $label  (tap: models)" }
        }.start()
    }

    private fun showHint(t: String?) {
        if (t == null) {
            b.tvHint.visibility = View.GONE
        } else {
            b.tvHint.text = t
            b.tvHint.visibility = View.VISIBLE
        }
    }

    private fun reloadFromStore() {
        val saved = ChatStore.load(this)
        messages.clear()
        if (saved.isEmpty()) {
            messages.add(ChatMessage("Haan! Main yahan hun. Bolo, kya karna hai? 😊", false))
        } else {
            messages.addAll(saved)
        }
        adapter.notifyDataSetChanged()
        b.rv.scrollToPosition(messages.size - 1)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val t = tts
            if (t != null) {
                VoiceHelper.apply(this, t)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            b.btnStopSpeak.visibility = View.VISIBLE
                            if (bargeEnabled()) bargeIn.start()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        bargeIn.stop()
                        runOnUiThread {
                            b.btnStopSpeak.visibility = View.GONE
                            afterSpeech()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        bargeIn.stop()
                        runOnUiThread { b.btnStopSpeak.visibility = View.GONE }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        bargeIn.stop()
                        runOnUiThread { b.btnStopSpeak.visibility = View.GONE }
                    }
                })
            }
            ttsReady = true
        } else {
            ttsReady = false
            ttsFailed = true
            showHint("🔇 Is phone mein bolne wala engine (Text-to-speech) nahi chal raha. Jawab sirf likha hua milega.")
        }
    }

    // ---------------- Baat-cheet (hands-free) mode ----------------
    private fun chatModeOn(): Boolean = Prefs.bool(this, "voice_chat_mode", false)

    private fun cancelAutoListen() {
        val r = autoListenRunnable
        if (r != null) b.root.removeCallbacks(r)
        autoListenRunnable = null
    }

    /** Jarvi ne bolna khatam kiya (ya bolne ko kuch nahi tha): mode ON ho to thodi der baad sunna shuru */
    private fun afterSpeech() {
        if (!chatModeOn() || !screenResumed || inFlight > 0 || isFinishing || isDestroyed) return
        cancelAutoListen()
        val r = Runnable {
            autoListenRunnable = null
            startAutoListen()
        }
        autoListenRunnable = r
        // Thoda ruko taaki mic Jarvi ki apni awaaz na pakde
        b.root.postDelayed(r, 600L)
    }

    private fun startAutoListen() {
        val v = voice ?: return
        if (v.listening || inFlight > 0 || !screenResumed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showHint("🎤 Baat-cheet mode ke liye Microphone allow karo (mic button dabao).")
            return
        }
        autoListening = true
        v.start()
    }

    // ---------------- Beech mein tokna (experimental) ----------------
    private fun bargeEnabled(): Boolean =
        Prefs.bool(this, "barge_in", false) && screenResumed &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** User bolne laga: Jarvi chup ho aur sunna shuru kare (purani awaaz naye message par na chale) */
    private fun onBargeIn() {
        if (!screenResumed || isFinishing || isDestroyed) return
        val v = voice ?: return
        if (v.listening) return
        tts?.stop()
        b.btnStopSpeak.visibility = View.GONE
        cancelAutoListen()
        autoListening = true
        v.start()
    }

    private fun toggleBargeIn() {
        val now = !Prefs.bool(this, "barge_in", false)
        Prefs.setBool(this, "barge_in", now)
        if (now) {
            Toast.makeText(
                this,
                "✋ Beech mein tokna ON (experimental). Earphones ke saath behtar chalta hai; speaker par Jarvi ki apni awaaz se galti se trigger ho sakta hai.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            bargeIn.stop()
            Toast.makeText(this, "Beech mein tokna OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleChatMode() {
        val now = !chatModeOn()
        Prefs.setBool(this, "voice_chat_mode", now)
        if (now) {
            Toast.makeText(
                this,
                "🗣 Baat-cheet mode ON: Jarvi bolne ke baad apne aap sunegi (floating Jarvi par bhi). " +
                    "Ye 'sun → samajh → bol → phir sun' ka chakkar hai.",
                Toast.LENGTH_LONG
            ).show()
            if (voice?.listening != true && inFlight == 0) onMicClicked()
        } else {
            cancelAutoListen()
            autoListening = false
            Toast.makeText(this, "Baat-cheet mode OFF", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- Voice ----------------
    private fun onMicClicked() {
        val v = voice ?: return
        if (v.listening) {
            v.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoice()
    }

    private fun startVoice() {
        cancelAutoListen()
        autoListening = false
        tts?.stop()
        voice?.start()
    }

    // ---------------- Attach ----------------
    private fun showAttachMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "🖼 Gallery se photo")
        pm.menu.add(0, 2, 1, "📷 Camera se photo")
        pm.menu.add(0, 3, 2, "📄 Text ya PDF file (summary/sawaal)")
        pm.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1, 2 -> {
                    if (!NetworkMonitor.isOnline(this) || ModelRouter.plan(this, Task.VISION, true).isEmpty()) {
                        Toast.makeText(this, "Photo samajhne ke liye internet aur online vision model (jaise Gemini) chahiye. Local model abhi photo nahi padhta.", Toast.LENGTH_LONG).show()
                    } else if (mi.itemId == 1) {
                        galleryLauncher.launch("image/*")
                    } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(null)
                    } else {
                        camPerm.launch(Manifest.permission.CAMERA)
                    }
                }
                3 -> fileLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/pdf"))
            }
            true
        }
        pm.show()
    }

    private fun attachImageUri(uri: Uri) {
        Thread {
            val b64 = Attachments.imageToBase64(applicationContext, uri)
            runOnUiThread {
                if (b64 == null) Toast.makeText(this, "Photo padh nahi paye", Toast.LENGTH_SHORT).show()
                else {
                    pendingImage = b64
                    pendingFile = null
                    showHint("🖼 Photo jud gayi. Sawaal likho (ya khali chhodo) aur ➤ dabao.")
                }
            }
        }.start()
    }

    private fun attachBitmap(bmp: Bitmap) {
        Thread {
            val b64 = Attachments.bitmapToBase64(bmp)
            runOnUiThread {
                pendingImage = b64
                pendingFile = null
                showHint("📷 Photo jud gayi. Sawaal likho (ya khali chhodo) aur ➤ dabao.")
            }
        }.start()
    }

    private fun attachFile(uri: Uri) {
        Thread {
            if (Attachments.isPdf(applicationContext, uri)) {
                val name = Attachments.displayName(applicationContext, uri)
                val ok = NetworkMonitor.isOnline(applicationContext) &&
                    ModelRouter.plan(applicationContext, Task.VISION, true).isNotEmpty()
                runOnUiThread {
                    if (!ok) {
                        Toast.makeText(this, "PDF padhne ke liye internet aur online vision model (jaise Gemini) chahiye. Local model abhi PDF nahi padhta.", Toast.LENGTH_LONG).show()
                    } else {
                        pendingFile = Attachments.FileText(name, "", uri)
                        pendingImage = null
                        showHint("📄 " + name + " jud gayi. Dhyan: iske pehle 8 pages online AI ko bheje jayenge. Sawaal likho (ya khali chhodo) aur ➤ dabao.")
                    }
                }
                return@Thread
            }
            val f = Attachments.readText(applicationContext, uri)
            runOnUiThread {
                if (f == null) Toast.makeText(this, "Ye file text ya PDF nahi lagti, ye format abhi supported nahi", Toast.LENGTH_LONG).show()
                else {
                    pendingFile = f
                    pendingImage = null
                    showHint("📄 " + f.name + " jud gayi. Batao kya karna hai (summary, rewrite...) aur ➤ dabao.")
                }
            }
        }.start()
    }

    // ---------------- Send ----------------
    private fun sendFromInput() {
        val t = b.etInput.text.toString().trim()
        if (t.isEmpty() && pendingImage == null && pendingFile == null) return
        b.etInput.setText("")
        showHint(null)
        sendMessage(t)
    }

    /** Peeche ke thread par: Haan/Nahi, seedha command, phir AI */
    private fun work(text: String, image: String?, file: Attachments.FileText?): String {
        val ctx = applicationContext
        if (image != null) return Brain.askWithImage(ctx, text, image)
        if (file != null) {
            val pdf = file.pdfUri
            return if (pdf != null) PdfReader.ask(ctx, text, file.name, pdf)
            else Brain.askAboutFile(ctx, text, file.name, file.text)
        }
        if (Brain.hasPending()) {
            val pr = Brain.answerPending(ctx, text)
            if (pr != null) return pr
        }
        val cmd = CommandHandler.handle(ctx, text)
        if (cmd != null) return cmd
        return Brain.askSmart(ctx, listOf(Pair("user", text)))
    }

    private fun sendMessage(text: String) {
        if (inFlight > 0) {
            val low = text.lowercase().trim()
            if (low == "ruko" || low == "ruk" || low == "stop" || low == "bas" || low == "रुको" || low == "स्टॉप") {
                Brain.requestStop()
                PdfReader.requestStop()
                Toast.makeText(this, "Ruk rahi hun 🛑", Toast.LENGTH_SHORT).show()
            } else {
                // Naya message: purana jawab roko (purani awaaz/jawab ab kaam ka nahi), naya uske baad bhejo
                queuedText = text
                Brain.requestStop()
                PdfReader.requestStop()
                tts?.stop()
                Toast.makeText(this, "Purana rok diya, ab ye le rahi hun ⏳", Toast.LENGTH_SHORT).show()
            }
            return
        }
        tts?.stop()
        val image = pendingImage
        val file = pendingFile
        pendingImage = null
        pendingFile = null
        val shown = when {
            image != null -> "🖼 " + text
            file != null -> "📄 " + file.name + (if (text.isNotBlank()) "\n" + text else "")
            else -> text
        }
        addMessage(shown, true)
        addPlaceholder("...")

        ModelRouter.onStatus = { s -> runOnUiThread { setPlaceholder(s) } }
        ModelRouter.onPartial = { p -> if (p.isNotBlank()) runOnUiThread { setPlaceholder(p) } }

        Thread {
            var r: String? = null
            var crashed = false
            Brain.lastReplyWasError = false
            try {
                r = work(text, image, file)
            } catch (e: Throwable) {
                // Chupchaap nigalna nahi: developer ke liye log (key/token chhupakar), user ko saaf message
                crashed = true
                Log.e("JarviChat", "work failed: " + e.javaClass.simpleName + " " + OnlineParsers.redact(e.message ?: ""))
            }
            val wasErr = crashed || Brain.lastReplyWasError
            val reply = r ?: "Kuch dikkat aa gayi 🥺 Dobara bolo"
            ChatStore.append(applicationContext, reply, false)
            ModelRouter.onStatus = null
            ModelRouter.onPartial = null
            runOnUiThread {
                inFlight--
                if (isFinishing || isDestroyed) return@runOnUiThread
                val rr = Brain.lastRoute
                if (rr != null && rr.switched) {
                    Toast.makeText(
                        this,
                        "ℹ️ " + rr.failedBefore.joinToString(", ") + " → ab " + (rr.backend?.label ?: "") + " use hua",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (reply.contains("sab bhool gayi")) {
                    messages.clear()
                    messages.add(ChatMessage(reply, false))
                    adapter.notifyDataSetChanged()
                    speak(reply)
                } else {
                    // Naya message line mein hai to purana (ab bekaar) jawab bolna nahi
                    val hasQueued = queuedText != null
                    // Error ka message bolkar nahi sunate (technical lagta hai), uski jagah chhoti saaf line bolte hain
                    fillPlaceholder(reply, !hasQueued, if (wasErr) Honesty.SPOKEN_ERROR else null)
                }
                refreshStatus()
                checkPendingMemory()
                val q = queuedText
                if (q != null) {
                    queuedText = null
                    sendMessage(q)
                }
            }
        }.start()
    }

    // ---------------- Fresh Mind ----------------
    private fun runFreshMind() {
        if (inFlight > 0) {
            Toast.makeText(this, "Pehle chal raha kaam khatam hone do ⏳", Toast.LENGTH_SHORT).show()
            return
        }
        addPlaceholder("🧠 Fresh Mind: baat-cheet ko dobara dekh rahi hun...")
        ModelRouter.onStatus = { s -> runOnUiThread { setPlaceholder("🧠 $s") } }
        Thread {
            var res: FreshMind.Result? = null
            try {
                res = FreshMind.run(applicationContext)
            } catch (e: Throwable) {
                Log.e("JarviChat", "FreshMind failed: " + e.javaClass.simpleName + " " + OnlineParsers.redact(e.message ?: ""))
            }
            val text = res?.text ?: "Fresh Mind mein dikkat aa gayi 🥺"
            ModelRouter.onStatus = null
            if (res?.ok == true) ChatStore.append(applicationContext, text, false)
            runOnUiThread {
                inFlight--
                if (isFinishing || isDestroyed) return@runOnUiThread
                fillPlaceholder(text, speakIt = false)
                refreshStatus()
                val sum = res?.summary
                if (res?.ok == true && !sum.isNullOrBlank()) {
                    AlertDialog.Builder(this).setTitle("Updated summary save karun?")
                        .setMessage(sum + "\n\n(Ye memory mein 'saar' ki tarah save hoga, jise aap kabhi bhi hata sakte ho.)")
                        .setPositiveButton("Haan, save karo") { _, _ ->
                            MemoryStore.add(this, sum, "summary", auto = false, force = true)
                            Toast.makeText(this, "Memory mein save ho gaya 📝", Toast.LENGTH_SHORT).show()
                        }.setNegativeButton("Nahi", null).show()
                }
            }
        }.start()
    }

    // ---------------- Menu ----------------
    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "➕ Nayi chat")
        pm.menu.add(0, 2, 1, "🕘 Chat history")
        pm.menu.add(0, 3, 2, "📱 Local AI models")
        pm.menu.add(0, 4, 3, "☁️ Online AI providers")
        pm.menu.add(0, 5, 4, "📝 Memory")
        pm.menu.add(0, 6, 5, "⚙️ Settings")
        pm.menu.add(0, 7, 6, "🗑 Chat saaf karo")
        pm.menu.add(
            0, 8, 7,
            if (chatModeOn()) "🗣 Baat-cheet mode: ON (band karo)" else "🗣 Baat-cheet mode: OFF (hands-free shuru karo)"
        )
        pm.menu.add(
            0, 9, 8,
            if (Prefs.bool(this, "barge_in", false)) "✋ Beech mein tokna: ON (experimental)" else "✋ Beech mein tokna: OFF (experimental)"
        )
        pm.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> newChat()
                2 -> startActivity(Intent(this, HistoryActivity::class.java))
                3 -> startActivity(Intent(this, ModelsActivity::class.java))
                4 -> startActivity(Intent(this, ProvidersActivity::class.java))
                5 -> startActivity(Intent(this, MemoryActivity::class.java))
                6 -> startActivity(Intent(this, SettingsActivity::class.java))
                7 -> {
                    if (inFlight == 0) {
                        ChatStore.clear(this)
                        Brain.resetWorking(this)
                        reloadFromStore()
                    }
                }
                8 -> toggleChatMode()
                9 -> toggleBargeIn()
            }
            true
        }
        pm.show()
    }

    private fun newChat() {
        if (inFlight > 0) {
            Toast.makeText(this, "Pehle chal raha kaam khatam hone do ⏳", Toast.LENGTH_SHORT).show()
            return
        }
        ChatStore.archiveCurrent(this)
        Brain.resetWorking(this)
        reloadFromStore()
    }

    // ---------------- Sensitive memory poochna ----------------
    private var askingMemory = false

    private fun checkPendingMemory() {
        if (askingMemory) return
        val list = MemoryStore.pendingList()
        if (list.isEmpty()) return
        val p = list[0]
        askingMemory = true
        AlertDialog.Builder(this).setTitle("Ye yaad rakhun?")
            .setMessage("'" + p.text + "'\n\nYe sensitive lagta hai (" + p.reason + "), isliye aapki haan chahiye.")
            .setPositiveButton("✅ Haan") { _, _ ->
                MemoryStore.approvePending(this, 0)
                askingMemory = false
                checkPendingMemory()
            }
            .setNegativeButton("❌ Nahi") { _, _ ->
                MemoryStore.rejectPending(0)
                askingMemory = false
                checkPendingMemory()
            }
            .setOnCancelListener {
                MemoryStore.rejectPending(0)
                askingMemory = false
            }.show()
    }

    // ---------------- Messages ----------------
    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(ChatMessage(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)
        b.rv.scrollToPosition(messages.size - 1)
        ChatStore.append(this, text, isUser)
    }

    private fun addPlaceholder(text: String) {
        inFlight++
        val p = ChatMessage(text, false)
        curPh = p
        messages.add(p)
        adapter.notifyItemInserted(messages.size - 1)
        b.rv.scrollToPosition(messages.size - 1)
    }

    private fun setPlaceholder(text: String) {
        val p = curPh ?: return
        val idx = messages.indexOfFirst { it === p }
        if (idx < 0) return
        val n = ChatMessage(text, false)
        messages[idx] = n
        curPh = n
        adapter.notifyItemChanged(idx)
        b.rv.scrollToPosition(messages.size - 1)
    }

    private fun fillPlaceholder(text: String, speakIt: Boolean = true, spoken: String? = null) {
        val p = curPh ?: return
        val idx = messages.indexOfFirst { it === p }
        curPh = null
        if (idx < 0) return
        messages[idx] = ChatMessage(text, false)
        adapter.notifyItemChanged(idx)
        b.rv.scrollToPosition(messages.size - 1)
        if (speakIt) {
            val spoke = speak(spoken ?: text)
            // Bola nahi (setting band / engine nahi): baat-cheet mode mein seedha sunna shuru
            if (!spoke) afterSpeech()
        }
    }

    /** true = bolna shuru hua. false = kuch bola nahi (setting band, engine nahi, ya bolne layak text nahi) */
    private fun speak(text: String): Boolean {
        if (!Prefs.speakReplies(this)) return false
        val t = tts
        if (t == null || !ttsReady || ttsFailed) return false
        val s = Honesty.spokenText(text)
        if (s.isEmpty()) return false
        val okVoice = VoiceHelper.apply(this, t, s)
        if (!okVoice && !voiceWarned && VoiceHelper.speakLang(this, s) == "en") {
            voiceWarned = true
            Toast.makeText(this, "English awaaz nahi mili. Phone Settings mein Text-to-speech se download kar lo", Toast.LENGTH_LONG).show()
        }
        val rc = t.speak(s.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        return rc == TextToSpeech.SUCCESS
    }

    override fun onDestroy() {
        cancelAutoListen()
        autoListening = false
        bargeIn.stop()
        NetworkMonitor.unregister(this, netCb)
        voice?.destroy()
        ModelRouter.onStatus = null
        ModelRouter.onPartial = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
