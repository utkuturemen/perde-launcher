package com.utku.perde

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.util.Locale

data class AppEntry(
    val pkg: String,
    val activity: String,
    val label: String,
    val icon: Drawable?,
    val banner: Drawable?,
)

object AppRepository {

    /** Every launchable app, TV (leanback) entries first so they keep their banners. */
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if (result.containsKey(ai.packageName)) continue
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
                val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                val banner = if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) {
                    runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
                } else null
                result[ai.packageName] = AppEntry(ai.packageName, ai.name, label, icon, banner)
            }
        }
        val locale = Locale("tr", "TR")
        return result.values.sortedBy { it.label.lowercase(locale) }
    }

    /** Apps worth pinning on first launch, if installed. */
    val DEFAULT_FAVORITES = listOf(
        "com.teamsmart.videomanager.tv",      // SmartTube
        "com.liskovsoft.smarttubetv.beta",    // SmartTube beta
        "com.liskovsoft.smarttubetv",
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.netflix.ninja",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "com.spotify.tv.android",
        "com.spotify.music",
        "org.videolan.vlc",
        "org.xbmc.kodi",
    )
}
