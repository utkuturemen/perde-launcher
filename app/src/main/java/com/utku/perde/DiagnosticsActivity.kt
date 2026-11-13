package com.utku.perde

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Shows which keycode each remote button sends and serves a full device report
 * over the LAN (http://<ip>:8080) so it can be opened and saved from a computer.
 */
class DiagnosticsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var keys: TextView
    private val keyLog = ArrayDeque<String>()
    @Volatile private var report: String = "Rapor hazırlanıyor…"
    private var server: ServerSocket? = null

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
            text = "Rapor hazırlanıyor…"
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

        thread(isDaemon = true) {
            report = DeviceReport.build(applicationContext)
            val ip = localIpv4()
            runOnUiThread {
                status.text = if (ip != null) {
                    "Rapor hazır. Bilgisayarda aç:  http://$ip:8080   (sayfayı .txt olarak kaydet)"
                } else {
                    "Rapor hazır ama ağ bağlantısı yok. Wi‑Fi'ye bağlanıp bu ekranı tekrar aç."
                }
            }
        }
        startServer()
    }

    override fun onDestroy() {
        runCatching { server?.close() }
        super.onDestroy()
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
        }
        return true
    }

    private fun startServer() {
        thread(isDaemon = true) {
            val ss = runCatching { ServerSocket(8080) }.getOrNull() ?: return@thread
            server = ss
            while (!ss.isClosed) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use { s ->
                        s.getInputStream().bufferedReader().readLine()
                        val keyPart = synchronized(keyLog) { keyLog.reversed().joinToString("\n") }
                        val body = (report + "\n\n==================== KUMANDA TUŞLARI ====================\n" + keyPart)
                            .toByteArray(Charsets.UTF_8)
                        val out = s.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
                        )
                        out.write(body)
                        out.flush()
                    }
                }
            }
        }
    }
}
