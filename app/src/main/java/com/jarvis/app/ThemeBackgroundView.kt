package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Poori screen ke peeche ka design: theme ka gradient YA gallery wallpaper,
 * uske upar chalne wali animation (bijli, aurora, taare...). Kuch click nahi leta.
 * Battery Saver ON ho ya phone ke animations band hon to sirf tasveer dikhata hai.
 */
class ThemeBackgroundView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private class Bolt(
        val xs: FloatArray, val ys: FloatArray,
        val branches: List<Pair<FloatArray, FloatArray>>,
        var life: Float, val max: Float
    )

    private var t: ThemeDef = AppTheme.current(ctx)
    private var style = "none"
    private var wall: Bitmap? = null
    private var dimColor = 0
    private val wallMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rnd = java.util.Random()
    private val d = ctx.resources.displayMetrics.density

    private var running = false
    private var lastNs = 0L
    private var time = 0f
    private var base: Shader? = null

    // chhote kan (bubbles, barf, taare...)
    private var n = 0
    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var pvx = FloatArray(0)
    private var pvy = FloatArray(0)
    private var ps = FloatArray(0)
    private var pp = FloatArray(0)
    private var pc = IntArray(0)

    // bijli
    private val bolts = ArrayList<Bolt>()
    private var nextBolt = 1f
    private var flash = 0f
    private var cloud: Shader? = null

    // taare + toota taara
    private var shootT = 3f
    private var shootLife = 0f
    private var shootX = 0f
    private var shootY = 0f

    // matrix
    private var mCols = 0
    private var mHead = FloatArray(0)
    private var mSpd = FloatArray(0)
    private var mTrail = IntArray(0)
    private var glyphs = CharArray(0)
    private val charBuf = CharArray(1)

    // grid / aurora
    private var scroll = 0f
    private val auroraShaders = arrayOfNulls<Shader>(3)

    private val confettiColors = intArrayOf(
        Color.parseColor("#FF4D6D"), Color.parseColor("#FFD166"), Color.parseColor("#06D6A0"),
        Color.parseColor("#4CC9F0"), Color.parseColor("#B388FF"), Color.parseColor("#FF9F1C")
    )

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    /** Settings badalne ke baad (ya screen khulne par) bulao */
    fun refresh() {
        t = AppTheme.current(context)
        style = AppTheme.style(context)
        wall = AppTheme.wallpaper(context)
        val dim = AppTheme.dim(context)
        val baseC = if (t.light) 255 else 0
        dimColor = Color.argb((dim * 255 / 100).coerceIn(0, 255), baseC, baseC, baseC)
        rebuildBase()
        initStyle()
        if (running && !canAnimate()) stop()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildBase()
        initStyle()
    }

    private fun rebuildBase() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        base = LinearGradient(
            0f, 0f, w * 0.4f, h.toFloat(),
            intArrayOf(t.bg1, t.bg2, t.bg3), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        val wb = wall
        if (wb != null && wb.width > 0 && wb.height > 0) {
            val sc = max(w.toFloat() / wb.width, h.toFloat() / wb.height)
            wallMatrix.reset()
            wallMatrix.setScale(sc, sc)
            wallMatrix.postTranslate((w - wb.width * sc) / 2f, (h - wb.height * sc) / 2f)
        }
        cloud = LinearGradient(
            0f, 0f, 0f, h * 0.35f,
            intArrayOf(Color.argb(150, 0, 0, 0), 0), null, Shader.TileMode.CLAMP
        )
        for (k in 0 until 3) {
            val c = when (k) {
                0 -> t.accent
                1 -> t.accent2
                else -> AppTheme.mix(t.accent, t.accent2, 0.5f)
            }
            val top = h * (0.10f + 0.12f * k) - h * 0.10f
            val bottom = h * (0.10f + 0.12f * k) + h * 0.22f + h * 0.12f
            auroraShaders[k] = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(0, AppTheme.alpha(c, 120), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
    }

    private fun initStyle() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        bolts.clear()
        flash = 0f
        nextBolt = 0.8f
        n = when (style) {
            "bubbles" -> 22
            "fireflies" -> 28
            "snow" -> 70
            "embers" -> 42
            "confetti" -> 46
            "hearts" -> 16
            "rain", "storm" -> 80
            "stars" -> 90
            "galaxy" -> 170
            else -> 0
        }
        px = FloatArray(n); py = FloatArray(n); pvx = FloatArray(n); pvy = FloatArray(n)
        ps = FloatArray(n); pp = FloatArray(n); pc = IntArray(n)
        for (i in 0 until n) respawn(i, true)
        if (style == "matrix") initMatrix()
    }

    private fun respawn(i: Int, initial: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        pc[i] = rnd.nextInt(3)
        pp[i] = rnd.nextFloat() * 6.2832f
        when (style) {
            "bubbles" -> {
                ps[i] = (6f + rnd.nextFloat() * 22f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = if (initial) rnd.nextFloat() * h else h + ps[i]
                pvy[i] = -(25f + rnd.nextFloat() * 60f) * d
            }
            "fireflies" -> {
                ps[i] = (2f + rnd.nextFloat() * 3.5f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = rnd.nextFloat() * h
            }
            "snow" -> {
                ps[i] = (1.5f + rnd.nextFloat() * 3.5f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = if (initial) rnd.nextFloat() * h else -ps[i]
                pvy[i] = (30f + rnd.nextFloat() * 60f) * d
            }
            "embers" -> {
                ps[i] = (1.5f + rnd.nextFloat() * 3f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = if (initial) rnd.nextFloat() * h else h + ps[i]
                pvy[i] = -(70f + rnd.nextFloat() * 150f) * d
            }
            "confetti" -> {
                ps[i] = (5f + rnd.nextFloat() * 6f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = if (initial) rnd.nextFloat() * h else -ps[i]
                pvy[i] = (70f + rnd.nextFloat() * 110f) * d
                pvx[i] = (rnd.nextFloat() - 0.5f) * 40f * d
                pc[i] = rnd.nextInt(confettiColors.size)
            }
            "hearts" -> {
                ps[i] = (8f + rnd.nextFloat() * 16f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = if (initial) rnd.nextFloat() * h else h + ps[i]
                pvy[i] = -(35f + rnd.nextFloat() * 55f) * d
            }
            "rain", "storm" -> {
                ps[i] = (12f + rnd.nextFloat() * 16f) * d
                px[i] = rnd.nextFloat() * (w + 200f * d)
                py[i] = if (initial) rnd.nextFloat() * h else -ps[i]
                pvy[i] = (650f + rnd.nextFloat() * 450f) * d
                pvx[i] = -120f * d
            }
            "stars" -> {
                ps[i] = (0.6f + rnd.nextFloat() * 1.8f) * d
                px[i] = rnd.nextFloat() * w
                py[i] = rnd.nextFloat() * h
            }
            "galaxy" -> {
                val r = 12f * d + Math.pow(rnd.nextFloat().toDouble(), 1.5).toFloat() * min(w, h) * 0.75f
                pvx[i] = r
                pp[i] = rnd.nextFloat() * 6.2832f + r * 0.012f
                ps[i] = (0.8f + rnd.nextFloat() * 1.8f) * d
            }
        }
    }

    // ---------- matrix ----------
    private fun initMatrix() {
        val cand = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ+=<>" +
            "\uFF71\uFF72\uFF73\uFF74\uFF75\uFF76\uFF77\uFF78\uFF79\uFF7A\uFF7B\uFF7C\uFF7D\uFF7E\uFF7F"
        val tp = Paint()
        tp.typeface = Typeface.MONOSPACE
        val ok = StringBuilder()
        for (ch in cand) if (tp.hasGlyph(ch.toString())) ok.append(ch)
        glyphs = (if (ok.length >= 12) ok.toString() else "0123456789ABCDEF").toCharArray()
        val cell = 16f * d
        mCols = (width / cell).toInt() + 1
        val rows = (height / cell).toInt() + 2
        mHead = FloatArray(mCols)
        mSpd = FloatArray(mCols)
        mTrail = IntArray(mCols)
        for (i in 0 until mCols) {
            mHead[i] = rnd.nextFloat() * rows
            mSpd[i] = 6f + rnd.nextFloat() * 10f
            mTrail[i] = 8 + rnd.nextInt(14)
        }
    }

    // ---------- bijli ----------
    private fun makeBolt() {
        val w = width.toFloat()
        val h = height.toFloat()
        val steps = 14 + rnd.nextInt(6)
        val x0 = w * (0.12f + rnd.nextFloat() * 0.76f)
        val endY = h * (0.45f + rnd.nextFloat() * 0.4f)
        val targetX = x0 + (rnd.nextFloat() - 0.5f) * w * 0.35f
        val xs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        for (i in 0..steps) {
            val k = i / steps.toFloat()
            ys[i] = endY * k
            val b0 = x0 + (targetX - x0) * k
            xs[i] = if (i == 0) x0 else b0 + (rnd.nextFloat() - 0.5f) * 46f * d
        }
        val br = ArrayList<Pair<FloatArray, FloatArray>>()
        val nb = 1 + rnd.nextInt(3)
        for (k in 0 until nb) {
            val s = 3 + rnd.nextInt(steps - 6)
            val len = 4 + rnd.nextInt(5)
            val dir = if (rnd.nextBoolean()) 1f else -1f
            val bx = FloatArray(len + 1)
            val by = FloatArray(len + 1)
            bx[0] = xs[s]
            by[0] = ys[s]
            for (j in 1..len) {
                bx[j] = bx[j - 1] + dir * (14f + rnd.nextFloat() * 26f) * d
                by[j] = by[j - 1] + (18f + rnd.nextFloat() * 26f) * d
            }
            br.add(Pair(bx, by))
        }
        bolts.add(Bolt(xs, ys, br, 0.55f, 0.55f))
        if (bolts.size > 3) bolts.removeAt(0)
        flash = 1f
    }

    // ---------- chalana / rokna ----------
    private fun canAnimate(): Boolean {
        if (style == "none") return false
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isPowerSaveMode) return false
            val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            scale > 0f
        } catch (e: Exception) {
            true
        }
    }

    fun start() {
        if (running) return
        if (!canAnimate()) {
            invalidate()
            return
        }
        running = true
        lastNs = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

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
                step(if (dt > 0.1f) 0.1f else dt)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    // ---------- har frame ka hisaab ----------
    private fun step(dt: Float) {
        time += dt
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        when (style) {
            "bubbles", "hearts" -> for (i in 0 until n) {
                py[i] += pvy[i] * dt
                px[i] += sin(time * 0.9f + pp[i]) * 14f * d * dt
                if (py[i] < -ps[i] * 2f) respawn(i, false)
            }
            "fireflies" -> for (i in 0 until n) {
                px[i] += cos(time * 0.6f + pp[i]) * 30f * d * dt
                py[i] += sin(time * 0.5f + pp[i] * 1.3f) * 30f * d * dt
                if (px[i] < 0f) px[i] = w
                if (px[i] > w) px[i] = 0f
                if (py[i] < 0f) py[i] = h
                if (py[i] > h) py[i] = 0f
            }
            "snow" -> for (i in 0 until n) {
                py[i] += pvy[i] * dt
                px[i] += sin(time * 0.7f + pp[i]) * 18f * d * dt
                if (py[i] > h + ps[i]) respawn(i, false)
            }
            "embers" -> for (i in 0 until n) {
                py[i] += pvy[i] * dt
                px[i] += sin(time * 2f + pp[i]) * 30f * d * dt
                if (py[i] < -ps[i]) respawn(i, false)
            }
            "confetti" -> for (i in 0 until n) {
                py[i] += pvy[i] * dt
                px[i] += pvx[i] * dt + sin(time * 1.5f + pp[i]) * 20f * d * dt
                if (py[i] > h + ps[i]) respawn(i, false)
            }
            "rain", "storm" -> {
                for (i in 0 until n) {
                    px[i] += pvx[i] * dt
                    py[i] += pvy[i] * dt
                    if (py[i] > h) respawn(i, false)
                }
                if (style == "storm") stepBolts(dt, 1f, 2.2f)
            }
            "galaxy" -> for (i in 0 until n) {
                pp[i] += dt * (0.5f * 60f * d / (pvx[i] + 40f * d))
            }
            "stars" -> {
                shootT -= dt
                if (shootLife > 0f) shootLife -= dt
                if (shootT <= 0f) {
                    shootT = 3f + rnd.nextFloat() * 5f
                    shootLife = 0.7f
                    shootX = rnd.nextFloat() * w * 0.7f
                    shootY = rnd.nextFloat() * h * 0.4f
                }
            }
            "matrix" -> {
                val cell = 16f * d
                val rows = (h / cell).toInt() + 2
                for (i in 0 until mCols) {
                    mHead[i] += mSpd[i] * dt
                    if (mHead[i] - mTrail[i] > rows) {
                        mHead[i] = -rnd.nextFloat() * 8f
                        mSpd[i] = 6f + rnd.nextFloat() * 10f
                        mTrail[i] = 8 + rnd.nextInt(14)
                    }
                }
            }
            "bijali" -> stepBolts(dt, 1.4f, 2.8f)
            "grid" -> scroll = (scroll + dt * 0.35f) % 1f
        }
    }

    private fun stepBolts(dt: Float, minGap: Float, extra: Float) {
        nextBolt -= dt
        if (nextBolt <= 0f) {
            makeBolt()
            // kabhi kabhi do baar jaldi jaldi chamakti hai
            nextBolt = if (rnd.nextInt(4) == 0) 0.14f else minGap + rnd.nextFloat() * extra
        }
        val it = bolts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= dt
            if (b.life <= 0f) it.remove()
        }
        flash = max(0f, flash - dt * 3.2f)
    }

    // ---------- chitra ----------
    private fun fill(col: Int, a: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = col
        paint.alpha = a.coerceIn(0, 255)
    }

    private fun stroke(col: Int, a: Int, w: Float) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = col
        paint.alpha = a.coerceIn(0, 255)
    }

    private fun pcol(i: Int): Int = when (pc[i]) {
        0 -> t.accent
        1 -> t.accent2
        else -> if (t.light) t.accent else Color.WHITE
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val wb = wall
        if (wb != null) {
            c.drawBitmap(wb, wallMatrix, wallPaint)
            if (dimColor != 0) c.drawColor(dimColor)
        } else {
            paint.style = Paint.Style.FILL
            paint.shader = base
            paint.alpha = 255
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        when (style) {
            "bubbles" -> drawBubbles(c)
            "fireflies" -> drawFireflies(c)
            "snow" -> for (i in 0 until n) {
                fill(pcol(i), 200)
                c.drawCircle(px[i], py[i], ps[i], paint)
            }
            "embers" -> for (i in 0 until n) {
                val col = if (pc[i] == 0) t.accent else t.accent2
                val fl = (120 + 100 * sin(time * 9f + pp[i])).toInt()
                fill(col, 40)
                c.drawCircle(px[i], py[i], ps[i] * 2.4f, paint)
                fill(col, fl)
                c.drawCircle(px[i], py[i], ps[i], paint)
            }
            "confetti" -> for (i in 0 until n) {
                fill(confettiColors[pc[i]], 220)
                c.save()
                c.translate(px[i], py[i])
                c.rotate(pp[i] * 57.3f)
                c.drawRect(-ps[i] / 2f, -ps[i] / 4f, ps[i] / 2f, ps[i] / 4f, paint)
                c.restore()
            }
            "hearts" -> for (i in 0 until n) {
                fill(if (pc[i] == 1) t.accent2 else t.accent, 120)
                heart(c, px[i], py[i], ps[i])
            }
            "rain" -> drawRain(c, 120)
            "storm" -> {
                drawRain(c, 90)
                drawBolts(c, w, h)
            }
            "bijali" -> drawBolts(c, w, h)
            "stars" -> drawStars(c, w, h)
            "galaxy" -> drawGalaxy(c, w, h)
            "matrix" -> drawMatrix(c)
            "grid" -> drawGrid(c, w, h)
            "aurora" -> drawAurora(c, w, h)
            "waves" -> drawWaves(c, w, h)
            "rays" -> drawRays(c, w, h)
            "rings" -> drawRings(c, w, h)
        }
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y + s * 0.9f)
        path.cubicTo(x - s * 1.3f, y + s * 0.2f, x - s * 0.9f, y - s * 0.9f, x, y - s * 0.35f)
        path.cubicTo(x + s * 0.9f, y - s * 0.9f, x + s * 1.3f, y + s * 0.2f, x, y + s * 0.9f)
        path.close()
        c.drawPath(path, paint)
    }

    private fun drawBubbles(c: Canvas) {
        for (i in 0 until n) {
            val col = pcol(i)
            fill(col, 22)
            c.drawCircle(px[i], py[i], ps[i], paint)
            stroke(col, 120, 1.4f * d)
            c.drawCircle(px[i], py[i], ps[i], paint)
            fill(Color.WHITE, if (t.light) 0 else 90)
            c.drawCircle(px[i] - ps[i] * 0.35f, py[i] - ps[i] * 0.35f, ps[i] * 0.16f, paint)
        }
    }

    private fun drawFireflies(c: Canvas) {
        for (i in 0 until n) {
            val col = if (pc[i] == 1) t.accent2 else t.accent
            val g = (70 + 60 * sin(time * 2f + pp[i])).toInt()
            fill(col, g)
            c.drawCircle(px[i], py[i], ps[i] * 3.4f, paint)
            fill(if (t.light) col else Color.WHITE, 230)
            c.drawCircle(px[i], py[i], ps[i], paint)
        }
    }

    private fun drawRain(c: Canvas, a: Int) {
        stroke(t.accent, a, 1.3f * d)
        for (i in 0 until n) {
            c.drawLine(px[i], py[i], px[i] - pvx[i] * 0.02f, py[i] - pvy[i] * 0.02f, paint)
        }
    }

    private fun drawBolts(c: Canvas, w: Float, h: Float) {
        // kaale baadal upar se
        paint.style = Paint.Style.FILL
        paint.shader = cloud
        paint.alpha = 255
        c.drawRect(0f, 0f, w, h * 0.35f, paint)
        paint.shader = null
        if (flash > 0f) {
            fill(AppTheme.mix(t.accent, Color.WHITE, 0.6f), (flash.coerceIn(0f, 1f) * 60f).toInt())
            c.drawRect(0f, 0f, w, h, paint)
        }
        for (b in bolts) {
            val fade = (b.life / b.max).coerceIn(0f, 1f)
            val flick = if ((b.life * 40f).toInt() % 3 == 0) 0.55f else 1f
            val k = fade * flick
            strokePath(c, b.xs, b.ys, k, 1f)
            for (br in b.branches) strokePath(c, br.first, br.second, k, 0.6f)
        }
    }

    private fun strokePath(c: Canvas, xs: FloatArray, ys: FloatArray, k: Float, wk: Float) {
        path.reset()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) path.lineTo(xs[i], ys[i])
        stroke(t.accent, (70 * k).toInt(), 16f * d * wk)
        c.drawPath(path, paint)
        stroke(t.accent, (170 * k).toInt(), 6f * d * wk)
        c.drawPath(path, paint)
        stroke(Color.WHITE, (255 * k).toInt(), 2.4f * d * wk)
        c.drawPath(path, paint)
    }

    private fun drawStars(c: Canvas, w: Float, h: Float) {
        for (i in 0 until n) {
            val a = (140 + 115 * sin(time * 1.8f + pp[i])).toInt().coerceIn(30, 255)
            fill(pcol(i), a)
            c.drawCircle(px[i], py[i], ps[i], paint)
        }
        if (shootLife > 0f) {
            val p = 1f - shootLife / 0.7f
            val x = shootX + p * w * 0.35f
            val y = shootY + p * h * 0.2f
            stroke(if (t.light) t.accent else Color.WHITE, (shootLife / 0.7f * 255f).toInt(), 2f * d)
            c.drawLine(x - 70f * d, y - 35f * d, x, y, paint)
        }
    }

    private fun drawGalaxy(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h * 0.4f
        val glow = RadialGradient(
            cx, cy, min(w, h) * 0.5f,
            intArrayOf(AppTheme.alpha(t.accent, 90), AppTheme.alpha(t.accent2, 30), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        paint.shader = glow
        paint.alpha = 255
        c.drawCircle(cx, cy, min(w, h) * 0.5f, paint)
        paint.shader = null
        for (i in 0 until n) {
            val r = pvx[i]
            val x = cx + cos(pp[i]) * r
            val y = cy + sin(pp[i]) * r * 0.6f
            val a = (110 + 100 * sin(time * 1.5f + i)).toInt().coerceIn(40, 255)
            fill(pcol(i), a)
            c.drawCircle(x, y, ps[i], paint)
        }
    }

    private fun drawMatrix(c: Canvas) {
        val cell = 16f * d
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = cell
        val rows = (height / cell).toInt() + 2
        val tick = (time * 6f).toInt()
        for (i in 0 until mCols) {
            val x = i * cell
            val headRow = mHead[i].toInt()
            val tl = mTrail[i]
            for (k in 0 until tl) {
                val row = headRow - k
                if (row < 0 || row > rows) continue
                val f = 1f - k.toFloat() / tl
                val a = (255f * f * f * 0.75f).toInt()
                if (a <= 4) continue
                val idx = Math.floorMod(i * 31 + row * 17 + (if (k == 0) tick * 7 else tick / 3), glyphs.size)
                paint.color = if (k == 0) AppTheme.mix(t.accent, Color.WHITE, 0.8f) else t.accent
                paint.alpha = a.coerceIn(0, 255)
                charBuf[0] = glyphs[idx]
                c.drawText(charBuf, 0, 1, x, (row + 1) * cell, paint)
            }
        }
        paint.typeface = null
    }

    private fun drawGrid(c: Canvas, w: Float, h: Float) {
        val horizon = h * 0.58f
        // retro suraj
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, horizon - 90f * d, 0f, horizon + 10f * d,
            AppTheme.alpha(t.accent2, 200), AppTheme.alpha(t.accent, 200), Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        c.drawCircle(w / 2f, horizon - 10f * d, 80f * d, paint)
        paint.shader = null
        // zameen
        fill(t.bg1, 215)
        c.drawRect(0f, horizon, w, h, paint)
        stroke(t.accent2, 150, 1.5f * d)
        c.drawLine(0f, horizon, w, horizon, paint)
        for (k in 0 until 12) {
            val f = (k + scroll) / 12f
            val y = horizon + (h - horizon) * f * f
            stroke(t.accent, (f * 200f).toInt(), 1.5f * d)
            c.drawLine(0f, y, w, y, paint)
        }
        stroke(t.accent, 110, 1.2f * d)
        for (i in -12..12) {
            c.drawLine(w / 2f + i * w * 0.01f, horizon, w / 2f + i * w * 0.16f, h, paint)
        }
    }

    private fun drawAurora(c: Canvas, w: Float, h: Float) {
        for (k in 0 until 3) {
            val sh = auroraShaders[k] ?: continue
            val topBase = h * (0.10f + 0.12f * k)
            val thick = h * 0.22f
            path.reset()
            var x = 0f
            val stepX = 18f * d
            var first = true
            while (x <= w + stepX) {
                val y = topBase + sin(x / w * 6.2832f * (1.2f + 0.4f * k) + time * (0.35f + 0.12f * k) + k * 2f) * h * 0.06f +
                    sin(x / w * 9f + time * 0.5f) * h * 0.02f
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += stepX
            }
            x -= stepX
            while (x >= -stepX) {
                val y = topBase + thick + sin(x / w * 6.2832f * (1.2f + 0.4f * k) + time * (0.35f + 0.12f * k) + k * 2f + 1f) * h * 0.05f
                path.lineTo(x, y)
                x -= stepX
            }
            path.close()
            paint.style = Paint.Style.FILL
            paint.shader = sh
            paint.alpha = 255
            c.drawPath(path, paint)
            paint.shader = null
        }
    }

    private fun drawWaves(c: Canvas, w: Float, h: Float) {
        for (l in 0 until 4) {
            val yBase = h * (0.72f + 0.07f * l)
            val amp = (14f + 6f * l) * d
            val freq = 6.2832f / (w * (0.8f + 0.2f * l))
            val speed = (0.8f + 0.3f * l) * (if (l % 2 == 0) 1f else -1f)
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            val stepX = 16f * d
            while (x <= w + stepX) {
                path.lineTo(x, yBase + sin(x * freq + time * speed + l * 1.7f) * amp)
                x += stepX
            }
            path.lineTo(w + stepX, h)
            path.close()
            fill(if (l % 2 == 0) t.accent else t.accent2, 0x30 + l * 0x0C)
            c.drawPath(path, paint)
        }
    }

    private fun drawRays(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h * 0.3f
        val len = max(w, h) * 1.4f
        val glow = RadialGradient(
            cx, cy, w * 0.8f,
            intArrayOf(AppTheme.alpha(t.accent, 70), 0), null, Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        paint.shader = glow
        paint.alpha = 255
        c.drawCircle(cx, cy, w * 0.8f, paint)
        paint.shader = null
        val rays = 14
        for (i in 0 until rays) {
            val a1 = time * 0.12f + i * (6.2832f / rays)
            val a2 = a1 + 0.12f
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(cx + len * cos(a1), cy + len * sin(a1))
            path.lineTo(cx + len * cos(a2), cy + len * sin(a2))
            path.close()
            fill(if (i % 2 == 0) t.accent else t.accent2, 0x18)
            c.drawPath(path, paint)
        }
    }

    private fun drawRings(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h * 0.55f
        val maxR = max(w, h) * 0.7f
        for (k in 0 until 4) {
            val ph = (time * 0.22f + k * 0.25f) % 1f
            stroke(if (k % 2 == 0) t.accent else t.accent2, ((1f - ph) * 140f).toInt(), 2.5f * d)
            c.drawCircle(cx, cy, ph * maxR, paint)
        }
    }
}
