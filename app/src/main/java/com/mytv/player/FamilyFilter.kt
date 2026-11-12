package com.mytv.player

import android.content.Context
import org.json.JSONObject

/**
 * Hides adult and music/dance content by matching category and title names against
 * the multilingual word lists in assets/filter_words.json plus the parent's own words.
 */
object FamilyFilter {
    private var adult: List<Regex> = emptyList()
    private var music: List<Regex> = emptyList()
    private var allow: List<Regex> = emptyList()
    private var custom: List<Regex> = emptyList()
    private var customSource: String? = null

    fun init(context: Context) {
        val json = JSONObject(context.assets.open("filter_words.json").bufferedReader().use { it.readText() })
        fun list(key: String) = json.getJSONArray(key).let { a -> (0 until a.length()).map { matcher(a.getString(it)) } }
        adult = list("adult")
        music = list("music")
        allow = list("allow")
        suggested = json.getJSONArray("suggested").let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } }
    }

    /** Suggested extra words, used as the starting value of the parent's own word list. */
    var suggested = ""
        private set

    /** Lower-cases and folds Arabic letter variants and diacritics so spelling differences still match. */
    fun normalize(s: String): String = s.lowercase()
        .replace(Regex("[أإآٱ]"), "ا")
        .replace('ة', 'ه')
        .replace('ى', 'ي')
        .replace(Regex("[ً-ٰٟـ]"), "")

    private fun matcher(word: String): Regex {
        val w = normalize(word.trim())
        val escaped = Regex.escape(w)
        // Latin words must stand alone ("sex" must not hit "Essex"); other scripts attach prefixes, so match anywhere.
        val latin = w.all { it.code < 0x250 }
        return if (latin) Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])") else Regex(escaped)
    }

    private fun customWords(): List<Regex> {
        val src = Prefs.customWords
        if (src != customSource) {
            customSource = src
            custom = src.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { matcher(it) }
        }
        return custom
    }

    /** True when a name (e.g. a genre) contains an adult word, regardless of the on/off switch. */
    fun isAdult(name: String?): Boolean =
        !name.isNullOrBlank() && normalize(name).let { n -> adult.any { it.containsMatchIn(n) } }

    /** True when a name must be hidden. Adult and music/dance blocking is permanent and has no switch. */
    fun blocks(name: String?, adultFlag: Boolean = false): Boolean {
        if (adultFlag) return true
        if (name.isNullOrBlank()) return false
        val n = normalize(name)
        if (adult.any { it.containsMatchIn(n) }) return true
        if (customWords().any { it.containsMatchIn(n) }) return true
        if (music.any { it.containsMatchIn(n) } && allow.none { it.containsMatchIn(n) }) return true
        return false
    }
}
