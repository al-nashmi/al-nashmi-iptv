package com.mytv.player

import android.net.Uri
import org.json.JSONObject

data class Rating(val age: Int?, val label: String)

/**
 * Finds the age rating of a movie or series: first from the IPTV panel's own info,
 * then (if the parent added a free TMDB key) from The Movie Database.
 * Blocking; call from Dispatchers.IO.
 */
object AgeRating {
    private val cache = HashMap<String, Rating>()

    // US, UK, Australian and TV certifications mapped to the minimum age.
    private val codes = mapOf(
        "G" to 0, "U" to 0, "E" to 0, "AL" to 0, "TV-Y" to 0, "TV-G" to 0,
        "TV-Y7" to 7, "TV-Y7-FV" to 7, "PG" to 8, "TV-PG" to 10, "12A" to 12,
        "PG-13" to 13, "TV-14" to 14, "M" to 15, "MA15+" to 15,
        "R" to 17, "TV-MA" to 17, "NC-17" to 18, "X" to 18, "R18" to 18, "R18+" to 18,
    )

    fun parse(raw: String?): Int? {
        val s = raw?.trim()?.uppercase()?.removePrefix("RATED")?.trim().orEmpty()
        if (s.isEmpty()) return null
        codes[s]?.let { return it }
        return Regex("\\d{1,2}").find(s)?.value?.toInt()?.takeIf { it in 0..21 }
    }

    fun lookup(item: Item): Rating {
        val key = "${item.kind}:${item.id}"
        synchronized(cache) { cache[key]?.let { return it } }

        var age: Int? = null
        var label: String? = null
        val info = try { Repo.titleInfo(item) } catch (e: Exception) { null }
        if (info != null) {
            for (field in listOf("mpaa_rating", "age", "certification", "content_rating", "rating_mpaa")) {
                val v = info.optString(field)
                val a = parse(v)
                if (a != null) { age = a; label = v; break }
            }
            val genre = info.optString("genre")
            if (FamilyFilter.isAdult(genre)) { age = 18; label = genre }
        }
        if (age == null && Prefs.tmdbKey.isNotBlank()) {
            try {
                tmdb(item, info)?.let { (a, l) -> age = a; label = l }
            } catch (_: Exception) {
            }
        }
        val rating = Rating(age, label?.trim()?.ifBlank { null } ?: "غير مصنّف")
        synchronized(cache) { cache[key] = rating }
        return rating
    }

    private fun tmdb(item: Item, info: JSONObject?): Pair<Int, String>? {
        val key = Uri.encode(Prefs.tmdbKey.trim())
        val tv = item.kind == Kind.SERIES
        val api = "https://api.themoviedb.org/3"
        var id = info?.let { it.optString("tmdb_id").ifBlank { it.optString("tmdb") } }
            ?.takeIf { it.isNotBlank() && it != "0" }
        if (id == null) {
            // Panels often decorate names ("AR - Title (2020) [4K]"); search with the bare title and year.
            val year = Regex("\\b(19|20)\\d{2}\\b").find(item.name)?.value
            val query = item.name
                .replace(Regex("^[A-Za-z]{2,3}\\s*[-|:]\\s*"), "")
                .replace(Regex("[\\[(|].*?[\\])|]"), " ")
                .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
                .trim()
            val yearParam = year?.let { if (tv) "&first_air_date_year=$it" else "&year=$it" } ?: ""
            val first = JSONObject(Net.get("$api/search/${if (tv) "tv" else "movie"}?api_key=$key&query=${Uri.encode(query)}$yearParam"))
                .optJSONArray("results")?.optJSONObject(0) ?: return null
            if (first.optBoolean("adult")) return 18 to "Adult"
            id = first.optString("id")
        }

        val byCountry = LinkedHashMap<String, String>()
        if (tv) {
            val results = JSONObject(Net.get("$api/tv/$id/content_ratings?api_key=$key")).optJSONArray("results")
            for (i in 0 until (results?.length() ?: 0)) {
                val r = results!!.getJSONObject(i)
                r.optString("rating").takeIf { it.isNotBlank() }?.let { byCountry[r.optString("iso_3166_1")] = it }
            }
        } else {
            val results = JSONObject(Net.get("$api/movie/$id/release_dates?api_key=$key")).optJSONArray("results")
            for (i in 0 until (results?.length() ?: 0)) {
                val r = results!!.getJSONObject(i)
                val dates = r.optJSONArray("release_dates") ?: continue
                for (j in 0 until dates.length()) {
                    val c = dates.getJSONObject(j).optString("certification")
                    if (c.isNotBlank()) { byCountry[r.optString("iso_3166_1")] = c; break }
                }
            }
        }
        // Prefer Gulf ratings, then US and UK, then whatever exists.
        val order = listOf("SA", "AE", "US", "GB") + byCountry.keys
        for (country in order) {
            val cert = byCountry[country] ?: continue
            parse(cert)?.let { return it to "$cert ($country)" }
        }
        return null
    }
}
