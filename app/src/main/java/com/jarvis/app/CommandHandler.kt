package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.SmsManager
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

object CommandHandler {

    private val knownApps = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps"
    )

    // Hindi akshar ko English shabdon mein badalna
    private val replacements = listOf(
        "यूट्यूब" to "youtube", "यूट्युब" to "youtube", "युटुब" to "youtube",
        "यूटूब" to "youtube", "यूटयूब" to "youtube", "यू ट्यूब" to "youtube",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp",
        "व्हाट्सअप" to "whatsapp", "वाट्सएप" to "whatsapp", "वाट्सऐप" to "whatsapp",
        "इंस्टाग्राम" to "instagram", "क्रोम" to "chrome", "कैमरा" to "camera",
        "फेसबुक" to "facebook", "टेलीग्राम" to "telegram", "जीमेल" to "gmail",
        "मैप्स" to "maps", "मैप" to "maps", "गूगल" to "google",
        "खोलो" to "kholo", "खोलिए" to "kholo", "खोल दो" to "kholo", "खोल" to "kholo",
        "ओपन" to "kholo", "चालू" to "kholo", "शुरू" to "kholo", "चलाओ" to "kholo",
        "सर्च" to "search", "ढूंढो" to "search", "ढूँढो" to "search", "ढूंढ" to "search",
        "ढूँढ" to "search", "खोजो" to "search", "खोजिए" to "search", "खोजें" to "search",
        "पढ़" to "padh", "पढ" to "padh", "स्क्रीन" to "screen", "सुनाओ" to "sunao",
        "बताओ" to "batao", "क्या लिखा" to "kya likha", "लिखा" to "likha",
        "दिख" to "dikh", "किसका" to "kiska", "किसकी" to "kiski", "किसके" to "kiske",
        "कॉल" to "call", "नंबर" to "number", "नम्बर" to "number",
        "बैक" to "back", "पीछे" to "peeche", "होम" to "home",
        "स्क्रॉल" to "scroll", "स्क्रोल" to "scroll", "क्लिक" to "click", "टैप" to "tap",
        "नीचे" to "neeche", "ऊपर" to "upar", "रिसेंट" to "recent",
        "रहो" to "raho", "लगातार" to "lagatar", "बार बार" to "baar baar",
        "रुको" to "ruko", "रुक" to "ruko", "स्टॉप" to "stop", "बस करो" to "bas karo",
        "भूल जाओ" to "bhool jao", "भूल जा" to "bhool jao", "भूल" to "bhool",
        "जार्वी" to "jarvi", "जारवी" to "jarvi", "जार्विस" to "jarvis",
        "जर्विस" to "jarvis", "जारविस" to "jarvis",
        "सेल्फी" to "selfie", "फोटो" to "photo", "फ़ोटो" to "photo", "एसएमएस" to "sms"
    )

    private val fillerWords = listOf(
        "kholo", "khol", "kholna", "open", "karo", "kar", "do", "dena", "jao", "ko", "app",
        "please", "jarvis", "jarvi", "click", "tap", "dabao", "hey", "mere", "liye", "zara",
        "na", "करो", "कर", "दो", "हे", "मेरे", "लिए", "प्लीज", "प्लीज़", "ज़रा", "जरा", "को"
    )

    private val searchStop = listOf(
        "search", "youtube", "google", "chrome", "par", "pe", "mein", "me", "ka", "ki", "ke",
        "ko", "karo", "kar", "do", "dena", "kholo", "mere", "liye", "jarvi", "jarvis", "hey",
        "please", "zara", "na", "karke", "dhundo", "dikhao",
        "पर", "में", "का", "की", "के", "को", "करो", "कर", "दो", "हे", "मेरे", "लिए",
        "प्लीज", "जरा", "ज़रा"
    )

    // Ek vaakya mein kai kaam jodne wale shabd
    private val connectors = listOf(
        " aur ", " phir ", " fir ", " uske baad ", " then ", " and ", " और ", " फिर "
    )

    // Alag-alag kaam ke shabd (2 ya zyada group mile to vaakya mein kai kaam hain)
    private val actionGroups = listOf(
        listOf("khol", "open"),
        listOf("search", "dhundo"),
        listOf("type", "likho", "likh "),
        listOf("click", "dabao", "dabaao", "tap", "select", "play"),
        listOf("scroll"),
        listOf("call"),
        listOf("location"),
        listOf("alarm", "timer"),
        listOf("photo", "selfie"),
        listOf("bhejo", "send", "bhej "),
        listOf("fullscreen", "full screen", "bada"),
        listOf("back", "home", "recent")
    )

    private var scrollToken = 0

    private fun nap(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: Exception) {
        }
    }

    /** Kaam ke waqt Jarvi ko chhupao (taaki wo button na dhake aur tap na rok le) */
    fun petHidden(hide: Boolean) {
        val s = FloatingService.instance ?: return
        s.handler.post {
            val v = s.floatView
            if (v != null) {
                v.visibility = if (hide) View.INVISIBLE else View.VISIBLE
                try {
                    val lp = v.layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        if (hide) {
                            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        } else {
                            lp.flags = lp.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        }
                        val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        wm.updateViewLayout(v, lp)
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun normalize(raw: String): String {
        var s = raw.lowercase().trim()
        for ((a, b) in replacements) {
            s = s.replace(a, b)
        }
        s = s.replace(Regex("[,.!?।]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    /** Hindi ya English, kisi bhi ank ko English ank (0-9) mein badlo, '+' rakho */
    private fun asciiDigits(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            val d = Character.digit(c, 10)
            if (d >= 0) {
                sb.append(d)
            } else if (c == '+') {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Kya user screen padhne / dekhne ko keh raha hai? (vaakya kitna bhi lamba ho) */
    fun wantsScreen(raw: String): Boolean {
        val t = normalize(raw)
        if (t.contains("padhai")) return false
        if (t.contains("padh")) return true
        if (t.contains("screen")) return true
        if (t.contains("likha")) return true
        if (t.contains("kiska") || t.contains("kiski") || t.contains("kiske")) return true
        if (t.contains("dikh") && (t.contains("kya") || t.contains("kaun"))) return true
        if (t.contains("number") && t.contains("call")) return true
        return false
    }

    /** Kya vaakya mein kai kaam jude hue hain (aur, phir, ya 2+ alag kaam ke shabd)? */
    fun hasMultiTask(raw: String): Boolean {
        val padded = " " + normalize(raw) + " "
        // SMS ke message ke andar "aur" ho sakta hai, isliye use alag kaam mat samjho
        if (padded.contains(" sms ")) return false
        if (connectors.any { padded.contains(it) }) return true
        // "aur/phir" na bola ho tab bhi: 2 ya zyada alag kaam ke shabd ho to kai kaam hain
        var kinds = 0
        for (g in actionGroups) {
            if (g.any { padded.contains(it) }) kinds++
        }
        return kinds >= 2
    }

    /** Seedha command mila to reply lautao, nahi mila to null (tab Gemini samjhega) */
    // Ek samay mein ek hi command chale (ab ye peeche ke thread se bulaya jaata hai)
    private val handleLock = Any()

    fun handle(context: Context, raw: String): String? {
        synchronized(handleLock) {
            val r = handleInner(context, raw)
            if (r != null) Brain.noteLocal(context, raw, r)
            return r
        }
    }

    private fun handleInner(context: Context, raw: String): String? {
        val t = normalize(raw)
        val words = t.split(" ").filter { it.isNotBlank() }
        val wordCount = words.size

        // Sab kuch bhulwana
        if (wordCount <= 5 && t.contains("bhool jao")) {
            Brain.clearMemory(context)
            ChatStore.clear(context)
            return "Theek hai jaan, sab bhool gayi 🙈"
        }

        // SMS ka kaam Gemini karega (message ke andar back/home jaise shabd ho sakte hain)
        if (t.contains("sms")) return null

        // Scroll rokna
        if (wordCount <= 4 && words.any { it == "ruko" || it == "stop" || it == "bas" }) {
            scrollToken++
            Brain.requestStop()
            return "Ruk gayi 🛑"
        }

        // Ek saath kai kaam: Gemini ek-ek step karke karega
        if (hasMultiTask(raw)) return null

        // YouTube ya Google par search
        if (t.contains("search") && wordCount <= 12) {
            val q = searchQuery(t)
            if (q.isNotBlank()) {
                if (t.contains("youtube")) return openYoutubeSearch(context, q)
                if (t.contains("google") || t.contains("chrome")) return openWebSearch(context, q)
            }
        }

        // App kholo (sirf chhota command, jaise "YouTube kholo")
        if ((t.contains("khol") || t.startsWith("open ")) && wordCount <= 8) {
            val name = cleanName(t)
            if (name.contains("camera")) {
                return launchIntent(
                    context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    "Camera khol diya!"
                )
            }
            val r = openApp(context, name)
            if (r != null) return r
            // Nahi mila ya lamba vaakya hai to Gemini samjhega
        }

        // Screen padhne wale sawaal Gemini ke paas jayenge (screen ka text saath mein)
        if (wantsScreen(raw)) return null

        // Photo ya selfie lene ka kaam Gemini karega
        if (t.contains("selfie") || t.contains("photo")) return null

        // Phone control (Accessibility chahiye) - sirf chhote commands par
        val isControl = t.contains("back") || t.contains("home") ||
            t.contains("scroll") || t.contains("click") || t.contains("tap") ||
            t.contains("recent") || t.contains("peeche")

        if (isControl && wordCount <= 6) {
            val svc = JarvisAccessibilityService.instance
                ?: return "Accessibility atak gayi 😕 Settings mein Jarvis Phone Control ek baar OFF phir ON karo"

            return when {
                t.contains("back") || t.contains("peeche") -> {
                    svc.goBack(); "Back kar diya!"
                }
                t.contains("home") -> {
                    svc.goHome(); "Home par aa gaye!"
                }
                t.contains("recent") -> {
                    svc.openRecents(); "Recent apps khol diye!"
                }
                t.contains("scroll") || t.contains("neeche") || t.contains("upar") -> {
                    val up = t.contains("upar") || t.contains(" up")
                    val n = scrollCount(t)
                    repeatScroll(up, n)
                    (if (up) "Upar" else "Neeche") + " scroll kar rahi hun 📜"
                }
                t.contains("click") || t.contains("tap") -> {
                    val name = cleanName(t)
                    if (name.isBlank()) {
                        svc.tapCenter(); "Tap kar diya!"
                    } else if (svc.clickBest(name)) {
                        "'$name' par click kar diya!"
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
        return null
    }

    private fun scrollCount(t: String): Int {
        if (t.contains("raho") || t.contains("lagatar") || t.contains("baar baar")) return 8
        val m = Regex("[0-9०-९]+").find(t)
        if (m != null) {
            val n = asciiDigits(m.value).toIntOrNull() ?: 1
            return n.coerceIn(1, 12)
        }
        return 1
    }

    private fun repeatScroll(up: Boolean, n: Int) {
        scrollToken++
        val my = scrollToken
        val h = Handler(Looper.getMainLooper())
        var k = 0
        val r = object : Runnable {
            override fun run() {
                if (my != scrollToken) return
                val svc = JarvisAccessibilityService.instance ?: return
                if (up) svc.scrollUp() else svc.scrollDown()
                k++
                if (k < n) h.postDelayed(this, 1300)
            }
        }
        h.post(r)
    }

    /** Gemini ka jawab dekho: usme OPEN: ya ACTION: ho to kaam karo, warna jawab waisa hi lautao */
    fun interpret(context: Context, reply: String): String {
        val r = reply.trim()
        val oi = r.indexOf("OPEN:")
        if (oi >= 0) {
            val name = r.substring(oi + 5).lines().first().trim().trim('"', '\'', '*', '.')
            return openByLabel(context, name)
        }
        val ai = r.indexOf("ACTION:")
        if (ai >= 0) {
            val line = r.substring(ai + 7).lines().first().trim()
            return runAction(context, line)
        }
        return reply
    }

    private fun runAction(context: Context, line: String): String {
        val up = line.uppercase()

        if (up.startsWith("STOP")) {
            scrollToken++
            return "Ruk gayi 🛑"
        }

        // Alarm aur timer: Accessibility ki zarurat nahi
        if (up.startsWith("ALARM")) return setAlarm(context, line.drop(5).trim())
        if (up.startsWith("TIMER")) return setTimer(context, line.drop(5).trim())

        // Call: Accessibility ki zarurat nahi (naam screen se dhundna ho to chahiye)
        if (up.startsWith("CALL")) {
            val target = line.drop(4).trim().trim('"', '\'', '<', '>')
            return callTarget(context, target)
        }

        // SMS: Accessibility ki zarurat nahi
        if (up.startsWith("SMS")) return sendSms(context, line.drop(3).trim())

        val svc = JarvisAccessibilityService.instance
            ?: return "Accessibility atak gayi 😕 Settings mein Jarvis Phone Control ek baar OFF phir ON karo"

        if (up.startsWith("PHOTO")) return takePhoto(context, svc, false)
        if (up.startsWith("SELFIE")) return takePhoto(context, svc, true)

        if (up.startsWith("FULLSCREEN")) return doFullscreen(context, svc)

        if (up.startsWith("SEEK")) {
            val secs = Regex("-?[0-9]+").find(line)?.value?.toIntOrNull() ?: 10
            val fwd = secs >= 0
            val times = ((Math.abs(secs) + 9) / 10).coerceIn(1, 12)
            petHidden(true)
            nap(250)
            svc.seekVideo(fwd, times)
            nap(300)
            petHidden(false)
            return (if (fwd) "Video aage " else "Video peeche ") + (times * 10) +
                " second karne ki koshish ki ⏩ (YouTube ek baar mein 10 second badhata hai)"
        }

        if (up.startsWith("TYPE")) {
            val txt = line.drop(4).trim().trim('"', '\'')
            if (txt.isBlank()) return "Kya likhun jaan? 🤔"
            return if (svc.typeText(txt)) {
                "Likh diya: " + txt + " ✍️"
            } else {
                "Likhne ki jagah nahi mili. Pehle jahan likhna hai wahan tap kar do 😕"
            }
        }

        if (up.startsWith("CLICK")) {
            val name = line.drop(5).trim().trim('"', '\'')
            if (name.isBlank()) return "Kaunsa button dabaun? 🤔"
            if (name.lowercase().contains("full")) return doFullscreen(context, svc)
            val ln = name.lowercase()
            if (ln.contains("shutter") || ln.contains("capture") ||
                ln.contains("take photo") || ln.contains("take picture")
            ) {
                return takePhoto(context, svc, false)
            }
            if (svc.clickBest(name)) return "'" + name + "' dabaa diya! 👆"
            // Text se nahi mila (icon ya game ka button): screenshot dekh kar dhundo
            return Brain.visionTap(context, name)
        }

        if (up.contains("SCROLL")) {
            val upDir = up.contains("UP")
            val n = (Regex("[0-9]+").find(up)?.value?.toIntOrNull() ?: 1).coerceIn(1, 12)
            repeatScroll(upDir, n)
            return (if (upDir) "Upar" else "Neeche") + " scroll kar rahi hun 📜"
        }

        return when {
            up.contains("BACK") -> {
                svc.goBack(); "Back kar diya!"
            }
            up.contains("HOME") -> {
                svc.goHome(); "Home par aa gaye!"
            }
            up.contains("RECENT") -> {
                svc.openRecents(); "Recent apps khol diye!"
            }
            else -> "Ye kaam mujhe samajh nahi aaya 😕"
        }
    }

    private fun doFullscreen(context: Context, svc: JarvisAccessibilityService): String {
        petHidden(true)
        nap(250)
        // Comments ya description ki patti khuli ho to pehle use band karo (wo button ko dhakti hai)
        val txt = svc.readScreenText()
        val panelOpen = (txt.contains("Comments") &&
            (txt.contains("Newest") || txt.contains("Add a comment") || txt.contains("Comment…"))) ||
            txt.contains("Add a comment")
        if (panelOpen) {
            svc.goBack()
            nap(900)
        }
        val ok = svc.enterFullscreen()
        nap(300)
        petHidden(false)
        if (ok) return "Video bada kar diya 📺"
        return Brain.visionTap(context, "fullscreen (enlarge video) button")
    }

    // ---------- SMS ----------
    /** Format: <number ya naam> :: <message> */
    private fun sendSms(context: Context, arg: String): String {
        val idx = arg.indexOf("::")
        if (idx < 0) return "SMS mein kisko aur kya bhejna hai samajh nahi aaya 😕"
        val target = arg.substring(0, idx).trim().trim('"', '\'', '<', '>')
        val msg = arg.substring(idx + 2).trim().trim('"', '\'')
        if (target.isBlank() || msg.isBlank()) return "Kisko aur kya SMS bhejna hai? 🤔"

        val canSms = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canSms) return "Pehle Permissions mein Phone & SMS allow karo 💬"

        var number: String? = null
        var label = target
        val digits = asciiDigits(target)
        if (digits.count { it.isDigit() } >= 5) {
            number = digits
        } else {
            if (!hasContacts(context)) {
                return "Naam se SMS ke liye Permissions mein Contacts allow karo 📒"
            }
            for (n in target.split("|")) {
                val c = findContact(context, n.trim())
                if (c != null) {
                    number = c.second
                    label = c.first
                    break
                }
            }
        }
        if (number == null) return "'" + target + "' naam ka contact nahi mila 😕"

        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val parts = sm.divideMessage(msg)
            if (parts.size > 1) {
                sm.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                sm.sendTextMessage(number, null, msg, null, null)
            }
            label + " ko SMS bhej diya 💬"
        } catch (e: Exception) {
            "SMS nahi ja paya 😕 " + (e.message ?: "")
        }
    }

    // ---------- Photo / Selfie ----------
    /** Camera kholkar shutter par asli tap karta hai (background thread se bulao) */
    private fun takePhoto(
        context: Context,
        svc: JarvisAccessibilityService,
        selfie: Boolean
    ): String {
        var pkg = svc.currentPackage().lowercase()
        if (!pkg.contains("camera")) {
            val ci = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            if (selfie) {
                ci.putExtra("android.intent.extras.CAMERA_FACING", 1)
                ci.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                ci.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
            launchIntent(context, ci, "")
            nap(3200)
            pkg = svc.currentPackage().lowercase()
            if (!pkg.contains("camera")) {
                return "Camera khul nahi paya 😕 Ek baar camera khud khol do, phir bolo"
            }
        } else if (selfie) {
            // Camera pehle se khula hai: front/back badlo
            Brain.visionTap(
                context, "button that switches between the front and back camera"
            )
            nap(1000)
        }

        // Selfie mein pose banane ka time
        if (selfie) nap(2500) else nap(600)

        // Shutter par asli touch (kai phone mein click action se photo nahi khichti)
        val r = Brain.visionTap(context, "round camera shutter button that takes the photo")
        if (r.contains("par tap kar diya")) {
            nap(500)
            return if (selfie) "Selfie kheench li 📸 Gallery mein dekh lena" else "Photo kheench li 📸 Gallery mein dekh lena"
        }

        // Screenshot se na mila: neeche beech mein tap karo (zyadatar camera mein shutter wahin hota hai)
        petHidden(true)
        nap(300)
        val dm = context.resources.displayMetrics
        svc.tapAt(dm.widthPixels / 2f, dm.heightPixels * 0.86f)
        nap(400)
        petHidden(false)
        return "Shutter dabane ki koshish ki 📸 Gallery mein check kar lena"
    }

    // ---------- Alarm aur timer ----------
    private fun setAlarm(context: Context, arg: String): String {
        val m = Regex("([0-9]{1,2})\\s*[:.]\\s*([0-9]{2})").find(arg)
        val h: Int
        val min: Int
        if (m != null) {
            h = m.groupValues[1].toInt()
            min = m.groupValues[2].toInt()
        } else {
            val m2 = Regex("([0-9]{1,2})").find(arg)
                ?: return "Alarm ka time samajh nahi aaya 😕"
            h = m2.groupValues[1].toInt()
            min = 0
        }
        if (h < 0 || h > 23 || min < 0 || min > 59) return "Alarm ka time samajh nahi aaya 😕"
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, h)
        i.putExtra(AlarmClock.EXTRA_MINUTES, min)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvi alarm")
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return launchIntent(
            context, i,
            "Alarm bhej diya ⏰ " + String.format("%02d:%02d", h, min) +
                " (Clock app mein ek baar dekh lena)"
        )
    }

    private fun setTimer(context: Context, arg: String): String {
        val secs = Regex("[0-9]+").find(arg)?.value?.toIntOrNull()
            ?: return "Timer ka time samajh nahi aaya 😕"
        if (secs < 1 || secs > 86400) return "Timer ka time samajh nahi aaya 😕"
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
        i.putExtra(AlarmClock.EXTRA_LENGTH, secs)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvi timer")
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return launchIntent(context, i, "Timer bhej diya ⏱️ " + secs + " second")
    }

    // ---------- Call ----------
    private fun callTarget(context: Context, target: String): String {
        // 1) Number bola gaya (Hindi ya English ank, dono chalenge)
        val digits = asciiDigits(target)
        if (digits.count { it.isDigit() } >= 5) {
            return placeCall(context, digits, digits)
        }

        // 2) "Is number par call karo": screen par likha number
        val compact = target.lowercase().replace(" ", "")
        val generic = compact.isEmpty() || compact == "number" || compact == "screen" ||
            compact == "isnumber" || compact == "thisnumber"
        if (generic) {
            val s = numberFromScreen()
            if (s != null) return placeCall(context, s, s)
            return "Screen par mujhe number nahi dikha. Number bolkar batao 📞"
        }

        // 3) Naam hai to Contacts mein dhundo (agar permission hai)
        if (hasContacts(context)) {
            for (n in target.split("|")) {
                val c = findContact(context, n.trim())
                if (c != null) return placeCall(context, c.second, c.first)
            }
        }

        // 4) Naam screen par dikh raha ho (dialer ya contacts list) to us par tap karo
        val svc = JarvisAccessibilityService.instance
        if (svc != null) {
            for (n in target.split("|")) {
                if (n.isNotBlank() && svc.clickBest(n.trim())) {
                    return "'" + n.trim() + "' par tap kar diya 👆 Call na lage to bolo: call button dabao"
                }
            }
        }
        if (!hasContacts(context)) {
            return "Naam se call ke liye Permissions mein Contacts allow karo 📒"
        }
        return "'" + target + "' naam ka contact nahi mila 😕"
    }

    private fun numberFromScreen(): String? {
        val svc = JarvisAccessibilityService.instance ?: return null
        val text = svc.readScreenText()
        val m = Regex("[+0-9०-९][0-9०-९ \\-]{7,}[0-9०-९]").find(text) ?: return null
        val d = asciiDigits(m.value)
        return if (d.count { it.isDigit() } >= 7) d else null
    }

    private fun placeCall(context: Context, number: String, label: String): String {
        val canCall = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val i = Intent(action, Uri.parse("tel:" + Uri.encode(number)))
        return launchIntent(context, i, label + " ko call laga rahi hun 📞")
    }

    private fun hasContacts(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun findContact(context: Context, name: String): Pair<String, String>? {
        val want = name.lowercase().replace(" ", "")
        if (want.length < 2) return null
        var best: Pair<String, String>? = null
        var bestScore = 0
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null
        cursor.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val l = n.lowercase().replace(" ", "")
                var score = 0
                if (l == want) {
                    score = 4
                } else if (l.startsWith(want) || (l.length >= 3 && want.startsWith(l))) {
                    score = 3
                } else if (l.contains(want)) {
                    score = 2
                }
                if (score > bestScore) {
                    best = Pair(n, num)
                    bestScore = score
                }
            }
        }
        return best
    }

    // ---------- Apps ----------
    /** Phone mein jitne apps hain unki list: (naam, package) */
    fun installedApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        for (ri in list) {
            val label = ri.loadLabel(pm).toString().trim()
            val pkg = ri.activityInfo.packageName
            if (label.isNotEmpty() && seen.add(pkg)) {
                out.add(Pair(label, pkg))
            }
        }
        out.sortBy { it.first.lowercase() }
        return out
    }

    private fun findByLabel(
        context: Context,
        wanted: String,
        minScore: Int
    ): Pair<String, String>? {
        val w = wanted.lowercase().replace(" ", "")
        if (w.length < 2) return null
        var best: Pair<String, String>? = null
        var bestScore = 0
        for (app in installedApps(context)) {
            val l = app.first.lowercase().replace(" ", "")
            var score = 0
            if (l == w) {
                score = 4
            } else if (l.startsWith(w) || (l.length >= 3 && w.startsWith(l))) {
                score = 3
            } else if (l.contains(w) || (l.length >= 3 && w.contains(l))) {
                score = 2
            }
            if (score > bestScore) {
                best = app
                bestScore = score
            }
        }
        return if (bestScore >= minScore) best else null
    }

    fun openByLabel(context: Context, wanted: String): String {
        val app = findByLabel(context, wanted, 2)
        if (app != null) {
            val i = context.packageManager.getLaunchIntentForPackage(app.second)
            if (i != null) return launchIntent(context, i, app.first + " khol diya! 😊")
        }
        return "'" + wanted + "' app mujhe nahi mila 😕"
    }

    private fun cleanName(t: String): String {
        val words = t.split(" ").filter { it.isNotBlank() && it !in fillerWords }
        return words.joinToString(" ").trim()
    }

    private fun searchQuery(t: String): String {
        val words = t.split(" ").filter { it.isNotBlank() && it !in searchStop }
        return words.joinToString(" ").trim()
    }

    private fun openYoutubeSearch(context: Context, q: String): String {
        val i = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        )
        if (context.packageManager.getLaunchIntentForPackage("com.google.android.youtube") != null) {
            i.setPackage("com.google.android.youtube")
        }
        return launchIntent(context, i, "YouTube par '" + q + "' search kar diya! 🔎")
    }

    private fun openWebSearch(context: Context, q: String): String {
        val i = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
        )
        return launchIntent(context, i, "Google par '" + q + "' search kar diya! 🔎")
    }

    private fun openApp(context: Context, name: String): String? {
        if (name.isBlank()) return null
        // Lamba vaakya (video, kaam waghera) ho to ye "app kholo" nahi hai, Gemini samjhega
        if (name.split(" ").filter { it.isNotBlank() }.size > 2) return null
        val pm = context.packageManager

        val pkg = knownApps.entries.firstOrNull { name.contains(it.key) }?.value
        if (pkg != null) {
            val i = pm.getLaunchIntentForPackage(pkg)
            if (i != null) return launchIntent(context, i, name + " khol diya!")
        }

        val found = findByLabel(context, name, 3)
        if (found != null) {
            val i = pm.getLaunchIntentForPackage(found.second)
            if (i != null) return launchIntent(context, i, found.first + " khol diya!")
        }
        return null
    }

    private fun launchIntent(context: Context, i: Intent, okMsg: String): String {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Handler(Looper.getMainLooper()).post {
            try {
                context.startActivity(i)
            } catch (e: Exception) {
            }
        }
        return okMsg
    }
}
