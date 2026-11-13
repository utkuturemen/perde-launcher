package com.utku.perde

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("perde", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString("favs", "").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = sp.edit().putString("favs", value.joinToString(",")).apply()

    var initialized: Boolean
        get() = sp.getBoolean("initialized", false)
        set(value) = sp.edit().putBoolean("initialized", value).apply()

    fun addFavorite(pkg: String) {
        if (pkg !in favorites) favorites = favorites + pkg
    }

    fun removeFavorite(pkg: String) {
        favorites = favorites - pkg
    }

    fun moveFavorite(pkg: String, delta: Int) {
        val list = favorites.toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.size - 1)
        list.removeAt(from)
        list.add(to, pkg)
        favorites = list
    }

    fun shortcutApp(id: String): String? = sp.getString("sc_$id", null)

    fun setShortcutApp(id: String, pkg: String) = sp.edit().putString("sc_$id", pkg).apply()
}
