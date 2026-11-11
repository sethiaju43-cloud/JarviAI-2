package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null

        // In apps ki screen Jarvi padhegi nahi aur uski photo bhi nahi legi
        private val PRIVATE_PREFIXES = listOf(
            "com.phonepe", "com.google.android.apps.nbu.paisa", "net.one97.paytm",
            "in.org.npci", "com.dreamplug", "com.mobikwik", "com.freecharge", "com.bharatpe",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.messaging", "com.android.mms",
            "com.samsung.android.messaging", "com.miui.mms",
            "com.google.android.apps.authenticator2", "com.authy",
            "com.bitwarden", "com.x8bit.bitwarden", "com.lastpass", "com.onepassword",
            "com.agilebits", "com.dashlane", "org.keepassdroid", "keepass2android"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun nap(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: Exception) {
        }
    }

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    // Neeche scroll = ungli neeche se upar swipe
    fun scrollDown() = swipe(0.7f, 0.3f)

    // Upar scroll = ungli upar se neeche swipe
    fun scrollUp() = swipe(0.3f, 0.7f)

    private fun swipe(fromY: Float, toY: Float) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val path = Path().apply {
            moveTo(w / 2f, h * fromY)
            lineTo(w / 2f, h * toY)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
        dispatchGesture(g, null, null)
    }

    fun tapAt(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(g, null, null)
    }

    fun doubleTapAt(x: Float, y: Float) {
        val p1 = Path()
        p1.moveTo(x, y)
        val p2 = Path()
        p2.moveTo(x, y)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, 60))
            .addStroke(GestureDescription.StrokeDescription(p2, 160, 60))
            .build()
        dispatchGesture(g, null, null)
    }

    fun tapCenter() {
        val dm = resources.displayMetrics
        tapAt(dm.widthPixels / 2f, dm.heightPixels / 2f)
    }

    private fun activeRoot(): AccessibilityNodeInfo? {
        try {
            val r = rootInActiveWindow
            if (r != null) return r
            for (w in windows) {
                if (w.isActive) {
                    val x = w.root
                    if (x != null) return x
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    /** Abhi jo app screen par hai uska package naam */
    fun currentPackage(): String {
        return try {
            activeRoot()?.packageName?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Bank, UPI, message aur password apps private hain: unki screen nahi padhni */
    fun isPrivateApp(): Boolean {
        val pkg = currentPackage().lowercase()
        if (pkg.isEmpty()) return false
        if (pkg.contains("bank") || pkg.contains("upi") || pkg.contains("wallet")) return true
        return PRIVATE_PREFIXES.any { pkg.startsWith(it) }
    }

    /** Abhi jo app screen par hai uska naam (jaise WhatsApp) */
    fun currentAppLabel(): String {
        val pkg = currentPackage()
        if (pkg.isEmpty()) return ""
        if (pkg == packageName) return "Jarvis"
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun labelOf(n: AccessibilityNodeInfo): String {
        val t = n.text
        if (t != null && t.toString().isNotBlank()) return t.toString().trim()
        val d = n.contentDescription
        if (d != null && d.toString().isNotBlank()) return d.toString().trim()
        return ""
    }

    private fun isMenuLike(s: String): Boolean {
        val l = s.lowercase()
        return l.contains("more options") || l.contains("action menu") || l.contains("menu") ||
            l.contains("overflow") || l.contains("three dot") || l.contains("3 dot") ||
            l.contains("dismiss") || l.contains("share")
    }

    private fun collectNodes(
        n: AccessibilityNodeInfo?,
        out: ArrayList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (n == null || depth > 40 || out.size > 1500) return
        out.add(n)
        for (i in 0 until n.childCount) {
            collectNodes(n.getChild(i), out, depth + 1)
        }
    }

    private fun hasClickableParent(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n.parent
        var d = 0
        while (cur != null && d < 6) {
            if (cur.isClickable) return true
            cur = cur.parent
            d++
        }
        return false
    }

    /** Screen par sabse sahi match wali cheez dabao (menu/3-dot ko kam number milta hai) */
    fun clickBest(text: String): Boolean {
        try {
            val root = activeRoot() ?: return false
            val want = text.trim().lowercase()
            if (want.isEmpty()) return false
            val all = ArrayList<AccessibilityNodeInfo>()
            collectNodes(root, all, 0)
            var best: AccessibilityNodeInfo? = null
            var bestScore = 0
            for (n in all) {
                if (!n.isVisibleToUser) continue
                val label = labelOf(n).lowercase()
                if (label.isEmpty()) continue
                if (label.contains("exit") && !want.contains("exit")) continue
                var score = 0
                if (label == want) {
                    score = 100
                } else if (label.startsWith(want)) {
                    score = 80
                } else if (label.contains(want)) {
                    score = 50
                } else if (label.length >= 3 && want.contains(label)) {
                    score = 30
                }
                if (score == 0) continue
                if (isMenuLike(label) && !isMenuLike(want)) score -= 60
                val r = Rect()
                n.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height().toLong()
                score += Math.min(20L, area / 20000L).toInt()
                if (n.isClickable || hasClickableParent(n)) score += 10
                if (score > bestScore) {
                    best = n
                    bestScore = score
                }
            }
            val b = best ?: return false
            if (bestScore < 30) return false
            var cur: AccessibilityNodeInfo? = b
            var depth = 0
            while (cur != null && depth < 8) {
                if (cur.isClickable) {
                    if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    break
                }
                cur = cur.parent
                depth++
            }
            val rr = Rect()
            b.getBoundsInScreen(rr)
            tapAt(rr.centerX().toFloat(), rr.centerY().toFloat())
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun clickByText(text: String): Boolean {
        return clickBest(text)
    }

    // ---------- Video: bada karna aur aage-peeche karna ----------
    /** Video wali jagah ka beech ka y (portrait mein upar, landscape mein beech) */
    private fun playerCenterY(): Float {
        val dm = resources.displayMetrics
        if (dm.widthPixels > dm.heightPixels) return dm.heightPixels / 2f
        try {
            val root = activeRoot()
            if (root != null) {
                val all = ArrayList<AccessibilityNodeInfo>()
                collectNodes(root, all, 0)
                for (n in all) {
                    val id = n.viewIdResourceName ?: continue
                    if (id.contains("player", true) && n.isVisibleToUser) {
                        val r = Rect()
                        n.getBoundsInScreen(r)
                        if (r.width() > dm.widthPixels * 0.8 &&
                            r.height() > dm.heightPixels * 0.12 &&
                            r.top < dm.heightPixels * 0.4
                        ) {
                            return r.centerY().toFloat()
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        return dm.heightPixels * 0.17f
    }

    private fun clickFullscreenButton(): Boolean {
        return clickBest("enter fullscreen") || clickBest("enter full screen") ||
            clickBest("fullscreen") || clickBest("full screen")
    }

    /** Video ko full screen karo (background thread se bulao) */
    fun enterFullscreen(): Boolean {
        if (clickFullscreenButton()) return true
        // Button chhupa hai: video par tap karke dikhao, phir dabao
        val dm = resources.displayMetrics
        tapAt(dm.widthPixels / 2f, playerCenterY())
        nap(900)
        return clickFullscreenButton()
    }

    /** Video aage ya peeche karo: YouTube ke double-tap se (ek baar = 10 second) */
    fun seekVideo(forward: Boolean, times: Int) {
        val dm = resources.displayMetrics
        val x = if (forward) dm.widthPixels * 0.82f else dm.widthPixels * 0.18f
        val y = playerCenterY()
        for (i in 0 until times) {
            doubleTapAt(x, y)
            nap(550)
        }
    }

    /** Jahan cursor hai (ya pehla likhne wala dabba) wahan text likho */
    fun typeText(text: String): Boolean {
        try {
            val root = activeRoot() ?: return false
            var target: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (target == null || !target.isEditable) {
                target = findEditable(root)
            }
            if (target == null) return false
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
            )
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            return false
        }
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    /** Screen par jo kuch dikh raha hai wo sab text lautao (sabse upar app ka naam) */
    fun readScreenText(): String {
        try {
            if (isPrivateApp()) return ""
            val root = activeRoot() ?: return ""
            val sb = StringBuilder()
            sb.append("APP: ").append(currentAppLabel()).append("\n")
            collectText(root, sb, 0)
            return sb.toString().trim()
        } catch (e: Exception) {
            return ""
        }
    }

    private fun collectText(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (n == null || depth > 40 || sb.length > 5000) return
        if (!n.isVisibleToUser) return
        // Password wale dabbe ko mat padho
        if (n.isPassword) return
        val line = labelOf(n)
        if (line.isNotEmpty() && !sb.endsWith(line + "\n")) {
            sb.append(line).append("\n")
        }
        for (i in 0 until n.childCount) {
            collectText(n.getChild(i), sb, depth + 1)
        }
    }

    /** Screenshot lo (sirf background thread se bulao). Na mile to null */
    fun screenshotBlocking(): Bitmap? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Bitmap>(1)
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer, result.colorSpace
                            )
                            if (hw != null) {
                                holder[0] = hw.copy(Bitmap.Config.ARGB_8888, false)
                            }
                            result.hardwareBuffer.close()
                        } catch (e: Exception) {
                        }
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        latch.countDown()
                    }
                }
            )
        } catch (e: Exception) {
            return null
        }
        try {
            latch.await(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
        }
        return holder[0]
    }
}
