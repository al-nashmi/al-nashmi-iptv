package com.mytv.player

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Talks to the Xtream Codes player API or parses an M3U playlist.
 * All functions block; call them from Dispatchers.IO.
 */
object Repo {
    const val CAT_ALL = "__all__"
    const val CAT_FAV = "__fav__"

    var account: Account? = null
        set(value) {
            field = value
            synchronized(this) { cache.clear() }
        }

    private val cache = HashMap<Kind, List<Item>>()

    private val acc get() = account ?: error("no account")
    private val isM3u get() = acc.type == Account.TYPE_M3U
    private val base get() = acc.server.trimEnd('/')

    private fun api(action: String?, extra: String = ""): String {
        val url = buildString {
            append(base).append("/player_api.php?username=").append(Uri.encode(acc.user))
            append("&password=").append(Uri.encode(acc.pass))
            if (action != null) append("&action=").append(action)
            append(extra)
        }
        return Net.get(url)
    }

    /** Checks the account. Returns an error message, or null plus a status line on success. */
    fun login(): Pair<String?, String> {
        if (isM3u) {
            val items = allItems(Kind.LIVE)
            return if (items.isEmpty()) "القائمة فارغة أو الرابط غير صحيح." to ""
            else null to "${items.size} قناة"
        }
        val text = api(null)
        val json = try { JSONObject(text) } catch (e: Exception) {
            return "رد السيرفر غير مفهوم. تأكد من رابط السيرفر." to ""
        }
        val info = json.optJSONObject("user_info") ?: return "رد السيرفر غير مفهوم." to ""
        if (info.optString("auth") != "1") return "اسم المستخدم أو كلمة المرور غير صحيحة." to ""
        val status = info.optString("status")
        if (status.isNotEmpty() && !status.equals("Active", true)) return "الاشتراك غير فعّال ($status)." to ""
        val exp = info.optString("exp_date").toLongOrNull()
        val expText = if (exp != null && exp > 0) {
            "ينتهي الاشتراك: " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(exp * 1000))
        } else "اشتراك بدون تاريخ انتهاء"
        return null to expText
    }

    fun categories(kind: Kind): List<Category> {
        if (isM3u) {
            return allItems(kind).map { it.categoryId }.distinct().map { Category(it, it) }
        }
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIES -> "get_vod_categories"
            else -> "get_series_categories"
        }
        val arr = jsonArray(api(action))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Category(o.optString("category_id"), o.optString("category_name"))
        }
    }

    fun items(kind: Kind, categoryId: String): List<Item> {
        val all = allItems(kind)
        return when (categoryId) {
            CAT_ALL -> all
            CAT_FAV -> {
                val favs = Prefs.favorites()
                all.filter { "${it.kind}:${it.id}" in favs }
            }
            else -> all.filter { it.categoryId == categoryId }
        }
    }

    /** Every stream of a kind is fetched once and filtered locally, which also makes search instant. */
    @Synchronized
    private fun allItems(kind: Kind): List<Item> {
        cache[kind]?.let { return it }
        val list = if (isM3u) parseM3u(Net.get(acc.m3uUrl)) else fetchStreams(kind)
        cache[kind] = list
        return list
    }

    private fun fetchStreams(kind: Kind): List<Item> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIES -> "get_vod_streams"
            else -> "get_series"
        }
        val arr = jsonArray(api(action))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            when (kind) {
                Kind.LIVE -> Item(o.optString("stream_id"), o.optString("name"),
                    o.optString("stream_icon").ifBlank { null }, o.optString("category_id"), Kind.LIVE)
                Kind.MOVIES -> Item(o.optString("stream_id"), o.optString("name"),
                    o.optString("stream_icon").ifBlank { null }, o.optString("category_id"), Kind.MOVIES,
                    ext = o.optString("container_extension").ifBlank { "mp4" })
                else -> Item(o.optString("series_id"), o.optString("name"),
                    o.optString("cover").ifBlank { null }, o.optString("category_id"), Kind.SERIES)
            }
        }
    }

    fun seriesInfo(seriesId: String): List<Season> {
        val json = JSONObject(api("get_series_info", "&series_id=$seriesId"))
        val seasons = mutableListOf<Season>()
        fun addSeason(key: String, arr: JSONArray) {
            val eps = (0 until arr.length()).map {
                val e = arr.getJSONObject(it)
                val num = e.optString("episode_num")
                Item(
                    id = e.optString("id"),
                    name = e.optString("title").ifBlank { "الحلقة $num" },
                    logo = e.optJSONObject("info")?.optString("movie_image")?.ifBlank { null },
                    categoryId = key,
                    kind = Kind.EPISODE,
                    ext = e.optString("container_extension").ifBlank { "mp4" },
                )
            }
            if (eps.isNotEmpty()) seasons.add(Season("الموسم $key", eps))
        }
        // Panels return episodes either as {"1": [...], "2": [...]} or as [[...], [...]].
        when (val eps = json.opt("episodes")) {
            is JSONObject -> eps.keys().asSequence()
                .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                .forEach { addSeason(it, eps.getJSONArray(it)) }
            is JSONArray -> for (i in 0 until eps.length()) {
                eps.optJSONArray(i)?.let { addSeason("${i + 1}", it) }
            }
        }
        return seasons
    }

    fun playUrl(item: Item): String {
        item.url?.let { return it }
        val u = Uri.encode(acc.user)
        val p = Uri.encode(acc.pass)
        return when (item.kind) {
            Kind.LIVE -> "$base/live/$u/$p/${item.id}.${acc.format}"
            Kind.MOVIES -> "$base/movie/$u/$p/${item.id}.${item.ext}"
            else -> "$base/series/$u/$p/${item.id}.${item.ext}"
        }
    }

    private fun jsonArray(text: String): JSONArray =
        if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray()

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parseM3u(text: String): List<Item> {
        val items = mutableListOf<Item>()
        var name = ""
        var logo: String? = null
        var group = "عام"
        var extGroup: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.ifBlank { null }
                    group = attrs["group-title"]?.ifBlank { null } ?: extGroup ?: "عام"
                    // The title follows the first comma after the attributes.
                    val afterAttrs = line.lastIndexOf('"').coerceAtLeast(0)
                    val comma = line.indexOf(',', afterAttrs)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                }
                line.startsWith("#EXTGRP:", true) -> extGroup = line.substringAfter(':').trim()
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    items.add(Item("${items.size}", name.ifBlank { "قناة ${items.size + 1}" }, logo, group, Kind.LIVE, url = line))
                    name = ""; logo = null; extGroup = null
                }
            }
        }
        return items
    }
}
