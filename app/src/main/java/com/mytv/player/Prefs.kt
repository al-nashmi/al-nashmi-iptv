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

    // ---- Parental controls ----
    /** Highest allowed age rating for movies and series; 18 means no limit. */
    var maxAge: Int
        get() = sp.getInt("max_age", 18)
        set(v) = sp.edit().putInt("max_age", v).apply()
    /** What to do with an unrated title while an age limit is set: "allow", "warn" or "block". */
    var unknownPolicy: String
        get() = sp.getString("unknown_policy", "warn")!!
        set(v) = sp.edit().putString("unknown_policy", v).apply()
    /** Show a warning before titles rated 13+ even when there is no age limit. */
    var warnMature: Boolean
        get() = sp.getBoolean("warn_mature", true)
        set(v) = sp.edit().putBoolean("warn_mature", v).apply()
    var customWords: String
        // Until the parent edits it, the list starts with the suggested words.
        get() = sp.getString("custom_words", null) ?: FamilyFilter.suggested
        set(v) = sp.edit().putString("custom_words", v).apply()
    var tmdbKey: String
        get() = sp.getString("tmdb_key", "")!!
        set(v) = sp.edit().putString("tmdb_key", v).apply()
    var pin: String?
        get() = sp.getString("pin", null)
        set(v) = sp.edit().putString("pin", v).apply()

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
