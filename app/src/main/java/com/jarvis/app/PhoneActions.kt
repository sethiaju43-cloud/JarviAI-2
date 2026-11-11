package com.jarvis.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Naye phone ke kaam: torch, awaaz, battery, settings, notifications, lock, screenshot,
 * gaana, WhatsApp message, raasta, map, website, calendar.
 * Jo command yahan nahi milta wo purane CommandHandler ko de diya jata hai.
 */
object PhoneActions {

    private var torchOn = false

    private const val NO_ACC =
        "Accessibility atak gayi 😕 Settings mein Jarvis Phone Control ek baar OFF phir ON karo"

    private fun nap(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: Exception) {
        }
    }

    /** Gemini ke jawab ko dekho: naya command ho to yahan chalao, warna purana CommandHandler */
    fun interpret(context: Context, reply: String): String {
        val r = reply.trim()
        if (r.indexOf("OPEN:") < 0) {
            val ai = r.indexOf("ACTION:")
            if (ai >= 0) {
                val line = r.substring(ai + 7).lines().first().trim()
                val res = run(context, line)
                if (res != null) return res
            }
        }
        return CommandHandler.interpret(context, reply)
    }

    private fun run(context: Context, line: String): String? {
        val up = line.uppercase()
        return when {
            up.startsWith("FLASH") -> flash(context, up)
            up.startsWith("VOLUME") -> volume(context, up)
            up.startsWith("BATTERY") -> battery(context)
            up.startsWith("SETTINGS") -> openSettings(context, up)
            up.startsWith("NOTIFICATIONS") ->
                globalAct(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications khol di 🔔")
            up.startsWith("QUICKSETTINGS") ->
                globalAct(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick settings khol di ⚙️")
            up.startsWith("LOCK") -> lockScreen()
            up.startsWith("SCREENSHOT") -> screenshot()
            up.startsWith("MUSIC") -> music(context, line.drop(5).trim())
            up.startsWith("WHATSAPP") -> whatsapp(context, line.drop(8).trim())
            up.startsWith("NAVIGATE") -> navigate(context, line.drop(8).trim())
            up.startsWith("MAPS") -> maps(context, line.drop(4).trim())
            up.startsWith("URL") -> openUrl(context, line.drop(3).trim())
            up.startsWith("EVENT") -> addEvent(context, line.drop(5).trim())
            else -> null
        }
    }

    private fun launch(context: Context, i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun digitsOnly(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            val d = Character.digit(c, 10)
            if (d >= 0) sb.append(d)
        }
        return sb.toString()
    }

    // ---------- Torch ----------
    private fun flash(context: Context, up: String): String {
        val want = when {
            up.contains(" OFF") -> false
            up.contains(" ON") -> true
            else -> !torchOn
        }
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (c in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(c)
                val has = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = ch.get(CameraCharacteristics.LENS_FACING)
                if (has && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    id = c
                    break
                }
            }
            if (id == null) return "Is phone mein torch nahi mili 😕"
            cm.setTorchMode(id, want)
            torchOn = want
            return if (want) "Torch jala di 🔦" else "Torch band kar di"
        } catch (e: Exception) {
            return "Torch nahi chal payi 😕 (camera app khula ho to band kar do)"
        }
    }

    // ---------- Awaaz ----------
    private fun volume(context: Context, up: String): String {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = Regex("[0-9]+").find(up)?.value?.toIntOrNull()
            if (pct != null) {
                val v = pct.coerceIn(0, 100) * max / 100
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
                return "Awaaz " + pct.coerceIn(0, 100) + "% kar di 🔊"
            }
            if (up.contains("MAX")) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                return "Awaaz poori kar di 🔊"
            }
            if (up.contains("MUTE")) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                return "Awaaz band kar di 🔇"
            }
            val dir = if (up.contains("DOWN")) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
            for (k in 0 until 3) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            }
            return if (dir == AudioManager.ADJUST_LOWER) "Awaaz kam kar di 🔉" else "Awaaz badha di 🔊"
        } catch (e: Exception) {
            return "Awaaz badal nahi payi 😕"
        }
    }

    // ---------- Battery ----------
    private fun battery(context: Context): String {
        try {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return "Battery ka pata nahi chala 😕"
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (level < 0 || scale <= 0) return "Battery ka pata nahi chala 😕"
            val pct = level * 100 / scale
            return "Battery " + pct + "% hai" + (if (charging) " aur charge ho rahi hai 🔌" else " 🔋")
        } catch (e: Exception) {
            return "Battery ka pata nahi chala 😕"
        }
    }

    // ---------- Settings ----------
    private fun openSettings(context: Context, up: String): String {
        val act = when {
            up.contains("WIFI") -> Settings.ACTION_WIFI_SETTINGS
            up.contains("BLUETOOTH") -> Settings.ACTION_BLUETOOTH_SETTINGS
            up.contains("DISPLAY") || up.contains("BRIGHT") -> Settings.ACTION_DISPLAY_SETTINGS
            up.contains("SOUND") || up.contains("VOLUME") -> Settings.ACTION_SOUND_SETTINGS
            up.contains("BATTERY") -> Intent.ACTION_POWER_USAGE_SUMMARY
            up.contains("LOCATION") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            up.contains("AIRPLANE") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            up.contains("APPS") -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (launch(context, Intent(act))) "Settings khol di ⚙️" else "Settings nahi khul payi 😕"
    }

    // ---------- Accessibility ke seedhe kaam ----------
    private fun globalAct(action: Int, ok: String): String {
        val svc = JarvisAccessibilityService.instance ?: return NO_ACC
        return if (svc.performGlobalAction(action)) ok else "Ye kaam nahi ho paya 😕"
    }

    private fun lockScreen(): String {
        if (Build.VERSION.SDK_INT < 28) return "Is Android par screen lock nahi kar sakti 😕"
        return globalAct(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Phone lock kar diya 🔒")
    }

    private fun screenshot(): String {
        if (Build.VERSION.SDK_INT < 28) return "Is Android par screenshot nahi le sakti 😕"
        val svc = JarvisAccessibilityService.instance ?: return NO_ACC
        CommandHandler.petHidden(true)
        nap(400)
        val ok = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        nap(900)
        CommandHandler.petHidden(false)
        return if (ok) "Screenshot le liya 📸" else "Screenshot nahi ho paya 😕"
    }

    // ---------- Gaana ----------
    private fun music(context: Context, q: String): String {
        val query = q.trim().trim('"', '\'')
        if (query.isBlank()) return "Kaunsa gaana chalaun? 🎵"
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        i.putExtra(android.app.SearchManager.QUERY, query)
        if (launch(context, i)) return "'" + query + "' chala rahi hun 🎵"
        val y = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        )
        return if (launch(context, y)) {
            "YouTube par '" + query + "' dhundh diya 🎵 Pehla gaana khud chala do"
        } else {
            "Gaana chalane wala app nahi mila 😕"
        }
    }

    // ---------- WhatsApp ----------
    private fun whatsapp(context: Context, arg: String): String {
        val idx = arg.indexOf("::")
        val target: String
        val msg: String
        if (idx >= 0) {
            target = arg.substring(0, idx).trim().trim('"', '\'', '<', '>')
            msg = arg.substring(idx + 2).trim().trim('"', '\'')
        } else {
            target = arg.trim().trim('"', '\'', '<', '>')
            msg = ""
        }
        if (target.isBlank()) return "Kisko WhatsApp karna hai? 🤔"

        var number: String? = null
        var label = target
        val digits = digitsOnly(target)
        if (digits.length >= 5) {
            number = digits
        } else {
            val canRead = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (!canRead) return "Naam se WhatsApp ke liye Permissions mein Contacts allow karo 📒"
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

        var d = digitsOnly(number)
        d = d.trimStart('0')
        // 10 ank wale number ke aage Settings wala country code lagao (khali ho to kuch nahi)
        val cc = (context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString("country_code", "91") ?: "91").filter { it.isDigit() }
        if (cc.isNotEmpty() && d.length == 10) d = cc + d
        var url = "https://wa.me/" + d
        if (msg.isNotBlank()) url += "?text=" + Uri.encode(msg)

        val wa = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        wa.setPackage("com.whatsapp")
        if (!launch(context, wa)) {
            if (!launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                return "WhatsApp khul nahi paya 😕"
            }
        }
        if (msg.isBlank()) return label + " ki WhatsApp chat khol di 💬"

        nap(3500)
        val svc = JarvisAccessibilityService.instance
            ?: return label + " ki chat mein message likh diya, Send aap daba do 💬"
        var sent = svc.clickBest("send")
        if (!sent) {
            val r = Brain.visionTap(context, "Send button (paper plane icon)")
            sent = r.contains("par tap kar diya")
        }
        return if (sent) {
            label + " ko WhatsApp par bhej diya 💬"
        } else {
            label + " ki chat mein message likh diya, Send aap daba do 💬"
        }
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

    // ---------- Raasta, map, website ----------
    private fun navigate(context: Context, place: String): String {
        val p = place.trim().trim('"', '\'')
        if (p.isBlank()) return "Kahan jaana hai? 🧭"
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(p)))
        nav.setPackage("com.google.android.apps.maps")
        if (launch(context, nav)) return "'" + p + "' ka raasta khol diya 🧭"
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(p)))
        return if (launch(context, geo)) "'" + p + "' map par khol diya 🗺️" else "Maps nahi mila 😕"
    }

    private fun maps(context: Context, place: String): String {
        val p = place.trim().trim('"', '\'')
        if (p.isNotBlank()) {
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(p)))
            return if (launch(context, geo)) "'" + p + "' map par dikha diya 🗺️" else "Maps nahi mila 😕"
        }
        val i = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        if (i != null && launch(context, i)) return "Maps khol diya 🗺️"
        return "Maps nahi mila 😕"
    }

    private fun openUrl(context: Context, link: String): String {
        var u = link.trim().trim('"', '\'', '<', '>')
        if (u.isBlank()) return "Kaunsi website kholni hai? 🤔"
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u
        return if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(u)))) {
            "Website khol di 🌐"
        } else {
            "Website nahi khul payi 😕"
        }
    }

    // ---------- Calendar ----------
    /** Format: <title> :: <YYYY-MM-DD HH:MM> */
    private fun addEvent(context: Context, arg: String): String {
        val idx = arg.indexOf("::")
        val title = (if (idx >= 0) arg.substring(0, idx) else arg).trim().trim('"', '\'')
        val whenStr = if (idx >= 0) arg.substring(idx + 2).trim() else ""
        if (title.isBlank()) return "Event ka naam kya rakhun? 🤔"

        val i = Intent(Intent.ACTION_INSERT)
        i.data = CalendarContract.Events.CONTENT_URI
        i.putExtra(CalendarContract.Events.TITLE, title)
        if (whenStr.isNotBlank()) {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).parse(whenStr)
                if (d != null) {
                    i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, d.time)
                    i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, d.time + 3600000L)
                }
            } catch (e: Exception) {
            }
        }
        if (!launch(context, i)) return "Calendar app nahi mila 😕"
        nap(2500)
        val svc = JarvisAccessibilityService.instance
        if (svc != null && svc.clickBest("save")) {
            return "'" + title + "' calendar mein jod diya 📅"
        }
        return "'" + title + "' calendar mein bhar diya 📅 Save ek baar dabana padega"
    }
}
