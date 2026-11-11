package com.jarvis.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.WindowCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Poore app ko naye design ke rang deta hai.
 * - har screen ke peeche ThemeBackgroundView lagata hai
 * - XML se bani screens ke purane rang (neon hara, safed, dim) naye theme ke rang se badalta hai
 * - Jarvi ke DP (home + floating) par frame / photo lagata hai
 */
object Skin {
    private val OLD_NEON = Color.parseColor("#19F5C8")
    private val OLD_BG = Color.parseColor("#050B14")
    private val OLD_CARD = Color.parseColor("#0A1A24")
    private val OLD_DIM = Color.parseColor("#8FB5B0")
    private const val TAG_FRAME = "skin_frame"
    private const val TAG_PHOTO = "skin_photo"

    @Volatile
    var resumed: Activity? = null

    fun thumbTint(t: ThemeDef): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(t.accent, t.dim)
    )

    fun trackTint(t: ThemeDef): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(AppTheme.alpha(t.accent, 0x88), AppTheme.alpha(t.dim, 0x55))
    )

    fun stopBg(a: Activity) {
        val content = a.findViewById<ViewGroup>(android.R.id.content) ?: return
        for (i in 0 until content.childCount) {
            val ch = content.getChildAt(i)
            if (ch is ThemeBackgroundView) ch.stop()
        }
    }

    /** Ek activity par poora design lagao (peeche ka background, bar ke rang, sab view) */
    fun applyActivity(a: Activity) {
        if (a.isFinishing || a.isDestroyed) return
        val t = AppTheme.current(a)
        try {
            a.window.setBackgroundDrawable(ColorDrawable(t.bg1))
            a.window.statusBarColor = t.bg1
            a.window.navigationBarColor = t.bg3
            val ic = WindowCompat.getInsetsController(a.window, a.window.decorView)
            ic.isAppearanceLightStatusBars = t.light
            ic.isAppearanceLightNavigationBars = t.light
        } catch (e: Exception) {
        }
        val content = a.findViewById<ViewGroup>(android.R.id.content) ?: return
        var bg: ThemeBackgroundView? = null
        for (i in 0 until content.childCount) {
            val ch = content.getChildAt(i)
            if (ch is ThemeBackgroundView) {
                bg = ch
                break
            }
        }
        if (bg == null) {
            bg = ThemeBackgroundView(a)
            content.addView(bg, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        bg.refresh()
        if (a === resumed) bg.start()
        recolor(content)
    }

    /** Kisi bhi view-tree ke purane rang naye theme ke rang se badlo */
    fun recolor(root: View) {
        val ctx = root.context
        val t = AppTheme.current(ctx)
        val ver = AppTheme.version(ctx)
        val known = ArrayList<Pair<String, Drawable.ConstantState?>>()
        val list = listOf(
            "btn" to R.drawable.bg_button, "card" to R.drawable.bg_card, "mic" to R.drawable.bg_mic,
            "bubble_main" to R.drawable.bg_bubble_main, "bubble" to R.drawable.bg_bubble,
            "bubble_small" to R.drawable.bg_bubble_small, "msg_bot" to R.drawable.bg_msg_bot,
            "msg_user" to R.drawable.bg_msg_user
        )
        for ((role, id) in list) {
            known.add(Pair(role, ContextCompat.getDrawable(ctx, id)?.constantState))
        }
        walk(root, ctx, t, ver, known)
    }

    private fun walk(v: View, ctx: Context, t: ThemeDef, ver: Int, known: List<Pair<String, Drawable.ConstantState?>>) {
        if (v is ThemeBackgroundView) return
        // Purani hari barish ab theme ki animation se badal gayi: use band rakho
        if (v is MatrixRainView) {
            v.setLevel(0)
            v.visibility = View.GONE
            return
        }
        if (v.getTag(R.id.skin_ver) != ver) {
            try {
                skinOne(v, ctx, t, known)
            } catch (e: Exception) {
            }
            v.setTag(R.id.skin_ver, ver)
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), ctx, t, ver, known)
        }
    }

    private fun byId(v: View): String = when (v.id) {
        R.id.tvBubble -> "bubble_main"
        R.id.tvFloatBubble -> "bubble_small"
        R.id.btnMic -> "mic"
        else -> ""
    }

    private fun detectBg(v: View, known: List<Pair<String, Drawable.ConstantState?>>): String {
        val bg = v.background ?: return ""
        if (bg is ColorDrawable) {
            return when (bg.color) {
                OLD_BG -> "bg"
                OLD_CARD -> "cardc"
                else -> ""
            }
        }
        val cs = bg.constantState
        if (cs != null) {
            for ((role, k) in known) {
                if (k != null && k == cs) return role
            }
        }
        return byId(v)
    }

    private fun skinOne(v: View, ctx: Context, t: ThemeDef, known: List<Pair<String, Drawable.ConstantState?>>) {
        // 1. peeche ka rang / shape
        var role = v.getTag(R.id.skin_bg) as? String
        if (role == null) {
            role = detectBg(v, known)
            v.setTag(R.id.skin_bg, role)
        }
        if (role.isNotEmpty()) {
            val keep = role.startsWith("bubble") || role.startsWith("msg")
            val l = v.paddingLeft
            val tp = v.paddingTop
            val r = v.paddingRight
            val b = v.paddingBottom
            when (role) {
                "bg" -> v.background = null
                "cardc" -> v.setBackgroundColor(AppTheme.cardColor(t))
                else -> v.background = AppTheme.drawableFor(ctx, role)
            }
            if (keep) v.setPadding(l, tp, r, b)
        }

        // 2. likhai ka rang
        if (v is TextView) {
            var tr = v.getTag(R.id.skin_text) as? Int
            if (tr == null) {
                val c = v.currentTextColor
                tr = when (c) {
                    OLD_NEON -> 1
                    OLD_DIM -> 2
                    Color.WHITE -> 3
                    else -> 0
                }
                v.setTag(R.id.skin_text, tr)
                if (v.shadowRadius > 0f && Color.alpha(v.shadowColor) > 0) v.setTag(R.id.skin_glow, true)
            }
            when (tr) {
                1 -> v.setTextColor(t.accent)
                2 -> v.setTextColor(t.dim)
                3 -> v.setTextColor(t.text)
            }
            if (v.getTag(R.id.skin_glow) == true) {
                v.setShadowLayer(v.shadowRadius, v.shadowDx, v.shadowDy, AppTheme.alpha(t.accent, 0x99))
            }
            if (v is EditText) v.setHintTextColor(t.dim)
        }

        // 3. switch / radio / mic icon
        if (v is SwitchCompat) {
            v.thumbTintList = thumbTint(t)
            v.trackTintList = trackTint(t)
        } else if (v is CompoundButton) {
            CompoundButtonCompat.setButtonTintList(v, thumbTint(t))
        }
        if (v is ImageView && v.id == R.id.btnMic) v.setColorFilter(t.accent, PorterDuff.Mode.SRC_IN)

        // 4. chat list: naye rang ke saath dobara banao
        if (v is RecyclerView) {
            try {
                v.adapter?.notifyDataSetChanged()
            } catch (e: Exception) {
            }
        }

        // 5. Jarvi ka DP (home + floating)
        if (v.id == R.id.petBox && v is FrameLayout) applyAvatar(v, t)
    }

    private fun applyAvatar(box: FrameLayout, t: ThemeDef) {
        val ctx = box.context
        var fv: ImageView? = null
        var pv: ImageView? = null
        for (i in 0 until box.childCount) {
            val ch = box.getChildAt(i)
            if (ch.tag == TAG_FRAME && ch is ImageView) fv = ch
            if (ch.tag == TAG_PHOTO && ch is ImageView) pv = ch
        }

        // frame (peeche)
        val fidx = AppTheme.frame(ctx)
        if (fidx <= 0) {
            fv?.let { box.removeView(it) }
        } else {
            if (fv == null) {
                fv = ImageView(ctx)
                fv.tag = TAG_FRAME
                fv.scaleType = ImageView.ScaleType.FIT_CENTER
                fv.isClickable = false
                box.addView(fv, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            fv.setImageDrawable(AppTheme.frameDrawable(fidx, t))
        }

        // photo (upar), ya Jarvi character
        val front = box.findViewById<View>(R.id.petFront)
        val back = box.findViewById<View>(R.id.petBack)
        val bmp = if (AppTheme.avatarMode(ctx) == 1) AppTheme.dpBitmap(ctx) else null
        if (bmp != null) {
            if (pv == null) {
                pv = ImageView(ctx)
                pv.tag = TAG_PHOTO
                pv.scaleType = ImageView.ScaleType.FIT_CENTER
                pv.isClickable = false
                box.addView(pv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            val rd = RoundedBitmapDrawableFactory.create(ctx.resources, bmp)
            rd.setCircular(true)
            rd.setAntiAlias(true)
            pv.setImageDrawable(rd)
            val pad = UiKit.dp(ctx, 8)
            pv.setPadding(pad, pad, pad, pad)
            front?.visibility = View.INVISIBLE
            back?.visibility = View.INVISIBLE
        } else {
            pv?.let { box.removeView(it) }
            front?.visibility = View.VISIBLE
            back?.visibility = View.VISIBLE
        }
    }
}

/** Har activity ke chalu/ruk-ne par design lagata aur animation rokta hai */
class SkinCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {
        try {
            a.window.setBackgroundDrawable(ColorDrawable(AppTheme.current(a).bg1))
        } catch (e: Exception) {
        }
    }

    override fun onActivityStarted(a: Activity) {}

    override fun onActivityResumed(a: Activity) {
        Skin.resumed = a
        // onResume ka baaki kaam khatam hone ke baad lagao
        a.window.decorView.post { Skin.applyActivity(a) }
    }

    override fun onActivityPaused(a: Activity) {
        if (Skin.resumed === a) Skin.resumed = null
        Skin.stopBg(a)
    }

    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}

/** Floating Jarvi ki layout ka root: jab screen par aata hai, khud naye design ke rang le leta hai */
class SkinLinearLayout @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LinearLayout(c, a) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Skin.recolor(this)
    }
}
