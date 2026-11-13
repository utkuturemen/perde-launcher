package com.utku.perde

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Tiny LAN control panel at http://<projector-ip>:8080, usable from any phone,
 * tablet or PC browser on the same Wi-Fi:
 *  - upload an APK and install it (no USB stick needed)
 *  - list and open installed apps
 *  - device report and the remote's key log (from the diagnostics screen)
 */
object WebServer {

    const val PORT = 8080
    private const val MAX_UPLOAD = 400L * 1024 * 1024

    @Volatile private var server: ServerSocket? = null
    private lateinit var app: Context

    private val keyLog = ArrayDeque<String>()

    fun logKey(line: String) = synchronized(keyLog) {
        keyLog.addLast(line)
        while (keyLog.size > 200) keyLog.removeFirst()
    }

    fun start(ctx: Context) {
        if (server != null) return
        app = ctx.applicationContext
        thread(isDaemon = true, name = "perde-web") {
            val ss = runCatching { ServerSocket(PORT) }.getOrNull() ?: return@thread
            server = ss
            while (!ss.isClosed) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching { handle(socket) }
                    runCatching { socket.close() }
                }
            }
            server = null
        }
    }

    // ---------- HTTP ----------

    private fun handle(socket: Socket) {
        socket.soTimeout = 60_000
        val input = socket.getInputStream().buffered()
        val out = socket.getOutputStream()
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrElse(0) { "GET" }
        val target = parts.getOrElse(1) { "/" }
        val path = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))

        when {
            method == "GET" && path == "/" ->
                respond(out, 200, "text/html; charset=utf-8", PanelPage.HTML)
            method == "GET" && path == "/apps" ->
                respond(out, 200, "application/json; charset=utf-8", appsJson())
            method == "GET" && path == "/report" ->
                respond(out, 200, "text/plain; charset=utf-8", reportText())
            method == "POST" && path == "/launch" ->
                respond(out, 200, "text/plain; charset=utf-8", launch(query["pkg"].orEmpty()))
            method == "POST" && path == "/upload" -> {
                val length = headers["content-length"]?.toLongOrNull() ?: -1
                respond(out, 200, "text/plain; charset=utf-8", upload(input, length, query["name"].orEmpty()))
            }
            else -> respond(out, 404, "text/plain; charset=utf-8", "Bulunamadı")
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return sb.toString()
            sb.append(b.toChar())
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }.associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = if (code == 200) "OK" else "Not Found"
        out.write(
            ("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(bytes)
        out.flush()
    }

    // ---------- Routes ----------

    private fun appsJson(): String {
        val arr = JSONArray()
        for (a in AppRepository.load(app)) {
            arr.put(JSONObject().put("label", a.label).put("pkg", a.pkg))
        }
        return arr.toString()
    }

    private fun reportText(): String {
        val keys = synchronized(keyLog) { keyLog.joinToString("\n") }
        return DeviceReport.build(app) +
            "\n\n==================== KUMANDA TUŞLARI ====================\n" +
            keys.ifEmpty { "(Tanılama ekranında tuşlara basılmadı)" }
    }

    private fun launch(pkg: String): String {
        val pm = app.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            ?: return "Uygulama bulunamadı"
        return runCatching {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Açıldı"
        }.getOrElse { "Açılamadı (projektörde ana ekran açıkken dene)" }
    }

    private fun upload(input: InputStream, length: Long, name: String): String {
        if (length <= 0) return "Dosya boş"
        if (length > MAX_UPLOAD) return "Dosya çok büyük"
        if (!name.lowercase().endsWith(".apk")) return "Sadece .apk dosyaları yüklenebilir"
        val file = File(app.cacheDir, "upload.apk")
        file.outputStream().use { fileOut ->
            val buf = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n == -1) break
                fileOut.write(buf, 0, n)
                remaining -= n
            }
            if (remaining > 0) return "Yükleme yarıda kesildi"
        }
        return if (Installer.install(app, file)) {
            "Dosya alındı. Projektör ekranındaki kurulum onayına bas."
        } else {
            "Önce projektörde açılan ekrandan Perde'ye uygulama yükleme izni ver, sonra tekrar gönder."
        }
    }
}
