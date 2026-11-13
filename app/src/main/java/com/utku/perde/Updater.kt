package com.utku.perde

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks the repo's builds branch for a newer Perde and installs it. */
object Updater {

    private const val BASE = "https://raw.githubusercontent.com/utkuturemen/perde-launcher/builds"

    fun installedVersion(ctx: Context): Long {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** Latest published versionCode, or null if offline. Call off the main thread. */
    fun latestVersion(): Long? = runCatching {
        val text = open("$BASE/version.json").inputStream.bufferedReader().use { it.readText() }
        JSONObject(text).getLong("versionCode")
    }.getOrNull()

    /** Downloads the latest APK and hands it to the installer. Call off the main thread. */
    fun downloadAndInstall(ctx: Context): Boolean {
        val target = File(ctx.cacheDir, "update.apk")
        val ok = runCatching {
            open("$BASE/perde.apk").inputStream.use { input ->
                target.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
        }.isSuccess
        if (!ok) {
            Installer.toast(ctx, "Güncelleme indirilemedi")
            return false
        }
        return Installer.install(ctx, target)
    }

    private fun open(url: String): HttpURLConnection =
        (URL("$url?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
        }
}
