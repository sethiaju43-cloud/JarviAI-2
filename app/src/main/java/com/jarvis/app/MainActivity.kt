package com.jarvis.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.app.databinding.ActivityMainBinding
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var pet: PetAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private var lastTouch = System.currentTimeMillis()

    private val lines = listOf(
        "Hii! Main Jarvi hun\nBolo, kya karna hai? 😊",
        "Namaste! 🙏\nKya madad karun?",
        "Kya kar rahe ho? 😀",
        "Bore ho rahe ho? 🤔\nChalo baat karte hain!",
        "Main yahan hun! 🙌"
    )

    private val auto = object : Runnable {
        override fun run() {
            val p = pet
            if (p != null && !p.isSleeping()) {
                if (System.currentTimeMillis() - lastTouch > 60000) {
                    p.sleep()
                    b.tvBubble.text = "Zzz... 😴"
                } else if (!p.busy) {
                    when (rnd.nextInt(4)) {
                        0 -> hello()
                        1 -> {
                            b.tvBubble.text = lines[rnd.nextInt(lines.size)]
                            p.talk(3500)
                        }
                        2 -> {
                            b.tvBubble.text = "Hehe haha! 😄"
                            p.laugh(2200)
                        }
                        else -> {
                            b.tvBubble.text = "Hmm... sochne do 🤔"
                            p.think(2000)
                        }
                    }
                }
            }
            handler.postDelayed(this, 8000)
        }
    }

    private fun hello() {
        b.tvBubble.text = lines[rnd.nextInt(lines.size)]
        pet?.wave(2600)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        pet = PetAnimator(applicationContext, b.petBox, b.petFront, b.petBack)

        b.petBox.setOnClickListener {
            lastTouch = System.currentTimeMillis()
            val p = pet ?: return@setOnClickListener
            if (p.isSleeping()) {
                p.wake()
                b.tvBubble.text = "Arre! Jaag gayi 😄"
            } else {
                hello()
            }
        }

        b.btnMic.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        b.btnApps.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        handler.postDelayed({ hello() }, 600)
        handler.postDelayed(auto, 8000)
    }

    override fun onResume() {
        super.onResume()
        lastTouch = System.currentTimeMillis()
        b.rain.applyPrefs()
        b.rain.start()
        val floatingOn = getSharedPreferences("jarvis", MODE_PRIVATE)
            .getBoolean("floating", true)
        if (floatingOn && Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(
                this, Intent(this, FloatingService::class.java)
            )
        }
    }

    override fun onPause() {
        // Screen se bahar jate hi barish ruk jaye (battery)
        b.rain.stop()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pet?.release()
        super.onDestroy()
    }
}
