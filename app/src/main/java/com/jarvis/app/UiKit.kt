package com.jarvis.app

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat

/** Settings jaisi screens ke liye chhote view banane wale helpers (rang ab chune hue design se aate hain) */
object UiKit {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun lp(ctx: Context, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(ctx, top); bottomMargin = dp(ctx, bottom) }

    fun text(ctx: Context, t: String, sp: Float = 15f, bold: Boolean = false, dim: Boolean = false): TextView {
        val th = AppTheme.current(ctx)
        val v = TextView(ctx)
        v.text = t
        v.textSize = sp
        v.setTextColor(if (dim) th.dim else th.text)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        v.layoutParams = lp(ctx, 4, 4)
        return v
    }

    fun section(ctx: Context, t: String): TextView {
        val v = text(ctx, t, 14f, true)
        v.setTextColor(AppTheme.current(ctx).accent)
        v.layoutParams = lp(ctx, 22, 6)
        return v
    }

    fun button(ctx: Context, t: String, onClick: () -> Unit): TextView {
        val th = AppTheme.current(ctx)
        val v = TextView(ctx)
        v.text = t
        v.textSize = 14f
        v.gravity = Gravity.CENTER
        v.setTextColor(th.accent)
        v.background = AppTheme.drawableFor(ctx, "btn")
        v.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
        v.layoutParams = lp(ctx, 6, 2)
        v.setOnClickListener { onClick() }
        return v
    }

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = AppTheme.drawableFor(ctx, "card")
        c.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        c.layoutParams = lp(ctx, 6, 6)
        return c
    }

    fun switchRow(ctx: Context, label: String, checked: Boolean, onChange: (Boolean) -> Unit): SwitchCompat {
        val th = AppTheme.current(ctx)
        val s = SwitchCompat(ctx)
        s.text = label
        s.textSize = 14f
        s.setTextColor(th.text)
        s.thumbTintList = Skin.thumbTint(th)
        s.trackTintList = Skin.trackTint(th)
        s.isChecked = checked
        s.layoutParams = lp(ctx, 6, 6)
        s.setOnCheckedChangeListener { _, v -> onChange(v) }
        return s
    }

    fun input(ctx: Context, hint: String, value: String = "", password: Boolean = false): EditText {
        val th = AppTheme.current(ctx)
        val e = EditText(ctx)
        e.hint = hint
        e.setText(value)
        e.textSize = 14f
        e.setTextColor(th.text)
        e.setHintTextColor(th.dim)
        e.background = AppTheme.drawableFor(ctx, "card")
        e.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        e.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        e.layoutParams = lp(ctx, 6, 4)
        return e
    }

    fun progress(ctx: Context): ProgressBar {
        val p = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        p.max = 100
        p.progressTintList = android.content.res.ColorStateList.valueOf(AppTheme.current(ctx).accent)
        p.layoutParams = lp(ctx, 6, 4)
        return p
    }

    fun row(ctx: Context): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = lp(ctx)
        return r
    }

    /** Row ke andar barabar chaudai wala button */
    fun rowButton(ctx: Context, t: String, onClick: () -> Unit): TextView {
        val b = button(ctx, t, onClick)
        b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginEnd = dp(ctx, 6); topMargin = dp(ctx, 6) }
        return b
    }

    fun toast(ctx: Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    fun setVisible(v: View, on: Boolean) {
        v.visibility = if (on) View.VISIBLE else View.GONE
    }
}
