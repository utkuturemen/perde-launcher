package com.utku.perde

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shows which keycode each remote button sends and serves a full device report
 * over the LAN (http://<ip>:8080) so it can be opened and saved from a computer.
 */
class DiagnosticsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var keys: TextView
    private val keyLog = ArrayDeque<String>()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withFixedDensity())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (40 * dp).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = "Cihaz tanılama"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.text_primary))
        })
        status = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(getColor(R.color.accent))
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
        }
        column.addView(status)
        column.addView(TextView(this).apply {
            text = "Kumandadaki tuşlara tek tek bas, her birinin kodu aşağıda görünecek ve rapora eklenecek. Çıkmak için Geri tuşu."
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        })
        keys = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        column.addView(keys)

        setContentView(ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_gradient)
            addView(column)
        })

        val ip = localIpv4()
        status.text = if (ip != null) {
            "Rapor bilgisayardan ya da tabletten:  http://$ip:${WebServer.PORT}/report"
        } else {
            "Ağ bağlantısı yok. Wi‑Fi'ye bağlanıp bu ekranı tekrar aç."
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val line = "${KeyEvent.keyCodeToString(event.keyCode)}  code=${event.keyCode}  scan=${event.scanCode}  device=${event.device?.name}"
            synchronized(keyLog) {
                keyLog.addFirst(line)
                while (keyLog.size > 40) keyLog.removeLast()
                keys.text = keyLog.joinToString("\n")
            }
            WebServer.logKey(line)
        }
        return true
    }
}
