package com.mytv.player

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("tvplayer", Context.MODE_PRIVATE)
    }

    fun load(): Account? {
        val type = sp.getString("type", null) ?: return null
        return Account(
            type = type,
            server = sp.getString("server", "")!!,
            user = sp.getString("user", "")!!,
            pass = sp.getString("pass", "")!!,
            m3uUrl = sp.getString("m3u", "")!!,
            format = sp.getString("format", "ts")!!,
            info = sp.getString("info", "")!!,
        )
    }

    fun save(a: Account) {
        sp.edit()
            .putString("type", a.type)
            .putString("server", a.server)
            .putString("user", a.user)
            .putString("pass", a.pass)
            .putString("m3u", a.m3uUrl)
            .putString("format", a.format)
            .putString("info", a.info)
            .apply()
    }

    fun clearAccount() {
        sp.edit().remove("type").apply()
    }

    // Favorites are stored as "KIND:id" keys.
    private fun favKey(item: Item) = "${item.kind}:${item.id}"

    fun favorites(): Set<String> = sp.getStringSet("favs", emptySet())!!

    fun isFavorite(item: Item) = favKey(item) in favorites()

    /** Returns true when the item is now a favorite. */
    fun toggleFavorite(item: Item): Boolean {
        val set = favorites().toMutableSet()
        val key = favKey(item)
        val added = if (key in set) { set.remove(key); false } else { set.add(key); true }
        sp.edit().putStringSet("favs", set).apply()
        return added
    }
}
