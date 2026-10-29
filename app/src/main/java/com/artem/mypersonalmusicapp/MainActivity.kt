package com.artem.mypersonalmusicapp

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class MainActivity : FragmentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Audio.init(applicationContext)
        Config.load(applicationContext)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { App() }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var tab by remember { mutableStateOf(0) }            // 0 Home, 1 Artist, 2 Search, 3 Library
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Track>()) }
    var loading by remember { mutableStateOf(false) }
    val saved = remember { mutableStateListOf<Track>() }
    val downloads = remember { mutableStateListOf<Track>() }
    val downloading = remember { mutableStateListOf<String>() }
    val favorites = remember { mutableStateListOf<String>() }
    val selectedPicks = remember { mutableStateListOf<String>() }
    var pendingUnsave by remember { mutableStateOf<Track?>(null) }
    var accent by remember { mutableStateOf(Color(0xFF1DB954)) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var artistView by remember { mutableStateOf<String?>(null) }
    var artistTracks by remember { mutableStateOf(listOf<Track>()) }
    var artistLoading by remember { mutableStateOf(false) }
    var homeTracks by remember { mutableStateOf(listOf<Track>()) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeLoadedSeed by remember { mutableStateOf<String?>(null) }
    var discArtist by remember { mutableStateOf<String?>(null) }
    var discTracks by remember { mutableStateOf(listOf<Track>()) }
    var discLoading by remember { mutableStateOf(false) }

    fun prefs() = context.getSharedPreferences("pm", Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        val p = prefs()
        accent = Color(p.getInt("accent", 0xFF1DB954.toInt()))
        saved.clear(); saved.addAll(loadTracks(p.getString("saved", "[]") ?: "[]"))
        downloads.clear(); downloads.addAll(loadTracks(p.getString("downloads", "[]") ?: "[]"))
        favorites.clear(); favorites.addAll(loadStrings(p.getString("favs", "[]") ?: "[]"))
        selectedPicks.clear(); selectedPicks.addAll(loadStrings(p.getString("picks", "[]") ?: "[]"))
    }
    fun persistSaved() { prefs().edit().putString("saved", tracksJson(saved)).apply() }
    fun persistDownloads() { prefs().edit().putString("downloads", tracksJson(downloads)).apply() }
    fun persistFavs() { val a = JSONArray(); favorites.forEach { a.put(it) }; prefs().edit().putString("favs", a.toString()).apply() }
    fun persistPicks() { val a = JSONArray(); selectedPicks.forEach { a.put(it) }; prefs().edit().putString("picks", a.toString()).apply() }

    fun runSearch(term: String) {
        val q = term.trim(); if (q.isEmpty()) return
        query = q; tab = 2; loading = true; focus.clearFocus()
        scope.launch {
            val r = withContext(Dispatchers.IO) { searchServer(q) }
            loading = false
            if (r == null) Toast.makeText(context, "Search failed — server running?", Toast.LENGTH_SHORT).show() else results = r
        }
    }
    fun openArtist(name: String) {
        if (name.isBlank()) return
        artistView = name; artistLoading = true; focus.clearFocus()
        scope.launch { val r = withContext(Dispatchers.IO) { searchServer(name) }; artistLoading = false; artistTracks = r ?: emptyList() }
    }
    fun forPlay(t: Track): Track = downloads.firstOrNull { it.id == t.id } ?: t
    fun playFrom(src: List<Track>, t: Track) { focus.clearFocus(); val mapped = src.map { forPlay(it) }; val i = mapped.indexOfFirst { it.id == t.id }; Audio.playQueue(mapped, if (i >= 0) i else 0) }
    fun toggleSave(t: Track) {
        val i = saved.indexOfFirst { it.id == t.id }
        if (i >= 0) pendingUnsave = t            // ask before removing from liked
        else { saved.add(t); persistSaved() }
    }
    fun setAccent(c: Color) { accent = c; prefs().edit().putInt("accent", c.toArgb()).apply() }
    fun mixSaved() {
        val pool = (downloads + saved).distinctBy { it.id }
        if (pool.isEmpty()) { Toast.makeText(context, "Save some songs first", Toast.LENGTH_SHORT).show(); return }
        Audio.shuffle = true
        val mapped = pool.map { forPlay(it) }
        Audio.playQueue(mapped, (Math.random() * mapped.size).toInt())
    }
    fun toggleDownload(t: Track) {
        val existing = downloads.firstOrNull { it.id == t.id }
        if (existing != null) {
            try { File(existing.stream).delete() } catch (_: Exception) {}
            downloads.remove(existing); persistDownloads(); Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show(); return
        }
        if (downloading.contains(t.id)) return
        downloading.add(t.id)
        scope.launch(Dispatchers.IO) {
            try {
                val meta = apiGet("${Config.baseUrl}/api/yt/resolve?id=${t.id}")
                val direct = JSONObject(meta.inputStream.bufferedReader().use { it.readText() }).optString("url")
                if (direct.isNullOrEmpty()) throw Exception("no url")
                val dir = File(context.getExternalFilesDir(null), "music").apply { mkdirs() }
                val file = File(dir, "${t.id}.m4a")
                (URL(direct).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 40000 }.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } }
                withContext(Dispatchers.Main) { downloads.add(t.copy(stream = file.absolutePath)); persistDownloads(); downloading.remove(t.id); Toast.makeText(context, "Downloaded", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { downloading.remove(t.id); Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    fun downloadAll() {
        saved.forEach { t -> if (downloads.none { it.id == t.id } && !downloading.contains(t.id)) toggleDownload(t) }
        Toast.makeText(context, "Downloading liked songs…", Toast.LENGTH_SHORT).show()
    }
    val dl = DlApi({ id -> downloads.any { it.id == id } }, { id -> downloading.contains(id) }, { toggleDownload(it) })

    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val homeSeed = run { val pool = favorites + (if (selectedPicks.isEmpty()) GENRES else selectedPicks.toList()); pool[day % pool.size] }
    LaunchedEffect(homeSeed) {
        if (homeSeed != homeLoadedSeed) {
            homeLoading = true
            val r = withContext(Dispatchers.IO) { searchServer(homeSeed) }
            homeLoading = false; homeTracks = r ?: emptyList(); homeLoadedSeed = homeSeed
        }
    }
    LaunchedEffect(tab, favorites.size) {
        if (tab == 1 && favorites.isNotEmpty()) {
            val a = favorites[day % favorites.size]
            if (a != discArtist) { discArtist = a; discLoading = true; val r = withContext(Dispatchers.IO) { searchServer(a) }; discLoading = false; discTracks = r ?: emptyList() }
        }
    }

    BackHandler(enabled = showQueue || showPlayer || showSettings || artistView != null || tab != 0) {
        when {
            showQueue -> showQueue = false
            showPlayer -> showPlayer = false
            showSettings -> showSettings = false
            artistView != null -> artistView = null
            tab != 0 -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = tab, animationSpec = tween(250), label = "tab") { t ->
                    when (t) {
                        0 -> HomeScreen(accent, homeSeed, homeTracks, homeLoading, dl, saved, selectedPicks,
                            onTogglePick = { if (selectedPicks.contains(it)) selectedPicks.remove(it) else selectedPicks.add(it); persistPicks() },
                            onMore = { showSettings = true }, onSearch = { runSearch(it) }, onPlayHome = { playFrom(homeTracks, it) }, onPlayRecent = { playFrom(Audio.recent.toList(), it) }, onSave = { toggleSave(it) }, onArtist = { openArtist(it) })
                        1 -> DiscoverScreen(accent, favorites, discArtist, discTracks, discLoading, saved, dl,
                            onAddFav = { if (it.isNotBlank() && !favorites.contains(it.trim())) { favorites.add(it.trim()); persistFavs() } },
                            onRemoveFav = { favorites.remove(it); persistFavs() },
                            onPlay = { playFrom(discTracks, it) }, onSave = { toggleSave(it) }, onArtist = { openArtist(it) }, onSearch = { runSearch(it) })
                        2 -> SearchScreen(query, { query = it }, { runSearch(query) }, results, loading, accent, saved, dl, { playFrom(results, it) }, { toggleSave(it) }, { openArtist(it) }, { playFrom(Audio.recent.toList(), it) })
                        3 -> LibraryScreen(saved, downloads, accent, dl, { showSettings = true }, { mixSaved() }, { downloadAll() }, { list, t2 -> playFrom(list, t2) }, { toggleSave(it) }, { openArtist(it) })
                    }
                }
            }

            AnimatedVisibility(visible = Audio.current != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Audio.current?.let { t ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(BG2).clickable { showPlayer = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = t.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(CARD))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(t.artist, color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (Audio.buffering) CircularProgressIndicator(Modifier.size(26.dp).padding(end = 6.dp), color = accent, strokeWidth = 2.dp)
                        else IconBtn(if (Audio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Color.White, 28) { Audio.toggle() }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().background(Color(0xFF121212)).navigationBarsPadding().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                NavItem(Icons.Rounded.Home, "Home", tab == 0, accent) { tab = 0 }
                NavItem(Icons.Rounded.Search, "Search", tab == 2, accent) { tab = 2 }
                NavItem(Icons.Rounded.Person, "Artist", tab == 1, accent) { tab = 1 }
                NavItem(Icons.Rounded.LibraryMusic, "Library", tab == 3, accent) { tab = 3 }
            }
        }

        if (artistView != null)
            ArtistScreen(artistView!!, artistTracks, artistLoading, accent, saved, dl, favorites.contains(artistView),
                onBack = { artistView = null }, onPlay = { playFrom(artistTracks, it) }, onSave = { toggleSave(it) },
                onPlayAll = { if (artistTracks.isNotEmpty()) playFrom(artistTracks, artistTracks.first()) },
                onFav = { val a = artistView!!; if (favorites.contains(a)) favorites.remove(a) else favorites.add(a); persistFavs() })

        AnimatedVisibility(visible = showSettings, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            SettingsScreen(accent, { setAccent(it) }, { showSettings = false })
        }

        AnimatedVisibility(visible = showPlayer && Audio.current != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            FullPlayer(accent, saved.any { it.id == Audio.current?.id }, dl, { showPlayer = false }, { Audio.current?.let { toggleSave(it) } }, { openArtist(it); showPlayer = false }, { showQueue = true })
        }

        AnimatedVisibility(visible = showQueue, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            QueueScreen(accent, saved, dl, { showQueue = false },
                { t -> val i = Audio.queue.indexOfFirst { it.id == t.id }; if (i >= 0) Audio.playQueue(Audio.queue, i) },
                { toggleSave(it) }, { Audio.removeFromQueue(it) })
        }

        pendingUnsave?.let { t ->
            AlertDialog(
                onDismissRequest = { pendingUnsave = null },
                containerColor = BG2,
                title = { Text("Remove from Liked?", color = Color.White) },
                text = { Text("Remove \"${t.title}\" from your liked songs?", color = SUB) },
                confirmButton = { TextButton(onClick = { val i = saved.indexOfFirst { it.id == t.id }; if (i >= 0) { saved.removeAt(i); persistSaved() }; pendingUnsave = null }) { Text("Remove", color = Color(0xFFE22134)) } },
                dismissButton = { TextButton(onClick = { pendingUnsave = null }) { Text("Cancel", color = SUB) } }
            )
        }
    }
}
