package com.utku.perde

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import java.net.Inet4Address
import kotlin.math.max

/**
 * Cheap projectors report odd densities. Pin layouts to a 960dp-wide canvas
 * so the UI looks the same whatever the firmware claims.
 */
fun Context.withFixedDensity(): Context {
    val dm = resources.displayMetrics
    val widthPx = max(dm.widthPixels, dm.heightPixels)
    if (widthPx <= 0) return this
    val config = Configuration(resources.configuration)
    config.densityDpi = widthPx * 160 / 960
    return createConfigurationContext(config)
}

/** The device's IPv4 address on the current network, or null. */
fun Context.localIpv4(): String? {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return props.linkAddresses.map { it.address }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}
