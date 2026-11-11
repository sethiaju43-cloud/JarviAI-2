package com.jarvis.app

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.app.databinding.ActivityScreenBinding

/** Settings jaisi sab screens ka common dhaancha: upar title + back, neeche scroll hone wali list */
abstract class ScreenActivity : AppCompatActivity() {

    protected lateinit var container: LinearLayout
    private var builtVer = -1

    protected abstract fun screenTitle(): String
    protected abstract fun build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityScreenBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvTitle.text = screenTitle()
        b.btnBack.setOnClickListener { finish() }
        container = b.container
        builtVer = AppTheme.version(this)
        build()
    }

    protected fun rebuild() {
        container.removeAllViews()
        builtVer = AppTheme.version(this)
        build()
    }

    override fun onResume() {
        super.onResume()
        // Design badla ho to screen naye rang se dobara banao
        if (::container.isInitialized && (container.childCount == 0 || builtVer != AppTheme.version(this))) rebuild()
    }
}
