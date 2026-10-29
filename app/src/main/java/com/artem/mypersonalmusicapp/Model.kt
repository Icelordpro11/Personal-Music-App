package com.artem.mypersonalmusicapp

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

// Runtime server settings — editable in the app's Settings, saved in prefs.
object Config {
    var baseUrl: String = "http://192.168.178.77:5080"
    var secret: String = ""
    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("pm", Context.MODE_PRIVATE)
        baseUrl = p.getString("baseUrl", baseUrl) ?: baseUrl
        secret = p.getString("secret", "") ?: ""
    }
    fun save(ctx: Context, url: String, sec: String) {
        baseUrl = url.trim().trimEnd('/')
        secret = sec.trim()
        ctx.getSharedPreferences("pm", Context.MODE_PRIVATE).edit()
            .putString("baseUrl", baseUrl).putString("secret", secret).apply()
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val art: String,
    val stream: String,
    val source: String
)

class DlApi(
    val isDone: (String) -> Boolean,
    val isBusy: (String) -> Boolean,
    val toggle: (Track) -> Unit
)

// ---- Theme tokens ----
val SWATCHES = listOf(
    Color(0xFF1DB954), Color(0xFF2E77D0), Color(0xFFE22134), Color(0xFFF59B23),
    Color(0xFF9B59B6), Color(0xFFEC4899), Color(0xFF00BCD4), Color(0xFFFFC107),
    Color(0xFFFFFFFF), Color(0xFF9E9E9E), Color(0xFF8BC34A), Color(0xFFFF5722),
    Color(0xFF3F51B5), Color(0xFF009688)
)
val BG = Color(0xFF0E0E0E)
val BG2 = Color(0xFF181818)
val CARD = Color(0xFF232323)
val SUB = Color(0xFFB3B3B3)
val GENRES = listOf("Lofi", "Phonk", "Pop Hits", "Rock", "Jazz", "Workout", "Chill", "Rap", "8D Audio")

fun fmt(ms: Int): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

// ---- networking ----

/** Opens a connection to our server with the secret header attached (if set). */
fun apiGet(urlStr: String): HttpURLConnection {
    val c = URL(urlStr).openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 25000
    c.setRequestProperty("User-Agent", "PersonalMusic/1.0")
    if (Config.secret.isNotEmpty()) c.setRequestProperty("X-Api-Key", Config.secret)
    return c
}

/** Tests whether the given server + secret are reachable and authorized. */
fun ping(baseUrl: String, secret: String): Boolean {
    return try {
        val c = URL(baseUrl.trim().trimEnd('/') + "/api/ping").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.requestMethod = "GET"
        if (secret.trim().isNotEmpty()) c.setRequestProperty("X-Api-Key", secret.trim())
        c.responseCode == 200
    } catch (e: Exception) { false }
}

fun searchServer(q: String): List<Track>? {
    return try {
        val url = Config.baseUrl + "/api/search?query=" + URLEncoder.encode(q, "UTF-8")
        val c = apiGet(url)
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val data = JSONObject(text).getJSONArray("tracks")
        val out = ArrayList<Track>()
        for (i in 0 until data.length()) {
            val t = data.getJSONObject(i)
            val id = t.optString("id")
            val title = t.optString("title")
            val stream = t.optString("streamUrl")
            if (id.isEmpty() || title.isEmpty() || stream.isEmpty()) continue
            out.add(Track(id, title, t.optString("artist"), t.optString("artwork"), stream, t.optString("source")))
        }
        out
    } catch (e: Exception) {
        null
    }
}

fun tracksJson(list: List<Track>): String {
    val a = JSONArray()
    list.forEach { t ->
        a.put(JSONObject().apply {
            put("id", t.id); put("title", t.title); put("artist", t.artist)
            put("art", t.art); put("stream", t.stream); put("source", t.source)
        })
    }
    return a.toString()
}

fun loadTracks(json: String): List<Track> {
    val l = ArrayList<Track>()
    try {
        val a = JSONArray(json)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            l.add(Track(o.getString("id"), o.getString("title"), o.getString("artist"), o.getString("art"), o.getString("stream"), o.optString("source")))
        }
    } catch (_: Exception) {}
    return l
}

fun loadStrings(json: String): List<String> {
    val l = ArrayList<String>()
    try {
        val a = JSONArray(json)
        for (i in 0 until a.length()) l.add(a.getString(i))
    } catch (_: Exception) {}
    return l
}
