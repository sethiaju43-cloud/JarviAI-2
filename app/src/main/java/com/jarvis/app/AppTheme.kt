package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.LinearGradient
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

/** Ek poora "design": rang, accent aur default animation */
class ThemeDef(
    val id: String,
    val name: String,
    val bg1: Int,
    val bg2: Int,
    val bg3: Int,
    val accent: Int,
    val accent2: Int,
    val text: Int,
    val dim: Int,
    val light: Boolean,
    val style: String
)

/** Jarvis ke saare designs, animations, wallpaper aur DP ki settings ek jagah */
object AppTheme {

    fun mix(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
    }

    fun alpha(c: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun th(
        id: String, name: String, b1: String, b2: String, b3: String,
        a1: String, a2: String, style: String, light: Boolean = false
    ): ThemeDef {
        val bg1 = Color.parseColor(b1)
        val bg2 = Color.parseColor(b2)
        val bg3 = Color.parseColor(b3)
        val text = if (light) Color.parseColor("#2B1B3D") else Color.WHITE
        val dim = if (light) mix(text, bg1, 0.45f) else mix(Color.WHITE, bg2, 0.42f)
        return ThemeDef(id, name, bg1, bg2, bg3, Color.parseColor(a1), Color.parseColor(a2), text, dim, light, style)
    }

    /** 25 design */
    val all: List<ThemeDef> = listOf(
        th("bijli_neela", "\u26A1 Bijli Neela", "#02040F", "#0A1A4A", "#001A33", "#3DD6FF", "#B388FF", "bijali"),
        th("thunder_gold", "\u26A1 Thunder Gold", "#07070D", "#1A1A2E", "#2A2208", "#FFE600", "#00E5FF", "bijali"),
        th("bijli_jamuni", "\u26A1 Bijli Jamuni", "#0A0014", "#2A0050", "#12002B", "#E040FF", "#7C4DFF", "bijali"),
        th("toofan", "\u26C8 Toofan", "#0B0B1A", "#1B1033", "#08121F", "#C8A2FF", "#FFE66D", "storm"),
        th("jarvis", "\uD83D\uDC9A Jarvis Classic", "#050B14", "#07202B", "#04141C", "#19F5C8", "#2BFF7A", "matrix"),
        th("ironman", "\uD83E\uDDBE Ironman", "#1A0505", "#3B0A0A", "#240303", "#FFC93C", "#FF3B3B", "rays"),
        th("aurora", "\uD83C\uDF0C Aurora", "#021B1A", "#14123A", "#1B0B3A", "#5EFFC1", "#A66BFF", "aurora"),
        th("galaxy", "\uD83C\uDF00 Galaxy", "#05010F", "#1A0B3B", "#0B0620", "#FF7AD9", "#7AA7FF", "galaxy"),
        th("sunset", "\uD83C\uDF07 Sunset", "#1F0A33", "#7A1F5C", "#E8573A", "#FFD166", "#FF6B9A", "waves"),
        th("ocean", "\uD83C\uDF0A Ocean", "#00131F", "#003B5C", "#00203A", "#00E5FF", "#4DFFB8", "bubbles"),
        th("sakura", "\uD83C\uDF38 Sakura", "#2A0A1A", "#5C1A3A", "#3A0F28", "#FF8FB8", "#FFC2D9", "hearts"),
        th("jungle", "\uD83C\uDF3F Jungle", "#031A0A", "#0B3B1A", "#052512", "#7CFF6B", "#E0FF4F", "fireflies"),
        th("lava", "\uD83D\uDD25 Lava", "#1A0500", "#4A0E00", "#2A0800", "#FF7A1A", "#FFD23F", "embers"),
        th("ice", "\u2744\uFE0F Ice", "#061826", "#123B5A", "#0A2438", "#BDF0FF", "#7FB8FF", "snow"),
        th("cyberpunk", "\uD83D\uDD79 Cyberpunk", "#0D0221", "#2B0A4F", "#1A0536", "#FF2A6D", "#05D9E8", "grid"),
        th("gold", "\uD83D\uDC51 Gold Royal", "#0F0A02", "#2A1D05", "#1A1203", "#FFD54F", "#FFAB40", "stars"),
        th("midnight", "\uD83C\uDF19 Midnight", "#000000", "#0A0A14", "#05050A", "#8FA3FF", "#FF8FD1", "stars"),
        th("candy", "\uD83C\uDF6D Candy Pop", "#2A0A4A", "#5A0F7A", "#3A0A5A", "#FF5CCB", "#5CFFE0", "confetti"),
        th("matcha", "\uD83C\uDF75 Matcha", "#0A1A12", "#1F3B2A", "#12261B", "#B5F26B", "#6BF2C8", "bubbles"),
        th("rosegold", "\uD83C\uDF39 Rose Gold", "#1F0F14", "#4A2530", "#2E161E", "#FFB6A3", "#FFD9A8", "rays"),
        th("neonrain", "\uD83C\uDF06 Neon Rain", "#02030A", "#101030", "#07071C", "#00FFA3", "#FF00E5", "rain"),
        th("violet", "\uD83D\uDD2E Violet Dream", "#120A2E", "#3A1A7A", "#1E0F4A", "#C77DFF", "#7DF9FF", "rings"),
        th("cotton", "\uD83C\uDF6C Cotton Candy (light)", "#FFE3F1", "#E3E8FF", "#D6FFF4", "#D6338F", "#5B5BD6", "bubbles", true),
        th("sunrise", "\uD83C\uDF05 Sunrise (light)", "#FFF3D6", "#FFD9B3", "#FFC9C9", "#E8590C", "#C2255C", "rays", true),
        th("mint", "\uD83C\uDF43 Mint (light)", "#E6FFF5", "#CFF5FF", "#D8F5E6", "#00A878", "#0077B6", "waves", true)
    )

    /** Background animations: id to naam */
    val styles: List<Pair<String, String>> = listOf(
        "none" to "\uD83D\uDEAB Koi nahi (sirf rang)",
        "bijali" to "\u26A1 Bijli (lightning)",
        "storm" to "\u26C8 Toofan (bijli + barish)",
        "matrix" to "\uD83D\uDC9A Matrix barish",
        "grid" to "\uD83D\uDD79 Neon grid",
        "aurora" to "\uD83C\uDF0C Aurora",
        "stars" to "\u2728 Taare",
        "galaxy" to "\uD83C\uDF00 Galaxy",
        "bubbles" to "\uD83E\uDEE7 Bubbles",
        "fireflies" to "\uD83D\uDFE1 Jugnu",
        "rain" to "\uD83C\uDF27 Barish",
        "snow" to "\u2744\uFE0F Barf",
        "waves" to "\uD83C\uDF0A Lehrein",
        "hearts" to "\uD83D\uDC96 Dil",
        "embers" to "\uD83D\uDD25 Angaare",
        "confetti" to "\uD83C\uDF89 Confetti",
        "rays" to "\u2600\uFE0F Kirnein",
        "rings" to "\uD83D\uDCE1 Rings"
    )

    val frameNames: List<String> = listOf(
        "Koi nahi", "Neon ring", "Motti ring", "Dashed ring", "Double ring", "Rainbow disc",
        "Aura", "Aura 2", "Gradient disc", "Glass card", "Neon card", "Capsule", "Spotlight",
        "Sunset disc", "Dark disc", "Glow stack", "Star burst", "Hexagon", "Heart"
    )

    // ---------- chuni hui settings ----------
    fun byId(id: String): ThemeDef = all.firstOrNull { it.id == id } ?: all[0]
    fun current(ctx: Context): ThemeDef = byId(Prefs.str(ctx, "ui_theme", all[0].id))

    fun version(ctx: Context): Int = Prefs.int(ctx, "ui_ver", 0)
    fun bump(ctx: Context) = Prefs.setInt(ctx, "ui_ver", version(ctx) + 1)

    /** Naya theme chuna: uski apni default animation bhi lag jaati hai */
    fun setTheme(ctx: Context, id: String) {
        Prefs.setStr(ctx, "ui_theme", id)
        Prefs.setStr(ctx, "ui_style", "")
        bump(ctx)
    }

    fun style(ctx: Context): String {
        val s = Prefs.str(ctx, "ui_style", "")
        return if (s.isNotEmpty() && styles.any { it.first == s }) s else current(ctx).style
    }

    fun setStyle(ctx: Context, s: String) {
        Prefs.setStr(ctx, "ui_style", s)
        bump(ctx)
    }

    fun frame(ctx: Context): Int = Prefs.int(ctx, "ui_frame", 6).coerceIn(0, frameNames.size - 1)
    fun setFrame(ctx: Context, i: Int) {
        Prefs.setInt(ctx, "ui_frame", i)
        bump(ctx)
    }

    /** 0 = Jarvi character, 1 = meri photo */
    fun avatarMode(ctx: Context): Int = Prefs.int(ctx, "ui_avatar", 0)
    fun setAvatarMode(ctx: Context, m: Int) {
        Prefs.setInt(ctx, "ui_avatar", m)
        bump(ctx)
    }

    fun dim(ctx: Context): Int = Prefs.int(ctx, "ui_wall_dim", 35).coerceIn(0, 80)
    fun setDim(ctx: Context, v: Int) {
        Prefs.setInt(ctx, "ui_wall_dim", v)
        bump(ctx)
    }

    fun resetAll(ctx: Context) {
        Prefs.setStr(ctx, "ui_theme", all[0].id)
        Prefs.setStr(ctx, "ui_style", "")
        Prefs.setInt(ctx, "ui_frame", 6)
        Prefs.setInt(ctx, "ui_avatar", 0)
        Prefs.setInt(ctx, "ui_wall_dim", 35)
        try { wallpaperFile(ctx).delete() } catch (e: Exception) { }
        try { dpFile(ctx).delete() } catch (e: Exception) { }
        wallBmp = null
        dpBmp = null
        bump(ctx)
    }

    // ---------- rang ----------
    fun cardColor(t: ThemeDef): Int =
        if (t.light) alpha(Color.WHITE, 0xCC) else alpha(mix(t.bg2, Color.WHITE, 0.08f), 0xCC)

    private fun bubbleColor(t: ThemeDef, a: Int): Int =
        if (t.light) alpha(Color.WHITE, a) else alpha(mix(t.bg1, t.bg2, 0.5f), a)

    private fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    private fun box(ctx: Context, fill: Int, stroke: Int, strokeDp: Float, radiusDp: Float): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.setColor(fill)
        g.cornerRadius = dp(ctx, radiusDp)
        g.setStroke(dp(ctx, strokeDp).toInt().coerceAtLeast(1), stroke)
        return g
    }

    private fun grad(
        ctx: Context, colors: IntArray, o: GradientDrawable.Orientation,
        stroke: Int, strokeDp: Float, radiusDp: Float, oval: Boolean = false
    ): GradientDrawable {
        val g = GradientDrawable(o, colors)
        g.shape = if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (!oval) g.cornerRadius = dp(ctx, radiusDp)
        g.setStroke(dp(ctx, strokeDp).toInt().coerceAtLeast(1), stroke)
        return g
    }

    /** XML wale purane drawables ki jagah naye rang wale drawables */
    fun drawableFor(ctx: Context, role: String): Drawable {
        val t = current(ctx)
        return when (role) {
            "btn" -> grad(
                ctx, intArrayOf(alpha(t.accent, 0x3A), alpha(t.accent2, 0x3A)),
                GradientDrawable.Orientation.LEFT_RIGHT, alpha(t.accent, 0xDD), 1.5f, 14f
            )
            "mic" -> grad(
                ctx, intArrayOf(alpha(t.accent, 0x66), alpha(t.accent2, 0x66)),
                GradientDrawable.Orientation.TL_BR, t.accent, 3f, 0f, true
            )
            "bubble_main" -> box(ctx, bubbleColor(t, 0xEE), alpha(t.accent, 0xDD), 2f, 26f)
            "bubble" -> box(ctx, bubbleColor(t, 0xDD), alpha(t.accent, 0xAA), 1.5f, 22f)
            "bubble_small" -> box(ctx, bubbleColor(t, 0xEE), alpha(t.accent, 0xAA), 1.5f, 14f)
            "msg_bot" -> box(
                ctx,
                if (t.light) alpha(Color.WHITE, 0xEE) else alpha(mix(t.bg2, Color.WHITE, 0.14f), 0xEE),
                alpha(t.accent2, 0x44), 1f, 18f
            )
            "msg_user" -> grad(
                ctx, intArrayOf(alpha(t.accent, 0x55), alpha(t.accent2, 0x55)),
                GradientDrawable.Orientation.LEFT_RIGHT, alpha(t.accent, 0x99), 1f, 18f
            )
            else -> box(ctx, cardColor(t), alpha(t.accent, 0x55), 1f, 16f) // "card"
        }
    }

    // ---------- wallpaper aur DP ----------
    fun wallpaperFile(ctx: Context) = File(ctx.applicationContext.filesDir, "wallpaper.jpg")
    fun dpFile(ctx: Context) = File(ctx.applicationContext.filesDir, "dp.jpg")
    fun hasWallpaper(ctx: Context) = wallpaperFile(ctx).exists()
    fun hasDp(ctx: Context) = dpFile(ctx).exists()

    @Volatile private var wallBmp: Bitmap? = null
    @Volatile private var wallStamp = 0L
    @Volatile private var dpBmp: Bitmap? = null
    @Volatile private var dpStamp = 0L

    fun wallpaper(ctx: Context): Bitmap? {
        val f = wallpaperFile(ctx)
        if (!f.exists()) { wallBmp = null; return null }
        if (wallBmp != null && wallStamp == f.lastModified()) return wallBmp
        return try {
            val b = BitmapFactory.decodeFile(f.path)
            wallBmp = b
            wallStamp = f.lastModified()
            b
        } catch (e: Throwable) {
            null
        }
    }

    fun dpBitmap(ctx: Context): Bitmap? {
        val f = dpFile(ctx)
        if (!f.exists()) { dpBmp = null; return null }
        if (dpBmp != null && dpStamp == f.lastModified()) return dpBmp
        return try {
            val b = BitmapFactory.decodeFile(f.path)
            dpBmp = b
            dpStamp = f.lastModified()
            b
        } catch (e: Throwable) {
            null
        }
    }

    fun clearWallpaper(ctx: Context) {
        try { wallpaperFile(ctx).delete() } catch (e: Exception) { }
        wallBmp = null
        bump(ctx)
    }

    fun clearDp(ctx: Context) {
        try { dpFile(ctx).delete() } catch (e: Exception) { }
        dpBmp = null
        bump(ctx)
    }

    fun setWallpaper(ctx: Context, uri: Uri): Boolean = saveImage(ctx, uri, wallpaperFile(ctx), 2400, false)
    fun setDp(ctx: Context, uri: Uri): Boolean = saveImage(ctx, uri, dpFile(ctx), 512, true)

    private fun saveImage(ctx: Context, uri: Uri, out: File, maxPx: Int, square: Boolean): Boolean {
        return try {
            val cr = ctx.applicationContext.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var bmp: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false

            // Camera photo ghoomi hui ho to seedhi karo
            var rot = 0
            try {
                cr.openInputStream(uri)?.use {
                    val ex = ExifInterface(it)
                    rot = when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) { }
            if (rot != 0) {
                val m = Matrix()
                m.postRotate(rot.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            if (square) {
                val s = minOf(bmp.width, bmp.height)
                bmp = Bitmap.createBitmap(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s)
                if (s != maxPx) bmp = Bitmap.createScaledBitmap(bmp, maxPx, maxPx, true)
            } else {
                val big = maxOf(bmp.width, bmp.height)
                if (big > maxPx) {
                    val k = maxPx.toFloat() / big
                    bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
                }
            }
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            wallBmp = null
            dpBmp = null
            bump(ctx)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun frameDrawable(idx: Int, t: ThemeDef): Drawable? = if (idx <= 0) null else FrameArt(idx, t.accent, t.accent2)
}

/** DP ke peeche ki sajawat (19 tarah). 512x512 ke andar khinchti hai, kisi bhi size mein fit hoti hai */
class FrameArt(private val idx: Int, private val a: Int, private val b: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val m = 256f

    override fun getIntrinsicWidth() = 512
    override fun getIntrinsicHeight() = 512

    private fun al(c: Int, x: Int) = AppTheme.alpha(c, x)

    private fun pr(style: Paint.Style, color: Int, w: Float) {
        p.shader = null
        p.pathEffect = null
        p.style = style
        p.color = color
        p.strokeWidth = w
        p.strokeJoin = Paint.Join.ROUND
    }

    private fun ring(c: Canvas, r: Float, w: Float, col: Int) {
        pr(Paint.Style.STROKE, col, w)
        c.drawCircle(m, m, r, p)
    }

    private fun disc(c: Canvas, r: Float, col: Int) {
        pr(Paint.Style.FILL, col, 0f)
        c.drawCircle(m, m, r, p)
    }

    private fun roundBox(c: Canvas, l: Float, t: Float, r: Float, bt: Float, rad: Float, fill: Int, stroke: Int, w: Float) {
        rect.set(l, t, r, bt)
        pr(Paint.Style.FILL, fill, 0f)
        c.drawRoundRect(rect, rad, rad, p)
        pr(Paint.Style.STROKE, stroke, w)
        c.drawRoundRect(rect, rad, rad, p)
    }

    override fun draw(c: Canvas) {
        when (idx) {
            1 -> { ring(c, 240f, 26f, al(a, 60)); ring(c, 240f, 10f, a) }
            2 -> { disc(c, 246f, al(a, 36)); ring(c, 232f, 30f, b) }
            3 -> {
                pr(Paint.Style.STROKE, a, 12f)
                p.pathEffect = DashPathEffect(floatArrayOf(34f, 24f), 0f)
                c.drawCircle(m, m, 240f, p)
            }
            4 -> { ring(c, 244f, 10f, a); ring(c, 208f, 6f, b) }
            5 -> {
                val cols = intArrayOf(
                    Color.argb(140, 255, 59, 59), Color.argb(140, 255, 214, 0), Color.argb(140, 76, 255, 80),
                    Color.argb(140, 0, 229, 255), Color.argb(140, 124, 77, 255), Color.argb(140, 255, 64, 200),
                    Color.argb(140, 255, 59, 59)
                )
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = SweepGradient(m, m, cols, null)
                c.drawCircle(m, m, 246f, p)
                ring(c, 242f, 8f, al(Color.WHITE, 150))
            }
            6 -> {
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = RadialGradient(m, m, 252f, intArrayOf(al(a, 170), al(a, 70), 0), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(m, m, 252f, p)
            }
            7 -> {
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = RadialGradient(m, m, 252f, intArrayOf(al(b, 170), al(b, 60), 0), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(m, m, 252f, p)
                ring(c, 236f, 6f, al(a, 200))
            }
            8 -> {
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = LinearGradient(0f, 20f, 0f, 490f, al(a, 150), al(b, 150), Shader.TileMode.CLAMP)
                c.drawCircle(m, m, 240f, p)
                ring(c, 240f, 6f, al(Color.WHITE, 120))
            }
            9 -> roundBox(c, 16f, 16f, 496f, 496f, 96f, al(Color.WHITE, 34), al(Color.WHITE, 110), 8f)
            10 -> {
                roundBox(c, 16f, 16f, 496f, 496f, 96f, al(Color.BLACK, 100), al(a, 60), 28f)
                pr(Paint.Style.STROKE, a, 10f)
                rect.set(16f, 16f, 496f, 496f)
                c.drawRoundRect(rect, 96f, 96f, p)
            }
            11 -> {
                rect.set(96f, 12f, 416f, 500f)
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = LinearGradient(0f, 0f, 0f, 512f, al(a, 110), al(b, 110), Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, 160f, 160f, p)
                pr(Paint.Style.STROKE, a, 8f)
                c.drawRoundRect(rect, 160f, 160f, p)
            }
            12 -> {
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = RadialGradient(m, m, 252f, intArrayOf(al(Color.WHITE, 150), al(Color.WHITE, 40), 0), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(m, m, 252f, p)
            }
            13 -> {
                pr(Paint.Style.FILL, Color.WHITE, 0f)
                p.shader = LinearGradient(0f, 10f, 0f, 500f, al(b, 210), al(a, 210), Shader.TileMode.CLAMP)
                c.drawCircle(m, m, 244f, p)
                ring(c, 244f, 6f, al(Color.WHITE, 100))
            }
            14 -> { disc(c, 244f, al(Color.BLACK, 130)); ring(c, 240f, 10f, al(a, 210)) }
            15 -> { disc(c, 250f, al(a, 26)); disc(c, 200f, al(a, 44)); disc(c, 150f, al(b, 70)) }
            16 -> {
                path.reset()
                for (i in 0 until 24) {
                    val r = if (i % 2 == 0) 246f else 172f
                    val ang = Math.PI * 2 * i / 24 - Math.PI / 2
                    val x = m + (r * cos(ang)).toFloat()
                    val y = m + (r * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                pr(Paint.Style.FILL, al(a, 110), 0f)
                c.drawPath(path, p)
                pr(Paint.Style.STROKE, b, 8f)
                c.drawPath(path, p)
            }
            17 -> {
                path.reset()
                for (i in 0 until 6) {
                    val ang = Math.PI * 2 * i / 6 - Math.PI / 2
                    val x = m + (238f * cos(ang)).toFloat()
                    val y = m + (238f * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                pr(Paint.Style.FILL, al(b, 60), 0f)
                c.drawPath(path, p)
                pr(Paint.Style.STROKE, a, 12f)
                c.drawPath(path, p)
            }
            18 -> {
                path.reset()
                path.moveTo(256f, 468f)
                path.cubicTo(60f, 322f, 20f, 160f, 140f, 90f)
                path.cubicTo(210f, 50f, 256f, 110f, 256f, 140f)
                path.cubicTo(256f, 110f, 302f, 50f, 372f, 90f)
                path.cubicTo(492f, 160f, 452f, 322f, 256f, 468f)
                path.close()
                pr(Paint.Style.FILL, al(a, 90), 0f)
                c.drawPath(path, p)
                pr(Paint.Style.STROKE, a, 12f)
                c.drawPath(path, p)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
