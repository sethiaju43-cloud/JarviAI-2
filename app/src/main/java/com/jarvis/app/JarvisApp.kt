package com.jarvis.app

import android.app.Application

class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Har screen par naya design (rang, background animation, DP) apne aap lagega
        registerActivityLifecycleCallbacks(SkinCallbacks())
    }

    /** Phone ki memory kam ho to local model hata do (crash se bachne ke liye) */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LocalLlm.onTrim(level)
    }
}
