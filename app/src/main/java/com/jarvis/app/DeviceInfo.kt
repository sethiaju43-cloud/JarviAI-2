package com.jarvis.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

object DeviceInfo {

    fun snapshot(ctx: Context): DeviceSnapshot {
        val app = ctx.applicationContext
        var total = 0L
        var avail = 0L
        var low = false
        try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            total = mi.totalMem / (1024 * 1024)
            avail = mi.availMem / (1024 * 1024)
            low = mi.lowMemory
        } catch (e: Exception) {
        }
        var free = 0L
        try {
            free = StatFs(app.filesDir.path).availableBytes / (1024 * 1024)
        } catch (e: Exception) {
        }
        var pct = -1
        var charging = false
        var temp = 0f
        try {
            val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) pct = level * 100 / scale
                charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            }
        } catch (e: Exception) {
        }
        var severe = false
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                severe = pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            } catch (e: Exception) {
            }
        }
        return DeviceSnapshot(
            total, avail, low, free, Build.VERSION.SDK_INT,
            Build.SUPPORTED_ABIS.toList(), pct, charging, temp, severe
        )
    }

    fun describe(s: DeviceSnapshot): String {
        val sb = StringBuilder()
        sb.append("Phone: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(s.sdk).append(")\n")
        sb.append("RAM: ").append(s.totalRamMb).append(" MB total, ").append(s.availRamMb).append(" MB abhi khali\n")
        sb.append("Storage khali: ").append(s.freeStorageMb).append(" MB\n")
        sb.append("CPU: ").append(s.abis.joinToString(", ")).append(
            if (s.arm64) " (local runtime OK)" else " (local runtime support nahi)"
        ).append("\n")
        if (s.batteryPct >= 0) {
            sb.append("Battery: ").append(s.batteryPct).append("%")
            if (s.charging) sb.append(" (charging)")
            sb.append(", ").append(s.tempC).append(" C")
        }
        return sb.toString()
    }
}
