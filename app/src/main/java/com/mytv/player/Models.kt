package com.mytv.player

enum class Kind { LIVE, MOVIES, SERIES, EPISODE }

data class Category(val id: String, val name: String)

data class Item(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val kind: Kind,
    /** Direct stream URL (M3U). Xtream items build their URL from the account. */
    val url: String? = null,
    val ext: String? = null,
)

data class Season(val name: String, val episodes: List<Item>)

data class Account(
    val type: String,          // TYPE_XTREAM or TYPE_M3U
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val m3uUrl: String = "",
    val format: String = "ts", // live output: "ts" or "m3u8"
    val info: String = "",
) {
    companion object {
        const val TYPE_XTREAM = "xtream"
        const val TYPE_M3U = "m3u"
    }
}

/** Hands the list being played to PlayerActivity without squeezing it through an Intent. */
object PlayQueue {
    var items: List<Item> = emptyList()
    var index = 0
    var live = false
}
