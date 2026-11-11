package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.app.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var b: ActivityPermissionsBinding

    private class Perm(
        val title: String,
        val sub: String,
        val granted: () -> Boolean,
        val action: () -> Unit
    )

    private val launcher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    private lateinit var perms: List<Perm>

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun accEnabledInSettings(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }

        perms = listOf(
            Perm("Accessibility Service", "Back, Home, Scroll, Tap",
                { JarvisAccessibilityService.instance != null },
                { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }),

            Perm("Display over other apps", "Floating Jarvi",
                { Settings.canDrawOverlays(this) },
                {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }),

            Perm("Microphone", "Voice input",
                { has(Manifest.permission.RECORD_AUDIO) },
                { launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }),

            Perm("Camera", "Photo / Selfie",
                { has(Manifest.permission.CAMERA) },
                { launcher.launch(arrayOf(Manifest.permission.CAMERA)) }),

            Perm("Phone & SMS", "Call, Message (optional)",
                { has(Manifest.permission.CALL_PHONE) && has(Manifest.permission.SEND_SMS) },
                {
                    launcher.launch(
                        arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS)
                    )
                }),

            Perm("Contacts", "Naam se call karne ke liye",
                { has(Manifest.permission.READ_CONTACTS) },
                { launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }),

            Perm("Notifications", "Background running",
                {
                    Build.VERSION.SDK_INT < 33 ||
                        has(Manifest.permission.POST_NOTIFICATIONS)
                },
                {
                    if (Build.VERSION.SDK_INT >= 33) {
                        launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                })
        )

        for (p in perms) {
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_permission, b.container, false)
            row.findViewById<TextView>(R.id.tvPermTitle).text = p.title
            row.findViewById<TextView>(R.id.tvPermSub).text = p.sub
            row.findViewById<TextView>(R.id.btnAllow).setOnClickListener { p.action() }
            b.container.addView(row)
        }

        // Pichli galti (agar app kabhi giri ho) neeche dikhegi
        val crash = getSharedPreferences("jarvis", MODE_PRIVATE).getString("crash", "") ?: ""
        if (crash.isNotBlank()) {
            val tv = TextView(this)
            tv.text = "Pichli galti (screenshot bhej dena):\n" + crash
            tv.setTextColor(Color.parseColor("#FF8A80"))
            tv.textSize = 10f
            tv.setPadding(40, 30, 40, 30)
            b.container.addView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        for (i in perms.indices) {
            val row = b.container.getChildAt(i)
            val btn = row.findViewById<TextView>(R.id.btnAllow)
            if (perms[i].granted()) {
                btn.text = "Done ✓"
                btn.alpha = 0.5f
            } else {
                btn.text = "Allow"
                btn.alpha = 1f
            }
        }
        // Accessibility chalu dikh rahi ho par atki ho to bata do
        val sub = b.container.getChildAt(0).findViewById<TextView>(R.id.tvPermSub)
        if (JarvisAccessibilityService.instance == null && accEnabledInSettings()) {
            sub.text = "Atki hui hai: Allow dabao, Jarvis Phone Control OFF phir ON karo"
        } else {
            sub.text = "Back, Home, Scroll, Tap"
        }
    }
}
