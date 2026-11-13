package com.utku.perde

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {

    /** A tile in the quick bar. [keywords] != null means it opens a user-chosen app. */
    private data class QuickAction(
        val id: String,
        val title: String,
        val icon: Int,
        val keywords: List<String>? = null,
    )

    private val quickActions = listOf(
        QuickAction("input", "HDMI / Kaynak", R.drawable.ic_input,
            listOf("hdmi", "source", "signal", "kaynak", "sinyal", "input", "tvinput")),
        QuickAction("mirror", "Yansıtma", R.drawable.ic_cast,
            listOf("mirror", "airplay", "airpin", "transcreen", "miracast", "eshare", "cast",
                "yansıt", "multiscreen", "screen share", "dlna")),
        QuickAction("settings", "Ayarlar", R.drawable.ic_settings),
        QuickAction("wifi", "Wi‑Fi", R.drawable.ic_wifi),
        QuickAction("bt", "Bluetooth", R.drawable.ic_bluetooth),
        QuickAction("clean", "Belleği boşalt", R.drawable.ic_bolt),
    )

    private lateinit var prefs: Prefs
    private lateinit var scroll: ScrollView
    private lateinit var shortcutBar: LinearLayout
    private lateinit var favRow: RecyclerView
    private lateinit var favEmpty: TextView
    private lateinit var netStatus: TextView
    private lateinit var ramStatus: TextView
    private lateinit var panelStatus: TextView
    private lateinit var favAdapter: AppAdapter
    private lateinit var gridAdapter: AppAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var apps: List<AppEntry> = emptyList()
    private var firstLoad = true

    private val ticker = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 3000)
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reloadApps()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withFixedDensity())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        scroll = findViewById(R.id.scroll)
        shortcutBar = findViewById(R.id.shortcut_bar)
        favRow = findViewById(R.id.fav_row)
        favEmpty = findViewById(R.id.fav_empty)
        netStatus = findViewById(R.id.net_status)
        ramStatus = findViewById(R.id.ram_status)
        panelStatus = findViewById(R.id.panel_status)

        favAdapter = AppAdapter(::launch, ::showAppMenu)
        favRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        favRow.adapter = favAdapter
        favRow.itemAnimator = null

        gridAdapter = AppAdapter(::launch, ::showAppMenu)
        val grid = findViewById<RecyclerView>(R.id.app_grid)
        grid.layoutManager = GridLayoutManager(this, 6)
        grid.adapter = gridAdapter
        grid.isNestedScrollingEnabled = false
        grid.itemAnimator = null

        buildQuickBar()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }

        reloadApps()
        WebServer.start(this)
        checkForUpdate(silent = true)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        executor.shutdownNow()
        super.onDestroy()
    }

    /** Home pressed while already home: jump back to the top. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        goTop()
    }

    @Deprecated("Launcher swallows back")
    override fun onBackPressed() {
        goTop()
    }

    private fun goTop() {
        scroll.smoothScrollTo(0, 0)
        focusStart()
    }

    private fun focusStart() {
        val target = favRow.getChildAt(0) ?: shortcutBar.getChildAt(0)
        target?.requestFocus()
    }

    // ---------- Apps ----------

    private fun reloadApps() {
        executor.execute {
            val list = AppRepository.load(applicationContext)
            handler.post {
                apps = list
                if (!prefs.initialized) {
                    val installed = list.map { it.pkg }.toSet()
                    prefs.favorites = AppRepository.DEFAULT_FAVORITES.filter { it in installed }
                    prefs.initialized = true
                }
                gridAdapter.items = list
                refreshFavorites()
                if (firstLoad) {
                    firstLoad = false
                    favRow.post { focusStart() }
                }
            }
        }
    }

    private fun refreshFavorites() {
        val byPkg = apps.associateBy { it.pkg }
        val favs = prefs.favorites.mapNotNull { byPkg[it] }
        favAdapter.items = favs
        favEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(app.pkg, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        start(intent)
    }

    private fun launchPackage(pkg: String) {
        apps.firstOrNull { it.pkg == pkg }?.let { launch(it); return }
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) start(intent) else toast("Uygulama bulunamadı")
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun showAppMenu(app: AppEntry) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (app.pkg in prefs.favorites) {
            actions += "Favorilerden çıkar" to { prefs.removeFavorite(app.pkg); refreshFavorites() }
            actions += "Sola taşı" to { prefs.moveFavorite(app.pkg, -1); refreshFavorites() }
            actions += "Sağa taşı" to { prefs.moveFavorite(app.pkg, 1); refreshFavorites() }
        } else {
            actions += "Favorilere ekle" to { prefs.addFavorite(app.pkg); refreshFavorites() }
        }
        actions += "Uygulama bilgisi" to {
            openOr(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
        }
        actions += "Kaldır" to {
            openOr(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
        }
        dialog().setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    // ---------- Quick bar ----------

    private fun buildQuickBar() {
        val inflater = LayoutInflater.from(this)
        val gap = (6 * resources.displayMetrics.density).toInt()
        quickActions.forEachIndexed { index, action ->
            val view = inflater.inflate(R.layout.item_shortcut, shortcutBar, false)
            view.findViewById<ImageView>(R.id.sc_icon).setImageResource(action.icon)
            view.findViewById<TextView>(R.id.sc_label).text = action.title
            view.layoutParams = LinearLayout.LayoutParams(0, view.layoutParams.height, 1f).apply {
                marginStart = if (index == 0) 8 * gap / 6 else gap
                marginEnd = if (index == quickActions.lastIndex) 8 * gap / 6 else gap
            }
            view.onFocusChangeListener = FocusScale
            view.setOnClickListener { onQuick(action) }
            view.setOnLongClickListener { onQuickLong(action); true }
            view.setOnKeyListener(menuKey { onQuickLong(action) })
            shortcutBar.addView(view)
        }
    }

    private fun onQuick(action: QuickAction) {
        when (action.id) {
            "settings" -> openOr(Intent(Settings.ACTION_SETTINGS))
            "wifi" -> openOr(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            "bt" -> openOr(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            "clean" -> cleanMemory()
            else -> openAssigned(action)
        }
    }

    private fun onQuickLong(action: QuickAction) {
        when {
            action.keywords != null -> pickAppFor(action)
            action.id == "settings" -> {
                val items = arrayOf(
                    "Güncellemeleri denetle", "Cihaz tanılama", "Android sistem ayarları",
                    "Cihaz bilgisi", "Geliştirici seçenekleri",
                )
                dialog().setTitle("Gelişmiş ayarlar")
                    .setItems(items) { _, i ->
                        when (i) {
                            0 -> checkForUpdate(silent = false)
                            1 -> startActivity(Intent(this, DiagnosticsActivity::class.java))
                            2 -> openOr(Intent().setClassName("com.android.settings", "com.android.settings.Settings"))
                            3 -> openOr(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                            4 -> openOr(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        }
                    }.show()
            }
        }
    }

    private fun openAssigned(action: QuickAction) {
        val saved = prefs.shortcutApp(action.id)?.takeIf { pkg -> apps.any { it.pkg == pkg } }
        val pkg = saved ?: detect(action.keywords.orEmpty())
        if (pkg != null) launchPackage(pkg) else pickAppFor(action)
    }

    private fun detect(keywords: List<String>): String? {
        val locale = Locale("tr", "TR")
        for (k in keywords) {
            apps.firstOrNull { it.label.lowercase(locale).contains(k) || it.pkg.lowercase(locale).contains(k) }
                ?.let { return it.pkg }
        }
        return null
    }

    private fun pickAppFor(action: QuickAction) {
        if (apps.isEmpty()) return
        dialog().setTitle("\"${action.title}\" için uygulama seç")
            .setItems(apps.map { it.label }.toTypedArray()) { _, i ->
                prefs.setShortcutApp(action.id, apps[i].pkg)
                launch(apps[i])
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** Try each intent in order; tell the user if none of them exists on this firmware. */
    private fun openOr(vararg intents: Intent) {
        if (intents.none { start(it) }) toast("Bu cihazda açılamadı")
    }

    // ---------- Updates ----------

    private fun checkForUpdate(silent: Boolean) {
        if (!silent) toast("Güncelleme denetleniyor…")
        executor.execute {
            val current = Updater.installedVersion(this)
            val latest = Updater.latestVersion()
            handler.post {
                when {
                    isFinishing -> Unit
                    latest == null -> if (!silent) toast("Sunucuya ulaşılamadı")
                    latest <= current -> if (!silent) toast("Perde güncel (sürüm $current)")
                    else -> dialog().setTitle("Yeni sürüm var")
                        .setMessage("Perde sürüm $latest hazır (yüklü: $current). Şimdi güncelleyelim mi?")
                        .setPositiveButton("Güncelle") { _, _ ->
                            toast("İndiriliyor…")
                            executor.execute { Updater.downloadAndInstall(this) }
                        }
                        .setNegativeButton("Sonra", null)
                        .show()
                }
            }
        }
    }

    // ---------- Memory ----------

    private fun availableMb(am: ActivityManager): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }

    private fun cleanMemory() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = availableMb(am)
        toast("Bellek boşaltılıyor…")
        executor.execute {
            val packages = runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
            for (info in packages) {
                if (info.packageName == packageName) continue
                runCatching { am.killBackgroundProcesses(info.packageName) }
            }
            Thread.sleep(800)
            val after = availableMb(am)
            handler.post {
                toast("+${max(0L, after - before)} MB boşaldı (boş: $after MB)")
                updateStatus()
            }
        }
    }

    // ---------- Status ----------

    private fun updateStatus() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val free = mi.availMem / (1024 * 1024)
        val total = mi.totalMem / (1024 * 1024)
        ramStatus.text = "RAM  $free / $total MB"
        ramStatus.setTextColor(getColor(if (free < 200 || mi.lowMemory) R.color.warn else R.color.text_primary))

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        netStatus.text = when {
            caps == null -> "Bağlantı yok"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi bağlı"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Bağlı"
        }

        val ip = localIpv4()
        panelStatus.visibility = if (ip != null) View.VISIBLE else View.GONE
        panelStatus.text = "Panel  $ip:${WebServer.PORT}"
    }

    // ---------- Helpers ----------

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
