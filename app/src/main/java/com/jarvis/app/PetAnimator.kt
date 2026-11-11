package com.jarvis.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView

object Poses {
    private val cache = HashMap<String, Bitmap?>()

    fun get(ctx: Context, name: String): Bitmap? {
        if (cache.containsKey(name)) return cache[name]
        val id = ctx.resources.getIdentifier("jarvi_" + name, "drawable", ctx.packageName)
        var bmp: Bitmap? = null
        if (id != 0) {
            val o = BitmapFactory.Options()
            o.inScaled = false
            bmp = BitmapFactory.decodeResource(ctx.resources, id, o)
        }
        cache[name] = bmp
        return bmp
    }
}

class PetAnimator(
    private val ctx: Context,
    private val root: View,
    private val front: ImageView,
    private val back: ImageView
) {
    private val h = Handler(Looper.getMainLooper())
    private var token = 0
    private var cur = ""
    private var pending: Bitmap? = null
    private var sleeping = false
    private val animators = ArrayList<ObjectAnimator>()

    var busy = false
        private set

    private val mouth = listOf(
        "talk_closed", "talk_open", "talk_wide", "talk_open",
        "talk_o", "talk_open", "talk_wide", "talk_closed"
    )

    init {
        root.post {
            root.pivotX = root.width / 2f
            root.pivotY = root.height.toFloat()
        }
        val sway = ObjectAnimator.ofFloat(root, "rotation", -1f, 1.2f)
        val breathe = ObjectAnimator.ofFloat(root, "scaleY", 1f, 1.018f)
        sway.duration = 1700
        breathe.duration = 2300
        for (a in listOf(sway, breathe)) {
            a.repeatCount = ValueAnimator.INFINITE
            a.repeatMode = ValueAnimator.REVERSE
            a.interpolator = AccelerateDecelerateInterpolator()
            a.start()
            animators.add(a)
        }
        show("talk_closed")
    }

    fun isSleeping(): Boolean {
        return sleeping
    }

    fun face(dir: Int) {
        val s = if (dir < 0) -1f else 1f
        front.scaleX = s
        back.scaleX = s
    }

    fun show(name: String) {
        if (name == cur) return
        val bmp = Poses.get(ctx, name) ?: return
        if (cur.isEmpty()) {
            front.setImageBitmap(bmp)
            cur = name
            return
        }
        cur = name
        back.animate().cancel()
        val old = pending
        if (old != null) front.setImageBitmap(old)
        pending = bmp
        back.setImageBitmap(bmp)
        back.alpha = 0f
        back.animate().alpha(1f).setDuration(90).withEndAction {
            front.setImageBitmap(bmp)
            back.alpha = 0f
            pending = null
        }.start()
    }

    private fun play(
        frames: List<String>,
        gap: Long,
        total: Long,
        onDone: Runnable?
    ) {
        token++
        val my = token
        busy = true
        sleeping = false
        val t0 = System.currentTimeMillis()
        var k = 0
        val step = object : Runnable {
            override fun run() {
                if (my != token) return
                if (System.currentTimeMillis() - t0 >= total) {
                    busy = false
                    show("talk_closed")
                    onDone?.run()
                    return
                }
                show(frames[k % frames.size])
                k++
                h.postDelayed(this, gap)
            }
        }
        step.run()
    }

    fun talk(ms: Long = 3500, onDone: Runnable? = null) {
        play(mouth, 120L, ms, onDone)
    }

    fun wave(ms: Long = 2600, onDone: Runnable? = null) {
        play(listOf("wave_high", "wave_low"), 380L, ms, onDone)
    }

    fun walkFrames(ms: Long, onDone: Runnable? = null) {
        play(listOf("walk_right", "walk_left"), 300L, ms, onDone)
    }

    fun namaste(ms: Long = 2000) {
        play(listOf("namaste"), 400L, ms, null)
    }

    fun think(ms: Long = 2000) {
        play(listOf("think"), 400L, ms, null)
    }

    fun laugh(ms: Long = 2200) {
        play(listOf("laugh", "talk_wide"), 260L, ms, null)
    }

    fun sleep() {
        token++
        busy = true
        sleeping = true
        show("sleep")
    }

    fun wake() {
        sleeping = false
        busy = false
        wave(2200)
    }

    fun stop() {
        token++
        busy = false
    }

    fun release() {
        token++
        h.removeCallbacksAndMessages(null)
        for (a in animators) a.cancel()
    }
}
