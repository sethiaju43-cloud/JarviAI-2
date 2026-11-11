package com.jarvis.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Design screen: theme, background animation, wallpaper, DP aur frame yahan se badlo */
class DesignActivity : ScreenActivity() {

    private var floatDirty = false

    private val pickWall = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = AppTheme.setWallpaper(this, uri)
            UiKit.toast(this, if (ok) "Wallpaper lag gaya \uD83C\uDF89" else "Ye photo nahi khul payi, doosri try karo")
            changed(false)
        }
    }

    private val pickDp = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = AppTheme.setDp(this, uri)
            if (ok) AppTheme.setAvatarMode(this, 1)
            UiKit.toast(this, if (ok) "Tumhari photo DP ban gayi \uD83D\uDE0E" else "Ye photo nahi khul payi, doosri try karo")
            changed(true)
        }
    }

    override fun screenTitle() = "\uD83C\uDFA8 Design"

    private fun dpi(v: Int) = UiKit.dp(this, v)

    /** Badlav ke baad: design lagao, screen dobara banao, scroll wahin rakho */
    private fun changed(avatarOrTheme: Boolean) {
        if (avatarOrTheme) floatDirty = true
        val sv = container.parent as? ScrollView
        val y = sv?.scrollY ?: 0
        rebuild()
        Skin.applyActivity(this)
        sv?.post { sv.scrollTo(0, y) }
    }

    override fun build() {
        val c = this
        val cur = AppTheme.current(c)
        val curStyle = AppTheme.style(c)

        container.addView(UiKit.text(c, "Yahan se poora look badlo: rang, animation, wallpaper aur DP. Jo pasand na aaye, turant doosra chuno \uD83D\uDE0A", 13f, dim = true))
        val top = UiKit.row(c)
        top.addView(UiKit.rowButton(c, "\uD83C\uDFB2 Surprise me") {
            val th = AppTheme.all.random()
            AppTheme.setTheme(c, th.id)
            AppTheme.setStyle(c, AppTheme.styles.filter { it.first != "none" }.random().first)
            AppTheme.setFrame(c, (1 until AppTheme.frameNames.size).random())
            changed(true)
        })
        top.addView(UiKit.rowButton(c, "\u21A9 Default design") {
            AppTheme.resetAll(c)
            changed(true)
        })
        container.addView(top)

        // ---------- Theme ----------
        container.addView(UiKit.section(c, "\uD83C\uDFA8 Theme (" + AppTheme.all.size + " design)"))
        gridRows(AppTheme.all.map { themeCell(it, it.id == cur.id) }, 3)

        // ---------- Animation ----------
        container.addView(UiKit.section(c, "\u26A1 Background animation (" + (AppTheme.styles.size - 1) + " tarah)"))
        container.addView(UiKit.text(c, "Har theme ki apni animation hoti hai, par yahan se koi bhi chun lo. Battery Saver ON ho to animation ruk jati hai.", 12f, dim = true))
        gridRows(AppTheme.styles.map { styleChip(it.first, it.second, it.first == curStyle) }, 2)

        // ---------- Wallpaper ----------
        container.addView(UiKit.section(c, "\uD83D\uDDBC Wallpaper"))
        container.addView(UiKit.text(c,
            if (AppTheme.hasWallpaper(c)) "Abhi: tumhari gallery ki wallpaper lagi hai (animation uske upar chalegi; band karni ho to upar 'Koi nahi' chuno)"
            else "Abhi: koi wallpaper nahi, theme ka gradient dikh raha hai", 12f, dim = true))
        val wr = UiKit.row(c)
        wr.addView(UiKit.rowButton(c, "\uD83D\uDDBC Gallery se chuno") { pickWall.launch("image/*") })
        if (AppTheme.hasWallpaper(c)) {
            wr.addView(UiKit.rowButton(c, "\uD83D\uDDD1 Hatao") {
                AppTheme.clearWallpaper(c)
                changed(false)
            })
        }
        container.addView(wr)
        if (AppTheme.hasWallpaper(c)) {
            val label = UiKit.text(c, "Wallpaper par andhera: " + AppTheme.dim(c) + "% (likhai saaf dikhne ke liye)", 13f)
            container.addView(label)
            val sb = SeekBar(c)
            sb.max = 80
            sb.progress = AppTheme.dim(c)
            sb.progressTintList = ColorStateList.valueOf(cur.accent)
            sb.thumbTintList = ColorStateList.valueOf(cur.accent)
            sb.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    label.text = "Wallpaper par andhera: $p% (likhai saaf dikhne ke liye)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    AppTheme.setDim(c, s?.progress ?: 35)
                    Skin.applyActivity(c)
                }
            })
            container.addView(sb)
        }

        // ---------- DP ----------
        container.addView(UiKit.section(c, "\uD83D\uDE0E DP aur floating icon"))
        container.addView(UiKit.text(c, "Ye Home screen ke Jarvi par aur floating Jarvi par lagta hai. Floating wala is screen se bahar aate hi naya look le leta hai.", 12f, dim = true))
        val mode = AppTheme.avatarMode(c)
        val mr = UiKit.row(c)
        mr.addView(choiceButton((if (mode == 0) "\u2713 " else "") + "Jarvi character", mode == 0) {
            AppTheme.setAvatarMode(c, 0)
            changed(true)
        })
        mr.addView(choiceButton((if (mode == 1) "\u2713 " else "") + "Meri photo", mode == 1) {
            if (AppTheme.hasDp(c)) {
                AppTheme.setAvatarMode(c, 1)
                changed(true)
            } else pickDp.launch("image/*")
        })
        container.addView(mr)
        val pr = UiKit.row(c)
        pr.addView(UiKit.rowButton(c, "\uD83D\uDCF7 Photo chuno (gallery)") { pickDp.launch("image/*") })
        if (AppTheme.hasDp(c)) {
            pr.addView(UiKit.rowButton(c, "\uD83D\uDDD1 Photo hatao") {
                AppTheme.clearDp(c)
                AppTheme.setAvatarMode(c, 0)
                changed(true)
            })
        }
        container.addView(pr)
        container.addView(UiKit.text(c, "Frame / glow (" + (AppTheme.frameNames.size - 1) + " tarah):", 13f))
        val fcur = AppTheme.frame(c)
        gridRows(AppTheme.frameNames.indices.map { frameCell(it, it == fcur) }, 4)

        container.addView(UiKit.text(c, "App ka launcher icon (phone ki home screen wala) Android ki wajah se yahan se nahi badal sakta, wo sirf app ke andar ka look hai.", 11f, dim = true))
    }

    // ---------- chhote tukde ----------
    private fun gridRows(cells: List<View>, perRow: Int) {
        var row: LinearLayout? = null
        for ((i, cell) in cells.withIndex()) {
            if (i % perRow == 0) {
                row = UiKit.row(this)
                container.addView(row)
            }
            row?.addView(cell)
        }
        val rem = cells.size % perRow
        if (rem != 0) {
            for (k in 0 until perRow - rem) {
                val sp = View(this)
                sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                row?.addView(sp)
            }
        }
    }

    private fun cellLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .apply { setMargins(dpi(3), dpi(4), dpi(3), dpi(4)) }

    private fun themeCell(th: ThemeDef, selected: Boolean): View {
        val cur = AppTheme.current(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.layoutParams = cellLp()
        val sw = View(this)
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(th.bg1, th.bg2, th.accent))
        g.cornerRadius = dpi(14).toFloat()
        g.setStroke(dpi(if (selected) 3 else 1), if (selected) cur.text else AppTheme.alpha(th.accent2, 0x99))
        sw.background = g
        sw.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(64))
        box.addView(sw)
        val label = UiKit.text(this, (if (selected) "\u2713 " else "") + th.name, 11f, bold = selected)
        label.gravity = Gravity.CENTER
        box.addView(label)
        box.setOnClickListener {
            AppTheme.setTheme(this, th.id)
            changed(true)
        }
        return box
    }

    private fun styleChip(id: String, name: String, selected: Boolean): View {
        val v = TextView(this)
        v.text = (if (selected) "\u2713 " else "") + name
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        val cur = AppTheme.current(this)
        v.setTextColor(if (selected) cur.text else cur.accent)
        v.background = if (selected) selectedBg() else AppTheme.drawableFor(this, "btn")
        v.setPadding(dpi(8), dpi(12), dpi(8), dpi(12))
        v.layoutParams = cellLp()
        v.setOnClickListener {
            AppTheme.setStyle(this, id)
            changed(false)
        }
        return v
    }

    private fun choiceButton(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val b = UiKit.rowButton(this, label, onClick)
        if (selected) b.background = selectedBg()
        return b
    }

    private fun selectedBg(): GradientDrawable {
        val cur = AppTheme.current(this)
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(AppTheme.alpha(cur.accent, 0x99), AppTheme.alpha(cur.accent2, 0x99)))
        g.cornerRadius = dpi(14).toFloat()
        g.setStroke(dpi(2), cur.text)
        return g
    }

    private fun frameCell(idx: Int, selected: Boolean): View {
        val cur = AppTheme.current(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.layoutParams = cellLp()
        val bg = GradientDrawable()
        bg.cornerRadius = dpi(12).toFloat()
        bg.setColor(AppTheme.cardColor(cur))
        bg.setStroke(dpi(if (selected) 3 else 1), if (selected) cur.text else AppTheme.alpha(cur.accent, 0x55))
        if (idx == 0) {
            val tv = TextView(this)
            tv.text = "\u2716"
            tv.gravity = Gravity.CENTER
            tv.textSize = 20f
            tv.setTextColor(cur.dim)
            tv.background = bg
            tv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(64))
            box.addView(tv)
        } else {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.background = bg
            iv.setPadding(dpi(6), dpi(6), dpi(6), dpi(6))
            iv.setImageDrawable(AppTheme.frameDrawable(idx, cur))
            iv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(64))
            box.addView(iv)
        }
        val label = UiKit.text(this, AppTheme.frameNames[idx], 10f)
        label.gravity = Gravity.CENTER
        box.addView(label)
        box.setOnClickListener {
            AppTheme.setFrame(this, idx)
            changed(true)
        }
        return box
    }

    // ---------- floating ko naya look ----------
    private fun restartFloating() {
        val app = applicationContext
        if (!Prefs.sp(app).getBoolean("floating", true) || !Settings.canDrawOverlays(app)) return
        app.stopService(Intent(app, FloatingService::class.java))
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                ContextCompat.startForegroundService(app, Intent(app, FloatingService::class.java))
            } catch (e: Exception) {
            }
        }, 600)
    }

    override fun onStop() {
        super.onStop()
        if (floatDirty) {
            floatDirty = false
            restartFloating()
        }
    }
}
