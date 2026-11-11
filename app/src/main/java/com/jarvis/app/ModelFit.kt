package com.jarvis.app

/** Ek local model ki jaankari (download se pehle dikhane ke liye) */
class LocalModel(
    val id: String,
    val name: String,
    val params: String,
    val sizeMb: Int,          // 0 = pata nahi (custom)
    val peakRamMb: Int,       // andaaza: chalte waqt RAM
    val minTotalRamMb: Int,   // isse kam total RAM par mat chalao
    val url: String,
    val fileName: String,
    val languages: String,
    val strengths: String,
    val license: String,
    val experimental: Boolean,
    val gated: Boolean,
    val note: String,
    val custom: Boolean = false
)

class DeviceSnapshot(
    val totalRamMb: Long,
    val availRamMb: Long,
    val lowMemory: Boolean,
    val freeStorageMb: Long,
    val sdk: Int,
    val abis: List<String>,
    val batteryPct: Int,
    val charging: Boolean,
    val tempC: Float,
    val thermalSevere: Boolean
) {
    val arm64: Boolean get() = abis.contains("arm64-v8a")
}

enum class Fit { OK, TIGHT, TOO_BIG, UNSUPPORTED, NO_STORAGE }

class FitResult(val fit: Fit, val message: String)

object ModelFit {
    fun evaluate(m: LocalModel, s: DeviceSnapshot, installed: Boolean): FitResult {
        if (!s.arm64 || s.sdk < 26) {
            return FitResult(Fit.UNSUPPORTED, "Is phone ka CPU/Android version local runtime ke liye support mein nahi (arm64 + Android 8+ chahiye)")
        }
        if (!installed && m.sizeMb > 0 && s.freeStorageMb < m.sizeMb + 300) {
            return FitResult(Fit.NO_STORAGE, "Storage kam hai: " + s.freeStorageMb + " MB khali, " + (m.sizeMb + 300) + " MB chahiye")
        }
        if (s.totalRamMb < m.minTotalRamMb) {
            return FitResult(Fit.TOO_BIG, "Ye model is phone ke liye bada hai (kam se kam ~" + m.minTotalRamMb / 1000.0 + " GB RAM chahiye)")
        }
        if (s.availRamMb < m.peakRamMb * 1.1) {
            return FitResult(Fit.TIGHT, "Abhi khali RAM " + s.availRamMb + " MB hai, model ko ~" + m.peakRamMb + " MB chahiye. Doosre apps band karo")
        }
        if (m.experimental && s.totalRamMb < 8000) {
            return FitResult(Fit.TIGHT, "Experimental: 6 GB RAM par chalne ki guarantee nahi hai, dheema ya band ho sakta hai")
        }
        return FitResult(Fit.OK, "Is phone par chal sakta hai")
    }

    /** Local model se bachna chahiye? (garam phone, kam battery, kam memory) */
    fun shouldAvoidLocal(s: DeviceSnapshot): Boolean {
        if (s.tempC >= 43f) return true
        if (s.thermalSevere) return true
        if (s.batteryPct in 0..15 && !s.charging) return true
        if (s.lowMemory) return true
        return false
    }
}
