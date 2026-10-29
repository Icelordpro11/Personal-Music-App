package com.artem.mypersonalmusicapp

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- small shared components ----------------

@Composable fun IconBtn(icon: ImageVector, tint: Color, size: Int = 24, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, null, tint = tint, modifier = Modifier.size(size.dp)) }
}

@Composable fun NavItem(icon: ImageVector, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Icon(icon, null, tint = if (selected) accent else SUB, modifier = Modifier.size(26.dp))
        Text(label, color = if (selected) accent else SUB, fontSize = 11.sp)
    }
}

@Composable fun TopBar(title: String, subtitle: String? = null, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, color = SUB, fontSize = 12.sp)
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        IconBtn(Icons.Rounded.MoreVert, Color.White) { onMore() }
    }
}

@Composable fun SectionTitle(t: String) {
    Text(t, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 8.dp))
}

@Composable fun Chip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(CARD).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

/** Large artwork that tries the high-res YouTube thumbnail and falls back to the standard one. */
@Composable fun BigArt(track: Track, modifier: Modifier) {
    var url by remember(track.id) { mutableStateOf("https://i.ytimg.com/vi/${track.id}/maxresdefault.jpg") }
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        onError = { if (url.contains("maxresdefault")) url = "https://i.ytimg.com/vi/${track.id}/sddefault.jpg" else if (url.contains("sddefault")) url = track.art },
        modifier = modifier
    )
}

@Composable fun BigCard(t: Track, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable { onClick() }) {
        AsyncImage(model = t.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(CARD))
        Text(t.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(t.artist, color = SUB, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun TrackRow(track: Track, isSaved: Boolean, accent: Color, dl: DlApi, onPlay: () -> Unit, onSave: () -> Unit, onArtist: (String) -> Unit) {
    val playing = Audio.current?.id == track.id
    val done = dl.isDone(track.id)
    val busy = dl.isBusy(track.id)
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(if (playing) CARD.copy(alpha = 0.5f) else Color.Transparent)
            .clickable { onPlay() }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = track.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(CARD))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) { Icon(Icons.Rounded.GraphicEq, null, tint = accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(track.title, color = if (playing) accent else Color.White, fontSize = 15.sp, fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(track.artist, color = SUB, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { if (track.artist.isNotEmpty()) onArtist(track.artist) })
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
        else IconBtn(if (done) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (done) accent else SUB, 20) { dl.toggle(track) }
        IconBtn(if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isSaved) accent else SUB, 22) { onSave() }
        Box {
            IconBtn(Icons.Rounded.MoreVert, SUB, 20) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(BG2)) {
                DropdownMenuItem(text = { Text("Add to queue", color = Color.White) }, onClick = { Audio.addToQueue(track); menu = false; Toast.makeText(ctx, "Added to queue", Toast.LENGTH_SHORT).show() })
            }
        }
    }
}

// ---------------- tab screens ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun HomeScreen(
    accent: Color, seed: String, homeTracks: List<Track>, homeLoading: Boolean, dl: DlApi, saved: List<Track>,
    selectedPicks: List<String>, onTogglePick: (String) -> Unit,
    onMore: () -> Unit, onSearch: (String) -> Unit, onPlayHome: (Track) -> Unit, onPlayRecent: (Track) -> Unit, onSave: (Track) -> Unit, onArtist: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField(value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text("What do you want to listen to?", color = Color(0xFF777777)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SUB) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank()) { focus.clearFocus(); onSearch(q.trim()) } }),
                    colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
                    shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f))
                IconBtn(Icons.Rounded.MoreVert, Color.White) { onMore() }
            }
        }
        item {
            SectionTitle("Quick picks")
            Text("Pick a few — your daily mix is built from them.", color = SUB, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GENRES.forEach { g ->
                    val on = selectedPicks.contains(g)
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) accent else CARD).clickable { onTogglePick(g) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(g, color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        item {
            SectionTitle("Made for you today · $seed")
            if (homeLoading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
            else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(homeTracks.take(12)) { t -> BigCard(t) { onPlayHome(t) } } }
        }
        if (Audio.recent.isNotEmpty()) item {
            SectionTitle("Recently played")
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(Audio.recent.toList()) { t -> BigCard(t) { onPlayRecent(t) } } }
        }
        item { SectionTitle("More from today") }
        items(homeTracks.drop(12), key = { "h" + it.id }) { t -> TrackRow(t, saved.any { it.id == t.id }, accent, dl, { onPlayHome(t) }, { onSave(t) }, onArtist) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DiscoverScreen(
    accent: Color, favorites: List<String>, discArtist: String?, discTracks: List<Track>, discLoading: Boolean, saved: List<Track>, dl: DlApi,
    onAddFav: (String) -> Unit, onRemoveFav: (String) -> Unit, onPlay: (Track) -> Unit, onSave: (Track) -> Unit, onArtist: (String) -> Unit, onSearch: (String) -> Unit
) {
    var fav by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Artist", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            SectionTitle("Find an artist")
            TextField(value = fav, onValueChange = { fav = it }, singleLine = true,
                placeholder = { Text("Search for an artist", color = Color(0xFF777777)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SUB) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (fav.isNotBlank()) { focus.clearFocus(); onArtist(fav.trim()) } }),
                colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text("Open an artist, then tap the heart to add them to your favorites below.", color = SUB, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            if (favorites.isNotEmpty()) SectionTitle("Your favorites")
            if (favorites.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(favorites) { a ->
                    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(CARD).clickable { onArtist(a) }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a, color = Color.White, fontSize = 13.sp); IconBtn(Icons.Rounded.Close, SUB, 16) { onRemoveFav(a) }
                    }
                }
            }
        }
        if (favorites.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Add artists above — one is featured here each day.", color = SUB, fontSize = 14.sp, textAlign = TextAlign.Center) } }
        else {
            item {
                SectionTitle("Artist of the day")
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(lerp(accent, Color.Black, 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(12.dp)); Column { Text(discArtist ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Today's picks", color = SUB, fontSize = 12.sp) }
                }
            }
            if (discLoading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) } }
            items(discTracks, key = { "d" + it.id }) { t -> TrackRow(t, saved.any { it.id == t.id }, accent, dl, { onPlay(t) }, { onSave(t) }, onArtist) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchScreen(
    query: String, onQuery: (String) -> Unit, onGo: () -> Unit, results: List<Track>, loading: Boolean, accent: Color, saved: List<Track>, dl: DlApi,
    onPlay: (Track) -> Unit, onSave: (Track) -> Unit, onArtist: (String) -> Unit, onPlayRecent: (Track) -> Unit
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = query, onValueChange = onQuery, placeholder = { Text("Search songs or artists", color = Color(0xFF777777)) }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SUB) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onGo() }),
                colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
            Button(onClick = { focus.clearFocus(); onGo() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Go", color = Color.Black) }
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        LazyColumn(Modifier.weight(1f)) {
            if (results.isEmpty() && !loading) {
                // No search yet (or no results): show what you've recently played.
                if (Audio.recent.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("Search for any song or artist", color = SUB, fontSize = 14.sp) } }
                else {
                    item { SectionTitle("Recently played") }
                    items(Audio.recent.toList(), key = { "sr" + it.id }) { t -> TrackRow(t, saved.any { it.id == t.id }, accent, dl, { focus.clearFocus(); onPlayRecent(t) }, { onSave(t) }, onArtist) }
                }
            } else {
                items(results, key = { it.id }) { t -> TrackRow(t, saved.any { it.id == t.id }, accent, dl, { focus.clearFocus(); onPlay(t) }, { onSave(t) }, onArtist) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryScreen(
    saved: List<Track>, downloads: List<Track>, accent: Color, dl: DlApi, onMore: () -> Unit, onMix: () -> Unit, onDownloadAll: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit, onSave: (Track) -> Unit, onArtist: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun f(list: List<Track>) = if (q.isBlank()) list else list.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
    val sv = f(saved); val dw = f(downloads)
    val notDownloaded = saved.count { s -> downloads.none { it.id == s.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopBar("Your Library", null, onMore) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = q, onValueChange = { q = it }, placeholder = { Text("Search your library", color = Color(0xFF777777)) }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SUB) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                Button(onClick = { focus.clearFocus(); onMix() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Icon(Icons.Rounded.Shuffle, null, tint = Color.Black); Spacer(Modifier.width(4.dp)); Text("Mix", color = Color.Black) }
            }
            if (notDownloaded > 0) Row(Modifier.fillMaxWidth().clickable { onDownloadAll() }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Download, null, tint = accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                Text("Download all liked ($notDownloaded)", color = Color.White, fontSize = 15.sp)
            }
        }
        if (dw.isNotEmpty()) { item { SectionTitle("Downloaded (${dw.size})") }; items(dw, key = { "ld" + it.id }) { t -> TrackRow(t, saved.any { it.id == t.id }, accent, dl, { focus.clearFocus(); onPlay(downloads, t) }, { onSave(t) }, onArtist) } }
        item { SectionTitle("Liked songs (${sv.size})") }
        if (sv.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Tap the heart on a song to save it", color = SUB, fontSize = 14.sp) } }
        items(sv, key = { "ls" + it.id }) { t -> TrackRow(t, true, accent, dl, { focus.clearFocus(); onPlay(saved, t) }, { onSave(t) }, onArtist) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ArtistScreen(
    name: String, tracks: List<Track>, loading: Boolean, accent: Color, saved: List<Track>, dl: DlApi, isFav: Boolean,
    onBack: () -> Unit, onPlay: (Track) -> Unit, onSave: (Track) -> Unit, onPlayAll: () -> Unit, onFav: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val shown = if (q.isBlank()) tracks else tracks.filter { it.title.contains(q, true) }
    Column(Modifier.fillMaxSize().background(BG)) {
        Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(lerp(accent, Color.Black, 0.15f), BG)))) {
            Box(Modifier.statusBarsPadding().padding(4.dp)) { IconBtn(Icons.Rounded.ArrowBack, Color.White, 28) { onBack() } }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} songs", color = SUB, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlayAll, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black); Spacer(Modifier.width(4.dp)); Text("Play", color = Color.Black) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { Audio.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, null, tint = if (Audio.shuffle) accent else Color.White) }
            Spacer(Modifier.weight(1f))
            IconBtn(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isFav) accent else Color.White, 28) { onFav() }
        }
        TextField(value = q, onValueChange = { q = it }, placeholder = { Text("Search in $name", color = Color(0xFF777777)) }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SUB) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        if (loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        LazyColumn(Modifier.weight(1f)) { items(shown, key = { "ar" + it.id }) { t -> TrackRow(t, saved.any { it.id == t.id }, accent, dl, { focus.clearFocus(); onPlay(t) }, { onSave(t) }, {}) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(accent: Color, onAccent: (Color) -> Unit, onClose: () -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Config.baseUrl) }
    var secret by remember { mutableStateOf(Config.secret) }
    var status by remember { mutableStateOf<Boolean?>(null) }
    var testing by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }   // server section is locked until fingerprint

    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconBtn(Icons.Rounded.ArrowBack, Color.White) { onClose() }; Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        // ---- Theme colors (grid) ----
        Spacer(Modifier.height(16.dp))
        Text("Theme color", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SWATCHES.forEach { c ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(c).clickable { onAccent(c) }, contentAlignment = Alignment.Center) {
                    if (c.toArgb() == accent.toArgb()) Icon(Icons.Rounded.Check, null, tint = Color.Black)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color(0xFFE22134), Color(0xFFF59B23), Color(0xFFFFC107), Color(0xFF1DB954), Color(0xFF00BCD4), Color(0xFF2E77D0), Color(0xFF9B59B6), Color(0xFFEC4899), Color(0xFFE22134))))
                .clickable { showPicker = true }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Palette, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap the color wheel for a custom color.", color = SUB, fontSize = 12.sp)

        // ---- Security / Server (under colors, fingerprint-locked) ----
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = SUB, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text("Security", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))

        if (!unlocked) {
            Text("Server: ${Config.baseUrl}", color = SUB, fontSize = 13.sp)
            Text(if (Config.secret.isEmpty()) "No code set" else "Code: ••••••••", color = SUB, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(10.dp))
            Button(onClick = { biometricAuth(ctx) { unlocked = true } }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(Icons.Rounded.Fingerprint, null, tint = Color.Black); Spacer(Modifier.width(6.dp)); Text("Unlock to edit", color = Color.Black)
            }
        } else {
            TextField(value = url, onValueChange = { url = it; status = null }, singleLine = true,
                label = { Text("Server address", color = SUB) }, placeholder = { Text("http://192.168.1.20:5080", color = Color(0xFF777777)) },
                colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = secret, onValueChange = { secret = it; status = null }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Secret code", color = SUB) }, placeholder = { Text("Secret code (optional)", color = Color(0xFF777777)) },
                colors = TextFieldDefaults.colors(focusedContainerColor = CARD, unfocusedContainerColor = CARD, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    Config.save(ctx, url, secret); Audio.onConfigChanged()
                    testing = true; status = null
                    scope.launch { val ok = withContext(Dispatchers.IO) { ping(url, secret) }; testing = false; status = ok }
                }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Save & Connect", color = Color.Black) }
                when {
                    testing -> { CircularProgressIndicator(Modifier.size(16.dp), color = accent, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("Checking…", color = SUB, fontSize = 13.sp) }
                    status == true -> { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF1DB954))); Spacer(Modifier.width(6.dp)); Text("Connected", color = Color(0xFF1DB954), fontSize = 13.sp) }
                    status == false -> { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE22134))); Spacer(Modifier.width(6.dp)); Text("Not connected", color = Color(0xFFE22134), fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Source: YouTube · streamed through your server", color = SUB, fontSize = 12.sp)
    }
    if (showPicker) ColorPickerDialog(accent, { showPicker = false }, { onAccent(it) })
}

/** Prompts fingerprint/PIN; calls onSuccess (or immediately if the device has no biometrics set up). */
fun biometricAuth(ctx: Context, onSuccess: () -> Unit) {
    val activity = ctx as? FragmentActivity ?: run { onSuccess(); return }
    val authTypes = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(ctx).canAuthenticate(authTypes) != BiometricManager.BIOMETRIC_SUCCESS) { onSuccess(); return }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock server settings")
        .setSubtitle("Confirm it's you to change the server or code")
        .setAllowedAuthenticators(authTypes)
        .build()
    prompt.authenticate(info)
}

@Composable fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsvInit = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var h by remember { mutableStateOf(hsvInit[0]) }
    var s by remember { mutableStateOf(hsvInit[1]) }
    var v by remember { mutableStateOf(hsvInit[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = BG2) {
            Column(Modifier.padding(20.dp)) {
                Text("Custom color", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(color)); Spacer(Modifier.width(12.dp))
                    Text("#%06X".format(0xFFFFFF and color.toArgb()), color = SUB, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SWATCHES) { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(c).clickable { val o = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), o); h = o[0]; s = o[1]; v = o[2] }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Hue", color = SUB, fontSize = 12.sp); Slider(value = h, onValueChange = { h = it }, valueRange = 0f..360f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Text("Saturation", color = SUB, fontSize = 12.sp); Slider(value = s, onValueChange = { s = it }, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Text("Brightness", color = SUB, fontSize = 12.sp); Slider(value = v, onValueChange = { v = it }, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = SUB) }; Spacer(Modifier.width(8.dp))
                    Button(onClick = { onPick(color); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text("Apply", color = Color.Black) }
                }
            }
        }
    }
}

@Composable fun QueueScreen(accent: Color, saved: List<Track>, dl: DlApi, onClose: () -> Unit, onPlay: (Track) -> Unit, onSave: (Track) -> Unit, onRemove: (Track) -> Unit) {
    val cur = Audio.current
    val idx = Audio.queue.indexOfFirst { it.id == cur?.id }
    val upNext = if (idx >= 0) Audio.queue.drop(idx + 1) else emptyList()
    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.KeyboardArrowDown, Color.White, 28) { onClose() }
            Text("Queue", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (cur != null) { SectionTitle("Now playing"); TrackRow(cur, saved.any { it.id == cur.id }, accent, dl, { onPlay(cur) }, { onSave(cur) }, {}) }
        SectionTitle("Up next (${upNext.size})")
        if (upNext.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Long-press any song to add it here", color = SUB, fontSize = 14.sp, textAlign = TextAlign.Center) }
        LazyColumn(Modifier.weight(1f)) {
            items(upNext, key = { "q" + it.id }) { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TrackRow(t, saved.any { it.id == t.id }, accent, dl, { onPlay(t) }, { onSave(t) }, {}) }
                    IconBtn(Icons.Rounded.Close, SUB, 20) { onRemove(t) }
                }
            }
        }
    }
}

@Composable fun FullPlayer(accent: Color, isSaved: Boolean, dl: DlApi, onClose: () -> Unit, onSave: () -> Unit, onArtist: (String) -> Unit, onQueue: () -> Unit) {
    val t = Audio.current ?: return
    val ctx = LocalContext.current
    var pos by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Audio.current, Audio.isPlaying) { while (true) { if (!dragging) pos = Audio.positionMs(); delay(500) } }
    val done = dl.isDone(t.id); val busy = dl.isBusy(t.id)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(accent, Color.Black, 0.25f), BG, BG))).statusBarsPadding().padding(20.dp)
        .pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(onDragEnd = { if (dy > 160f) onClose(); dy = 0f }, onVerticalDrag = { _, amount -> dy += amount })
        }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.KeyboardArrowDown, Color.White, 30) { onClose() }
            Spacer(Modifier.weight(1f)); Text(t.source, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.QueueMusic, Color.White) { onQueue() }
            Box {
                IconBtn(Icons.Rounded.MoreVert, if (Audio.sleepMinutes > 0) accent else Color.White) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(BG2)) {
                    DropdownMenuItem(text = { Text("Add to queue", color = Color.White) }, onClick = { Audio.addToQueue(t); menu = false; Toast.makeText(ctx, "Added to queue", Toast.LENGTH_SHORT).show() })
                    DropdownMenuItem(text = { Text("Clear up next", color = Color.White) }, onClick = { Audio.clearUpNext(); menu = false })
                    HorizontalDivider(color = CARD)
                    Text("Sleep timer", color = SUB, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour").forEach { (m, label) ->
                        DropdownMenuItem(text = { Text(label, color = if (Audio.sleepMinutes == m) accent else Color.White) }, onClick = { Audio.setSleep(m); menu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BigArt(t, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(CARD)
            .pointerInput(t.id) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { if (dx < -80f) Audio.next() else if (dx > 80f) Audio.prev(); dx = 0f },
                    onHorizontalDrag = { _, amount -> dx += amount }
                )
            })
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = accent, fontSize = 15.sp, modifier = Modifier.clickable { onArtist(t.artist) }.padding(top = 4.dp))
            }
            if (busy) CircularProgressIndicator(Modifier.size(26.dp), color = accent, strokeWidth = 2.dp)
            else IconBtn(if (done) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (done) accent else Color.White, 26) { dl.toggle(t) }
            IconBtn(if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isSaved) accent else Color.White, 26) { onSave() }
        }
        Spacer(Modifier.height(12.dp))
        val dur = Audio.durationMs.coerceAtLeast(1)
        Slider(value = pos.coerceIn(0, dur).toFloat(), onValueChange = { dragging = true; pos = it.toInt() }, onValueChangeFinished = { Audio.seekTo(pos); dragging = false }, valueRange = 0f..dur.toFloat(), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = CARD))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(fmt(pos), color = SUB, fontSize = 12.sp); Text(fmt(Audio.durationMs), color = SUB, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconBtn(Icons.Rounded.Shuffle, if (Audio.shuffle) accent else SUB, 24) { Audio.toggleShuffle() }
            IconBtn(Icons.Rounded.SkipPrevious, Color.White, 40) { Audio.prev() }
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { Audio.toggle() }, contentAlignment = Alignment.Center) {
                if (Audio.buffering) CircularProgressIndicator(Modifier.size(30.dp), color = Color.Black, strokeWidth = 3.dp)
                else Icon(if (Audio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(38.dp))
            }
            IconBtn(Icons.Rounded.SkipNext, Color.White, 40) { Audio.next() }
            IconBtn(if (Audio.repeat) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, if (Audio.repeat) accent else SUB, 24) { Audio.toggleRepeat() }
        }
        if (Audio.sleepMinutes > 0) { Spacer(Modifier.height(8.dp)); Text("Sleep in ${Audio.sleepMinutes} min", color = accent, fontSize = 12.sp) }
    }
}
