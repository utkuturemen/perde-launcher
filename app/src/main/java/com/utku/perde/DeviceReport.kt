package com.utku.perde

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import java.io.File

/**
 * Plain-text dump of everything a non-root app can see about the firmware:
 * hardware, system properties, settings tables and every package's activities
 * and services. Used to find how the stock UI drives keystone, focus and inputs.
 */
object DeviceReport {

    fun build(ctx: Context): String = buildString {
        section("BUILD") {
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("brand=${Build.BRAND}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("product=${Build.PRODUCT}")
            appendLine("board=${Build.BOARD}")
            appendLine("hardware=${Build.HARDWARE}")
            appendLine("display=${Build.DISPLAY}")
            appendLine("fingerprint=${Build.FINGERPRINT}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            val dm = ctx.resources.displayMetrics
            appendLine("screen=${dm.widthPixels}x${dm.heightPixels} densityDpi=${dm.densityDpi}")
        }

        section("DRM (Netflix / Disney+ / Prime icin)") {
            appendLine(drmInfo())
            appendLine("google_play_services=${installed(ctx, "com.google.android.gms")}")
            appendLine("google_play_store=${installed(ctx, "com.android.vending")}")
        }

        section("CPUINFO") {
            appendLine(runCatching { File("/proc/cpuinfo").readText() }.getOrElse { "okunamadı: $it" })
        }

        section("GETPROP") {
            appendLine(exec("getprop"))
        }

        for ((name, uri) in listOf(
            "SETTINGS SYSTEM" to Settings.System.CONTENT_URI,
            "SETTINGS GLOBAL" to Settings.Global.CONTENT_URI,
            "SETTINGS SECURE" to Settings.Secure.CONTENT_URI,
        )) {
            section(name) { appendLine(dumpSettings(ctx, uri)) }
        }

        section("INPUT DEVICES") {
            for (id in InputDevice.getDeviceIds()) {
                val d = InputDevice.getDevice(id) ?: continue
                appendLine("$id: ${d.name} (vendor=${d.vendorId} product=${d.productId} sources=0x${Integer.toHexString(d.sources)})")
            }
        }

        section("PACKAGES") {
            val pm = ctx.packageManager
            val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
            for (p in packages.sortedBy { it.packageName }) {
                val info = runCatching {
                    pm.getPackageInfo(p.packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                }.getOrNull() ?: p
                val app = info.applicationInfo
                val system = app != null && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = app?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() } ?: ""
                appendLine("# ${info.packageName}  \"$label\"  ${if (system) "[system]" else "[user]"}  v${info.versionName}")
                info.activities?.forEach { a ->
                    appendLine("    activity ${a.name}${if (a.exported) "  (exported)" else ""}")
                }
                info.services?.forEach { s ->
                    appendLine("    service  ${s.name}${if (s.exported) "  (exported)" else ""}")
                }
            }
        }
    }

    private fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Widevine level decides whether streaming apps play at all, and in which quality. */
    private fun drmInfo(): String = runCatching {
        val widevine = java.util.UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        val drm = android.media.MediaDrm(widevine)
        try {
            listOf("securityLevel", "systemId", "hdcpLevel", "maxHdcpLevel", "version", "vendor")
                .joinToString("\n") { key ->
                    "widevine.$key=" + runCatching { drm.getPropertyString(key) }.getOrDefault("?")
                }
        } finally {
            drm.close()
        }
    }.getOrElse { "widevine yok ya da okunamadı: $it" }

    private fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("==================== $title ====================")
        runCatching { body() }.onFailure { appendLine("hata: $it") }
    }

    private fun dumpSettings(ctx: Context, uri: Uri): String = runCatching {
        val sb = StringBuilder()
        ctx.contentResolver.query(uri, arrayOf("name", "value"), null, null, "name")?.use { c ->
            while (c.moveToNext()) sb.appendLine("${c.getString(0)}=${c.getString(1)}")
        }
        sb.toString().ifEmpty { "(boş ya da erişim yok)" }
    }.getOrElse { "erişim yok: $it" }

    private fun exec(cmd: String): String = runCatching {
        val p = Runtime.getRuntime().exec(cmd)
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    }.getOrElse { "çalıştırılamadı: $it" }
}
