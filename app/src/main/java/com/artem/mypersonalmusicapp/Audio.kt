package com.artem.mypersonalmusicapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Singleton playback engine backed by ExoPlayer.
 * UI reads the observable state (current, isPlaying, ...) and calls the control methods.
 */
@OptIn(UnstableApi::class)
object Audio {
    private lateinit var app: Context
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val exec = Executors.newCachedThreadPool()
    private var sleepRunnable: Runnable? = null
    private var token = 0

    var queue by mutableStateOf<List<Track>>(emptyList())
    var current by mutableStateOf<Track?>(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var durationMs by mutableStateOf(0)
    var sleepMinutes by mutableStateOf(0)
    val recent = mutableStateListOf<Track>()
    var onChange: (() -> Unit)? = null

    fun init(c: Context) { app = c.applicationContext }

    private fun ensure(): ExoPlayer {
        if (player == null) {
            // Longer timeouts so the first play of a song (while the server downloads
            // and caches it) doesn't time out; cross-protocol redirects allowed too.
            val headers = if (Config.secret.isNotEmpty()) mapOf("X-Api-Key" to Config.secret) else emptyMap()
            val httpFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
            player = ExoPlayer.Builder(app)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                androidx.media3.common.Player.STATE_READY -> { Audio.durationMs = duration.coerceAtLeast(0L).toInt(); Audio.buffering = false; fire() }
                                androidx.media3.common.Player.STATE_BUFFERING -> { Audio.buffering = true; fire() }
                                androidx.media3.common.Player.STATE_ENDED -> { if (!Audio.repeat) Audio.next() }
                                else -> {}
                            }
                        }
                        override fun onIsPlayingChanged(playing: Boolean) { Audio.isPlaying = playing; fire() }
                    })
                }
        }
        return player!!
    }

    fun playQueue(list: List<Track>, i: Int) { queue = list; if (i in list.indices) start(list[i]) }

    fun addToQueue(t: Track) {
        if (current == null) playQueue(listOf(t), 0)
        else { queue = queue + t; fire() }
    }

    fun removeFromQueue(t: Track) {
        if (t.id == current?.id) return   // don't remove the song that's playing
        queue = queue.filterNot { it.id == t.id }
        fire()
    }

    /** Clear everything after the current song (keeps what's playing). */
    fun clearUpNext() {
        val i = queue.indexOfFirst { it.id == current?.id }
        if (i >= 0) queue = queue.take(i + 1)
        fire()
    }

    private fun start(t: Track) {
        val my = ++token
        current = t; durationMs = 0; buffering = true; isPlaying = false
        recent.removeAll { it.id == t.id }; recent.add(0, t); if (recent.size > 30) recent.removeAt(recent.size - 1)
        try { ContextCompat.startForegroundService(app, Intent(app, MusicService::class.java)) } catch (_: Exception) {}
        fire()
        exec.execute {
            val url = resolveSource(t.stream)
            handler.post {
                if (my != token) return@post
                if (url.isNullOrEmpty()) { buffering = false; fire(); Toast.makeText(app, "Couldn't load track", Toast.LENGTH_SHORT).show(); return@post }
                val p = ensure()
                p.setMediaItem(MediaItem.fromUri(url))
                p.repeatMode = if (repeat) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                p.prepare(); p.playWhenReady = true
            }
        }
        val idx = queue.indexOfFirst { it.id == t.id }
        if (idx in 0 until queue.size - 1) prewarm(queue[idx + 1].stream)
    }

    /** Warm the server's cache for the next track so skipping forward is instant. */
    private fun prewarm(stream: String) {
        if (!stream.contains("/api/yt/")) return
        exec.execute {
            try { apiGet(stream).apply { setRequestProperty("Range", "bytes=0-1") }.inputStream.use { it.readBytes() } } catch (_: Exception) {}
        }
    }

    /** Resolve a track's play URL: local file, direct URL, or our server's /api/yt/resolve endpoint. */
    private fun resolveSource(stream: String): String? = try {
        when {
            stream.startsWith("/") || stream.startsWith("file:") -> stream
            stream.contains("/api/yt/resolve") -> {
                JSONObject(apiGet(stream).inputStream.bufferedReader().use { it.readText() }).optString("url").ifEmpty { null }
            }
            else -> stream
        }
    } catch (e: Exception) { null }

    /** Drop the player so it's rebuilt with the latest server settings (after changing them). */
    fun onConfigChanged() { try { player?.release() } catch (_: Exception) {}; player = null; current = null; isPlaying = false; fire() }

    fun toggle() { val p = player ?: return; if (p.isPlaying) p.pause() else p.play() }

    fun next() {
        if (queue.isEmpty()) return
        val i = queue.indexOfFirst { it.id == current?.id }
        if (shuffle && queue.size > 1) { var r = i; while (r == i) r = (Math.random() * queue.size).toInt(); start(queue[r]) }
        else if (i in 0 until queue.size - 1) start(queue[i + 1]) else player?.pause()
    }

    fun prev() {
        if (queue.isEmpty()) return
        val i = queue.indexOfFirst { it.id == current?.id }
        if ((player?.currentPosition ?: 0) > 3000) player?.seekTo(0)
        else if (i > 0) start(queue[i - 1]) else player?.seekTo(0)
    }

    fun seekTo(ms: Int) { try { player?.seekTo(ms.toLong()) } catch (_: Exception) {} }
    fun positionMs(): Int = try { (player?.currentPosition ?: 0).toInt() } catch (_: Exception) { 0 }
    fun toggleRepeat() { repeat = !repeat; player?.repeatMode = if (repeat) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF; fire() }
    fun toggleShuffle() { shuffle = !shuffle }

    fun setSleep(min: Int) {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepMinutes = min
        if (min > 0) {
            val r = Runnable { if (isPlaying) toggle(); sleepMinutes = 0 }
            sleepRunnable = r
            handler.postDelayed(r, min * 60_000L)
        }
    }

    fun fire() { onChange?.invoke() }
}

/** Foreground service that shows the media notification and lock-screen controls. */
class MusicService : Service() {
    private lateinit var session: MediaSessionCompat
    private val channelId = "pm_music"
    private val io = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "pm").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Audio.toggle()
                override fun onPause() = Audio.toggle()
                override fun onSkipToNext() = Audio.next()
                override fun onSkipToPrevious() = Audio.prev()
                override fun onSeekTo(pos: Long) = Audio.seekTo(pos.toInt())
                override fun onStop() { stopSelf() }
            })
            isActive = true
        }
        Audio.onChange = { update() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { "TOGGLE" -> Audio.toggle(); "NEXT" -> Audio.next(); "PREV" -> Audio.prev() }
        val t = Audio.current
        if (t == null) {
            safeForeground(placeholder()); try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}; stopSelf(); return START_NOT_STICKY
        }
        update()
        return START_STICKY
    }

    private fun placeholder(): android.app.Notification =
        NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Personal Music").build()

    /** startForeground can be blocked when started from the background on Android 12+; never let that crash the app. */
    private fun safeForeground(n: android.app.Notification) { try { startForeground(1, n) } catch (_: Exception) {} }

    private fun update() {
        val t = Audio.current ?: return
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, t.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Audio.durationMs.toLong())
                .build()
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(if (Audio.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, Audio.positionMs().toLong(), 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
                ).build()
        )
        safeForeground(buildNotification(t, Audio.isPlaying, null))
        loadArt(t.art) { bmp -> if (Audio.current?.id == t.id) safeForeground(buildNotification(t, Audio.isPlaying, bmp)) }
    }

    private fun buildNotification(t: Track, playing: Boolean, art: Bitmap?): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        fun act(a: String, icon: Int, l: String) = NotificationCompat.Action(icon, l, PendingIntent.getService(this, a.hashCode(), Intent(this, MusicService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE))
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(t.title).setContentText(t.artist)
            .setLargeIcon(art).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(act("PREV", android.R.drawable.ic_media_previous, "Prev"))
            .addAction(act("TOGGLE", if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play"))
            .addAction(act("NEXT", android.R.drawable.ic_media_next, "Next"))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun loadArt(url: String, cb: (Bitmap?) -> Unit) {
        if (url.isEmpty()) { cb(null); return }
        io.execute {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 10000
                val b = c.inputStream.readBytes()
                val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                Handler(Looper.getMainLooper()).post { cb(bmp) }
            } catch (_: Exception) {}
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Playback", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() { super.onDestroy(); Audio.onChange = null; session.release() }
}
