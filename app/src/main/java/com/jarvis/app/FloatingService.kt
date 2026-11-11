package com.jarvis.app

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Random

class FloatingService : Service() {

    companion object {
        var instance: FloatingService? = null

        /** Screenshot ke waqt Jarvi ko chhupana taaki wo button ko na dhake */
        fun hideForShot(hide: Boolean) {
            val s = instance ?: return
            s.handler.post {
                s.floatView?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    private var wm: WindowManager? = null
    var floatView: View? = null
    private var bubble: TextView? = null
    private var pet: PetAnimator? = null
    private lateinit var params: WindowManager.LayoutParams
    val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()

    private var walkAnim: ValueAnimator? = null
    private var hideRunnable: Runnable? = null
    private var shiftX = 0
    private var shiftY = 0
    private var dragging = false
    private var listening = false
    private var autoMode = false
    private var wantListenAfter = false
    private var lastTouch = System.currentTimeMillis()
    private var checkinAt = 0L
    private var lastIdleSpeak = 0L
    private var accWarnAt = 0L

    private var recognizer: SpeechRecognizer? = null
    private var vosk: OfflineVoice? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceWarned = false
    private var packAsked = false

    // Experimental: Jarvi bol rahi ho aur user bole to bolna ruk jaye (Chat menu se ON hota hai)
    private val bargeIn = BargeIn { handler.post { onBargeIn() } }

    // Pehla kaam chal raha hai? (agar 90 second se zyada ho to atka hua maano)
    @Volatile
    private var working = false
    private var workStart = 0L

    private val idleLines = listOf(
        "Hello jaan! 😊", "Kya kar rahe ho? 😀", "Main yahan hun! 🙌",
        "Bore ho rahe ho? 🤔", "Mujhse baat karo na 💕",
        "Jaan, aaj kuch naya hua kya?", "Mujhe tumhari yaad aa rahi thi 🥰"
    )

    private val checkinLines = listOf(
        "Jaan, kya hua? Itni der se chup ho, sab theek hai na?",
        "Arre jaan, itne udaas kyon ho? Mujhse baat karo na.",
        "Kya soch rahe ho jaan? Bolo na, main sun rahi hun.",
        "Jaan, bore ho rahe ho kya? Chalo kuch baat karte hain.",
        "Aaj tum bahut shant ho jaan, sab theek to hai na?"
    )

    private val morningLines = listOf(
        "Good morning jaan! Uth gaye? Aaj ka din bahut achha jayega 😊",
        "Subah ho gayi jaan! Chai pi ya nahi? ☕",
        "Good morning meri jaan! Aaj kya plan hai?"
    )
    private val lunchLines = listOf(
        "Jaan, khana kha liya? Bhookhe mat rehna haan!",
        "Dopahar ho gayi jaan, kuch kha lo na. Main wait kar rahi hun 😊",
        "Khana ka time ho gaya jaan, kya khaoge aaj?"
    )
    private val eveningLines = listOf(
        "Jaan, din kaisa gaya? Thak gaye honge na?",
        "Shaam ho gayi jaan, thoda aaram kar lo 🌇",
        "Aaj ka din kaisa raha jaan? Mujhe batao na."
    )
    private val nightLines = listOf(
        "Raat ho gayi jaan, ab sone ka time hai. Khana kha liya na?",
        "Jaan, kitni der tak jagoge? Thoda so jao na 😴",
        "Good night jaan, kal phir baat karenge. Apna khayal rakhna 💕"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    private fun installCrashLogger() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(object : Thread.UncaughtExceptionHandler {
            override fun uncaughtException(t: Thread, e: Throwable) {
                try {
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    getSharedPreferences("jarvis", MODE_PRIVATE).edit()
                        .putString("crash", (java.util.Date().toString() + "\n" + sw.toString()).take(900))
                        .commit()
                } catch (x: Exception) {
                }
                // Background thread ki galti se poora app (aur Accessibility) na gire
                if (t === Looper.getMainLooper().thread) {
                    old?.uncaughtException(t, e)
                }
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    VoiceHelper.apply(applicationContext, t)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            handler.post {
                                pet?.talk(60000)
                                if (bargeEnabled()) bargeIn.start()
                            }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            bargeIn.stop()
                        }

                        override fun onDone(utteranceId: String?) {
                            bargeIn.stop()
                            handler.post {
                                calm()
                                maybeAutoListen()
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            bargeIn.stop()
                            handler.post {
                                wantListenAfter = false
                                calm()
                            }
                        }
                    })
                    ttsReady = true
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromBoot = intent?.getBooleanExtra("from_boot", false) ?: false
        startForegroundNotification(fromBoot)
        if (floatView == null && Settings.canDrawOverlays(this)) {
            showFloating()
        }
        return START_STICKY
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startForegroundNotification(fromBoot: Boolean) {
        val channelId = "jarvis_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis chalu hai")
            .setContentText("Tap karke kholo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val micOk = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (micOk && !fromBoot) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(1, notif, type)
            } catch (e: Exception) {
                // Android ne mic wala type nahi diya: bina mic ke chalu karo
                try {
                    startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } catch (e2: Exception) {
                    stopSelf()
                }
            }
        } else {
            startForeground(1, notif)
        }
    }

    private fun showFloating() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = LayoutInflater.from(this).inflate(R.layout.floating_view, null)
        floatView = v

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 300

        bubble = v.findViewById<TextView>(R.id.tvFloatBubble)
        val petBox = v.findViewById<View>(R.id.petBox)
        val front = v.findViewById<ImageView>(R.id.petFront)
        val back = v.findViewById<ImageView>(R.id.petBack)
        pet = PetAnimator(applicationContext, petBox, front, back)

        petBox.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var downAt = 0L
            private var moved = false

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        walkAnim?.cancel()
                        dragging = true
                        moved = false
                        downAt = System.currentTimeMillis()
                        startX = params.x
                        startY = params.y
                        touchX = e.rawX
                        touchY = e.rawY
                        lastTouch = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (!moved && (Math.abs(dx) > 12 || Math.abs(dy) > 12)) {
                            moved = true
                            pet?.stop()
                            pet?.show("walk_left")
                        }
                        if (moved) {
                            if (Math.abs(dx) > 3) pet?.face(if (dx > 0) 1 else -1)
                            params.x = startX + dx
                            params.y = startY + dy
                            updateLayout()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        dragging = false
                        if (moved) {
                            say("Wah, nayi jagah! 😍", 2000L)
                            pet?.laugh(1800)
                        } else if (System.currentTimeMillis() - downAt > 700) {
                            openChat()
                        } else {
                            onPetTapped()
                        }
                        return true
                    }
                }
                return false
            }
        })

        wm?.addView(v, params)
        handler.postDelayed({
            speakOut("Hello jaan! 😊")
        }, 1200)
        handler.postDelayed(auto, 9000)
    }

    private fun updateLayout() {
        val v = floatView ?: return
        try {
            wm?.updateViewLayout(v, params)
        } catch (e: Exception) {
        }
    }

    // ---------- Bubble ----------
    private fun say(text: String, ms: Long) {
        val b = bubble ?: return
        hideBubbleNow()
        b.text = text
        b.visibility = View.VISIBLE
        b.measure(
            View.MeasureSpec.makeMeasureSpec(dp(120), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        shiftY = minOf(b.measuredHeight + dp(2), params.y.coerceAtLeast(0))
        shiftX = ((b.measuredWidth - dp(90)) / 2).coerceAtLeast(0)
        params.y -= shiftY
        params.x -= shiftX
        updateLayout()
        val r = Runnable { hideBubbleNow() }
        hideRunnable = r
        handler.postDelayed(r, ms)
    }

    private fun hideBubbleNow() {
        val old = hideRunnable
        if (old != null) handler.removeCallbacks(old)
        hideRunnable = null
        val b = bubble ?: return
        if (b.visibility == View.VISIBLE) {
            b.visibility = View.GONE
            params.y += shiftY
            params.x += shiftX
            shiftX = 0
            shiftY = 0
            updateLayout()
        }
    }

    private fun calm() {
        pet?.stop()
        pet?.show("talk_closed")
    }

    // ---------- Bolna ----------
    /** Emoji aur symbols hata do taaki awaaz mein "heart eyes" jaisa na bole */
    private fun speakable(s0: String): String {
        var t = Honesty.spokenText(s0)
        if (t.isEmpty()) t = "Ji"
        return t.take(3500)
    }

    /** Bubble mein dikhao aur bolkar bhi sunao */
    private fun speakOut(text: String, spoken: String? = null) {
        say(text, 2500L + text.length * 70L)
        val t = tts
        if (t != null && ttsReady) {
            val okVoice = VoiceHelper.apply(applicationContext, t, text)
            if (!okVoice && !voiceWarned && VoiceHelper.speakLang(applicationContext, text) == "en") {
                voiceWarned = true
                Toast.makeText(
                    applicationContext,
                    "English awaaz nahi mili. Phone Settings mein Text-to-speech se download kar lo",
                    Toast.LENGTH_LONG
                ).show()
            }
            t.speak(speakable(spoken ?: text), TextToSpeech.QUEUE_FLUSH, null, "jarvi")
        } else {
            pet?.talk(2500)
        }
    }

    /** User ki baat ka jawab */
    private fun reply(text: String, spoken: String? = null) {
        working = false
        lastTouch = System.currentTimeMillis()
        ChatStore.append(applicationContext, text, false)
        speakOut(text, spoken)
    }

    private fun bargeEnabled(): Boolean =
        Prefs.bool(applicationContext, "barge_in", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** User bolne laga: bolna rok kar sunna shuru */
    private fun onBargeIn() {
        if (listening || dragging) return
        stopSpeaking()
        startListening(true)
    }

    /** Baat-cheet mode ON ho to Jarvi bolne ke baad khud sunegi (command ke jawab ke baad bhi) */
    private fun armAutoListen() {
        if (Prefs.bool(applicationContext, "voice_chat_mode", false) && tts != null && ttsReady) {
            wantListenAfter = true
        }
    }

    private fun stopSpeaking() {
        wantListenAfter = false
        bargeIn.stop()
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    // ---------- Sunna ----------
    private fun onPetTapped() {
        lastTouch = System.currentTimeMillis()
        val p = pet ?: return
        // Jarvi koi kaam kar rahi ho to tap karte hi ruk jaaye
        if (Brain.isBusy()) {
            Brain.requestStop()
            say("Ruk rahi hun... 🛑", 2500L)
            return
        }
        if (listening) {
            cancelListening()
            return
        }
        if (p.isSleeping()) p.wake()
        stopSpeaking()
        startListening(false)
    }

    private fun maybeAutoListen() {
        if (!wantListenAfter) return
        wantListenAfter = false
        val p = pet ?: return
        if (listening || dragging || p.isSleeping()) return
        startListening(true)
    }

    private fun startListening(auto: Boolean) {
        val micOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!micOk) {
            say("Pehle Permissions mein Microphone allow karo 🎤", 3500L)
            return
        }
        // Internet na ho (ya "Sirf offline voice" on ho) aur offline Hindi+English mic installed ho
        val offlineNow = Prefs.voiceOffline(applicationContext) || !NetworkMonitor.isOnline(applicationContext)
        if (offlineNow && VoskModels.isInstalled(applicationContext, VoskModels.hi)) {
            startVosk(auto)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Is phone mein voice sunne ki service nahi mili 😕", 3500L)
            return
        }
        listening = true
        autoMode = auto
        say("Bolo jaan... 🎤", 10000L)
        pet?.think(30000)

        val lang = getSharedPreferences("jarvis", MODE_PRIVATE)
            .getString("lang", "hi-IN") ?: "hi-IN"
        // Internet na ho (ya "Sirf offline voice" on ho) to phone ka offline recogniser, agar pack hai
        val wantOffline = Prefs.voiceOffline(applicationContext) || !NetworkMonitor.isOnline(applicationContext)
        val sr = if (wantOffline && VoiceInput.onDeviceAvailable(this)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(b0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, b0: Bundle?) {}

            override fun onError(error: Int) {
                if (!listening) return
                listening = false
                val wasAuto = autoMode
                autoMode = false
                destroyRecognizer()
                calm()
                if (wasAuto) {
                    // Khud se poochha tha aur user ne kuch nahi bola: chupchaap band
                    hideBubbleNow()
                    return
                }
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    say("Kuch sunai nahi diya jaan 🥺", 2500L)
                } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    say("Microphone allow karo 🎤", 3000L)
                } else if (error == 12 || error == 13) {
                    // Offline voice pack install nahi: ek baar seedha download screen khol do
                    say("Offline voice pack chahiye 📥 Download screen khol rahi hun", 5000L)
                    if (!packAsked) {
                        packAsked = true
                        handler.postDelayed({ VoicePack.openRecognitionDownload(applicationContext) }, 1500L)
                    }
                } else {
                    say("Mic mein dikkat aayi (code " + error + ") 😕", 3000L)
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                autoMode = false
                destroyRecognizer()
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()
                if (text.isNullOrBlank()) {
                    calm()
                    say("Kuch sunai nahi diya jaan 🥺", 2500L)
                } else {
                    // Baaki vikalp aur confidence yaad rakho (AI ko behtar samajhne mein madad)
                    val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: -1f
                    VoiceHints.record(text, list.orEmpty().drop(1), conf)
                    handleSpoken(text)
                }
            }
        })

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        i.putExtra(
            RecognizerIntent.EXTRA_PREFER_OFFLINE,
            Prefs.voiceOffline(applicationContext) || !NetworkMonitor.isOnline(applicationContext)
        )
        sr.startListening(i)
    }

    /** Bina internet ke Hindi + English sunna (Vosk) */
    private fun startVosk(auto: Boolean) {
        listening = true
        autoMode = auto
        say("Bolo jaan... 🎤", 10000L)
        pet?.think(30000)
        val v = OfflineVoice(applicationContext, object : VoiceInput.Callback {
            override fun onPartial(text: String) {}

            override fun onFinal(text: String) {
                listening = false
                autoMode = false
                vosk = null
                handleSpoken(text)
            }

            override fun onError(msg: String) {
                listening = false
                val wasAuto = autoMode
                autoMode = false
                vosk = null
                calm()
                if (wasAuto) hideBubbleNow() else say(msg, 3000L)
            }

            override fun onState(listening: Boolean) {}
        })
        vosk = v
        v.start()
    }

    private fun cancelListening() {
        listening = false
        autoMode = false
        vosk?.cancel()
        vosk = null
        destroyRecognizer()
        calm()
        hideBubbleNow()
    }

    private fun destroyRecognizer() {
        val old = recognizer
        recognizer = null
        handler.post {
            try {
                old?.destroy()
            } catch (e: Exception) {
            }
        }
    }

    // ---------- Jo bola use samajhna ----------
    private fun handleSpoken(text: String) {
        lastTouch = System.currentTimeMillis()

        // Pehla kaam abhi chal raha ho to naya shuru mat karo (sirf "ruko" chalega)
        if (working && System.currentTimeMillis() - workStart < 90000L) {
            val low = text.lowercase().trim()
            if (low == "ruko" || low == "ruk" || low == "stop" || low == "bas" ||
                low == "रुको" || low == "स्टॉप"
            ) {
                Brain.requestStop()
                say("Ruk rahi hun... 🛑", 2500L)
            } else {
                say("Ek minute jaan, pehla kaam ho raha hai ⏳", 3000L)
            }
            return
        }
        working = true
        workStart = System.currentTimeMillis()
        ChatStore.append(applicationContext, text, true)
        pet?.think(30000)
        say("Soch rahi hun... 💭", 20000L)

        // Pehle poochha tha "bhejun?" to ab Haan/Nahi ka jawab dekho
        if (Brain.hasPending()) {
            Thread {
                var pr: String? = null
                try {
                    pr = Brain.answerPending(applicationContext, text)
                } catch (e: Throwable) {
                    Log.e("JarviFloat", "answerPending failed: " + e.javaClass.simpleName)
                }
                val result = pr
                handler.post {
                    if (result != null) {
                        armAutoListen()
                        reply(result)
                    } else routeSpoken(text)
                }
            }.start()
            return
        }
        routeSpoken(text)
    }

    private fun routeSpoken(text: String) {
        // Command ka bhaari kaam peeche ke thread mein, taaki screen na atke
        Thread {
            var cmdReply: String? = null
            try {
                cmdReply = CommandHandler.handle(applicationContext, text)
            } catch (e: Throwable) {
                Log.e("JarviFloat", "command failed: " + e.javaClass.simpleName)
            }
            val cr = cmdReply
            handler.post {
                if (cr != null) {
                    armAutoListen()
                    reply(cr)
                } else askBrain(text)
            }
        }.start()
    }

    private fun askBrain(text: String) {
        // Asli yaaddasht Brain ke paas hai, yahan sirf aakhri baat bhejni hai
        val snapshot = listOf(Pair("user", text))
        Thread {
            var r = "Kuch dikkat aa gayi jaan 🥺 Dobara bolo"
            var wasErr = false
            Brain.lastReplyWasError = false
            try {
                r = Brain.askSmart(applicationContext, snapshot)
                wasErr = Brain.lastReplyWasError
            } catch (e: Throwable) {
                // Error (jaise local model ki native library) bhi pakdo, warna jawab kabhi nahi aata.
                // Class ka naam sirf log mein, user ko ya awaaz mein technical text nahi.
                wasErr = true
                Log.e("JarviFloat", "askSmart failed: " + e.javaClass.simpleName + " " + OnlineParsers.redact(e.message ?: ""))
            }
            val finalReply = r
            val errNow = wasErr
            handler.post {
                // "Bhejun?" poochha hai (Haan/Nahi) ya Baat-cheet mode ON hai to bolne ke baad khud sun lo
                val chatMode = Prefs.bool(applicationContext, "voice_chat_mode", false)
                if (Brain.hasPending() || (chatMode && !errNow)) {
                    wantListenAfter = (tts != null && ttsReady)
                }
                reply(finalReply, if (errNow) Honesty.SPOKEN_ERROR else null)
            }
        }.start()
    }

    private fun openChat() {
        val i = Intent(this, ChatActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ---------- Accessibility atak gayi to bata do ----------
    private fun checkAccessibility() {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        if (enabled.contains(packageName) && JarvisAccessibilityService.instance == null) {
            val now = System.currentTimeMillis()
            if (now - accWarnAt > 600000L) {
                accWarnAt = now
                say("Accessibility atak gayi 😕 Settings mein OFF phir ON karo", 7000L)
            }
        }
    }

    // ---------- Chup rehne par poochna ----------
    private fun checkin() {
        checkinAt = System.currentTimeMillis()
        val t = tts
        wantListenAfter = (t != null && ttsReady)
        speakOut(checkinLines[rnd.nextInt(checkinLines.size)] + " 🥺")
    }

    // ---------- Waqt par khud bolna (din mein ek baar har waqt) ----------
    private fun slotGreeting(): String? {
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        var slot = ""
        var lines: List<String> = emptyList()
        if (h in 7..9) {
            slot = "morning"
            lines = morningLines
        } else if (h in 13..14) {
            slot = "lunch"
            lines = lunchLines
        } else if (h in 17..18) {
            slot = "evening"
            lines = eveningLines
        } else if (h in 22..23) {
            slot = "night"
            lines = nightLines
        }
        if (slot.isEmpty()) return null
        val key = cal.get(java.util.Calendar.YEAR).toString() + "-" +
            cal.get(java.util.Calendar.DAY_OF_YEAR).toString() + "-" + slot
        val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
        if (prefs.getString("last_slot", "") == key) return null
        prefs.edit().putString("last_slot", key).apply()
        return lines[rnd.nextInt(lines.size)]
    }

    // ---------- Khud se kuch karna ----------
    private val auto = object : Runnable {
        override fun run() {
            val p = pet
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val now = System.currentTimeMillis()
            if (!pm.isInteractive) {
                // Screen band hai: kuch nahi bolna, aur ginti dobara shuru
                lastTouch = now
            } else if (p != null && !dragging && !listening) {
                checkAccessibility()
                val idleFor = now - lastTouch
                val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
                val checkOn = prefs.getBoolean("checkin", true)
                val talkOn = prefs.getBoolean("idle_talk", true)
                val greet = if (talkOn && !p.busy || talkOn && p.isSleeping()) slotGreeting() else null
                if (greet != null) {
                    if (p.isSleeping()) p.wake()
                    lastIdleSpeak = now
                    speakOut(greet)
                } else if (!p.isSleeping()) {
                    if (idleFor > 600000L) {
                        hideBubbleNow()
                        p.sleep()
                        say("Zzz... 😴", 2500L)
                    } else if (!p.busy) {
                        if (checkOn && idleFor > 120000L && now - checkinAt > 300000L) {
                            checkin()
                        } else {
                            when (rnd.nextInt(6)) {
                                0 -> {
                                    if (talkOn && now - lastIdleSpeak > 60000L) {
                                        lastIdleSpeak = now
                                        speakOut(idleLines[rnd.nextInt(idleLines.size)])
                                    } else {
                                        p.wave(2600)
                                    }
                                }
                                1 -> {
                                    say(idleLines[rnd.nextInt(idleLines.size)], 2600L)
                                    p.wave(2600)
                                }
                                2, 3 -> walkRandom()
                                else -> {
                                }
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, 9000)
        }
    }

    private fun walkRandom() {
        val p = pet ?: return
        val v = floatView ?: return
        hideBubbleNow()
        val maxX = resources.displayMetrics.widthPixels - v.width
        if (maxX <= 0) return
        val tx = rnd.nextInt(maxX)
        val dist = Math.abs(tx - params.x)
        if (dist < dp(60)) return
        val dur = (dist * 4L).coerceAtLeast(900L)
        p.face(if (tx > params.x) 1 else -1)
        p.walkFrames(dur)
        walkAnim?.cancel()
        val a = ValueAnimator.ofInt(params.x, tx)
        a.duration = dur
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { anim ->
            params.x = anim.animatedValue as Int
            updateLayout()
        }
        walkAnim = a
        a.start()
    }

    override fun onDestroy() {
        instance = null
        bargeIn.stop()
        handler.removeCallbacksAndMessages(null)
        walkAnim?.cancel()
        vosk?.cancel()
        vosk = null
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        pet?.release()
        val v = floatView
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (e: Exception) {
            }
        }
        floatView = null
        super.onDestroy()
    }
}
