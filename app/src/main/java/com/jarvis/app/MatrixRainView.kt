package com.jarvis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

/** Barish ka pura hisaab (Android ke bina), taaki tests mein jaancha ja sake */
object RainMath {
    /** level: 0 = band, 1 = halka, 2 = tez */
    fun normalizeLevel(v: Int): Int = v.coerceIn(0, 2)

    /** Halka mein har doosra column chhod dete hain */
    fun columnStep(level: Int): Int = if (level == 1) 2 else 1

    fun speedFactor(level: Int): Float = when (level) {
        1 -> 0.6f
        2 -> 1f
        else -> 0f
    }

    fun alphaFactor(level: Int): Float = when (level) {
        1 -> 0.7f
        2 -> 1f
        else -> 0f
    }

    fun columns(widthPx: Int, cellPx: Float, level: Int): Int {
        if (level <= 0 || cellPx <= 0f || widthPx <= 0) return 0
        val all = (widthPx / cellPx).toInt() + 1
        val step = columnStep(level)
        return (all + step - 1) / step
    }

    /** Lakeer ke k-wa akshar (0 = sabse aage wala) kitna saaf dikhe: 0 se 255 */
    fun trailAlpha(k: Int, trail: Int, level: Int): Int {
        if (trail <= 0 || k < 0 || k >= trail) return 0
        val x = 1f - k.toFloat() / trail.toFloat()
        return (255f * x * x * alphaFactor(level)).toInt().coerceIn(0, 255)
    }
}

/**
 * Matrix jaisi hari code ki barish (sirf sajawat). Peeche chalti hai, kuch click nahi leti.
 * Battery bachane ke liye: Battery Saver ya "animations band" ho to chalti nahi (sirf ek tasveer dikhti hai),
 * aur Activity ke onPause mein stop() bulane par ruk jati hai.
 */
class MatrixRainView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(ctx, attrs, defStyle) {

    private val density = resources.displayMetrics.density
    private val cell = 12f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = cell * 0.95f
    }
    private val glyphs: CharArray = pickGlyphs()
    private val charBuf = CharArray(1)
    private val rnd = java.util.Random()

    private var level = 2
    private var running = false
    private var lastNs = 0L
    private var time = 0f

    private var cols = 0
    private var head = FloatArray(0)
    private var speed = FloatArray(0)
    private var trail = IntArray(0)
    private var seed = IntArray(0)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Sirf wahi akshar jo is phone ki font mein hain (warna kaale dibbe dikhte hain) */
    private fun pickGlyphs(): CharArray {
        val cand = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ+=<>" +
            "\uFF71\uFF72\uFF73\uFF74\uFF75\uFF76\uFF77\uFF78\uFF79\uFF7A\uFF7B\uFF7C\uFF7D\uFF7E\uFF7F"
        val p = Paint()
        p.typeface = Typeface.MONOSPACE
        val ok = StringBuilder()
        for (ch in cand) {
            if (p.hasGlyph(ch.toString())) ok.append(ch)
        }
        return (if (ok.length >= 12) ok.toString() else "0123456789ABCDEF").toCharArray()
    }

    private fun rebuild(w: Int, h: Int) {
        cols = RainMath.columns(w, cell, level)
        head = FloatArray(cols)
        speed = FloatArray(cols)
        trail = IntArray(cols)
        seed = IntArray(cols)
        val rows = (h / cell).toInt() + 2
        for (i in 0 until cols) {
            head[i] = rnd.nextFloat() * rows
            speed[i] = 6f + rnd.nextFloat() * 10f
            trail[i] = 8 + rnd.nextInt(14)
            seed[i] = rnd.nextInt(1000)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild(w, h)
    }

    /** Settings se chuna hua level (0 band, 1 halka, 2 tez) */
    fun applyPrefs() {
        setLevel(Prefs.sp(context).getInt("rain_level", 2))
    }

    fun setLevel(l: Int) {
        val n = RainMath.normalizeLevel(l)
        if (n == level && cols > 0) return
        level = n
        if (width > 0 && height > 0) rebuild(width, height)
        if (running && level <= 0) stop()
        invalidate()
    }

    private fun canAnimate(): Boolean {
        if (level <= 0) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isPowerSaveMode) return false
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        return scale > 0f
    }

    /** Activity ke onResume mein bulao */
    fun start() {
        if (running) return
        if (!canAnimate()) {
            // Chalti nahi, bas ek tasveer dikhao
            invalidate()
            return
        }
        running = true
        lastNs = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    /** Activity ke onPause mein bulao */
    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastNs == 0L) lastNs = frameTimeNanos
            val dt = (frameTimeNanos - lastNs) / 1_000_000_000f
            // Lagbhag 30 frame har second (battery ke liye)
            if (dt >= 0.032f) {
                lastNs = frameTimeNanos
                advance(if (dt > 0.1f) 0.1f else dt)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun advance(dt: Float) {
        time += dt
        val rows = (height / cell).toInt() + 2
        val sf = RainMath.speedFactor(level)
        for (i in 0 until cols) {
            head[i] += speed[i] * sf * dt
            if (head[i] - trail[i] > rows) {
                head[i] = -rnd.nextFloat() * 8f
                speed[i] = 6f + rnd.nextFloat() * 10f
                trail[i] = 8 + rnd.nextInt(14)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (level <= 0 || cols == 0) return
        val step = RainMath.columnStep(level)
        val rows = (height / cell).toInt() + 2
        val tick = (time * 6f).toInt()
        for (i in 0 until cols) {
            val x = i * step * cell
            val headRow = head[i].toInt()
            val tl = trail[i]
            for (k in 0 until tl) {
                val row = headRow - k
                if (row < 0 || row > rows) continue
                val a = RainMath.trailAlpha(k, tl, level)
                if (a <= 4) continue
                val idx = Math.floorMod(seed[i] * 31 + row * 17 + (if (k == 0) tick * 7 else tick / 3), glyphs.size)
                paint.color = if (k == 0) HEAD else GREEN
                paint.alpha = a
                charBuf[0] = glyphs[idx]
                canvas.drawText(charBuf, 0, 1, x, (row + 1) * cell, paint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    companion object {
        private val GREEN = Color.rgb(43, 255, 122)
        private val HEAD = Color.rgb(232, 255, 240)
    }
}
