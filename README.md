<div align="center">

# 🎵 Personal Music App

**My Music. My Server. My Rules.**

A personal music application built with Android Studio and .NET 8.

<img src="Docs/Screenshot 2026-10-29 050943.png" alt="Personal Music App" width="280"/>

**Kotlin · C# · ASP.NET Core · SQLite**

</div>

---

## Why I Built It

I got tired of Spotify's advertisements and limitations, so I decided to build my own music application that gives me more control over how I discover and listen to music.

I wanted an Android app where I could search for music through YouTube, stream tracks, save favorites, download supported music for offline listening, and customize the interface to my preferences.

I also wanted to understand how a complete application works behind the scenes, so I developed my own C# .NET 8 backend and connected it to the Android app using HTTP requests.

To keep costs low and maintain control over my infrastructure, I host the backend in a Proxmox LXC container running Debian 12, dedicated to my .NET API and SQLite database.

This project combines Android development, backend engineering, audio streaming, and self-hosting into one personal project that I can continuously improve and customize.

## Features

| Feature | Description |
|---|---|
| Music Discovery | Search for songs and artists. |
| Audio Streaming | Listen to supported tracks through the application. |
| Personal Library | Save and manage favorite songs. |
| Offline Listening | Download supported tracks where legally permitted. |
| Custom Themes | Customize the application's accent colors. |
| Audio Caching | Reduce repeated processing on the backend. |
| Real-Time Communication | SignalR support for communication between connected clients. |
| Self-Hosting | Run the backend on my own home server. |

---

## Architecture

The project consists of two main components: an Android application and a .NET backend API.

```text
┌──────────────────────────────────┐
│        Android Application       │
│                                  │
│  Kotlin + Jetpack Compose        │
│                                  │
│  Home · Search · Discover        │
│  Library · Player · Settings     │
└────────────────┬─────────────────┘
                 │
                 │ HTTP / API Requests
                 ▼
┌──────────────────────────────────┐
│          .NET 8 Backend          │
│          ASP.NET Core            │
│                                  │
│  Search · Audio · Playback       │
│  Liked Songs · API Middleware    │
└────────┬─────────────┬───────────┘
         │             │
         ▼             ▼
┌────────────────┐  ┌──────────────┐
│    Services    │  │    SQLite    │
│                │  │              │
│ YouTube Search │  │ Liked Songs  │
│ Audio Caching  │  │ Playback Data│
└────────────────┘  └──────────────┘
         │
         ▼
┌──────────────────────────────────┐
│          Proxmox Server          │
│                                  │
│  Debian 12 · LXC Container       │
│  .NET API · SQLite               │
└──────────────────────────────────┘
```

## Android Application

Built with Kotlin, Android Studio, and Jetpack Compose, the Android application provides the interface for discovering music and managing a personal library.

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Main activity and application entry point. |
| `Screens.kt` | Home, search, discover, library, artist, and settings screens. |
| `Audio.kt` | Audio and playback functionality. |
| `Model.kt` | Track models, server configuration, networking helpers, and shared utilities. |
| `ui/theme/` | Application theme resources. |

### Connecting to My Server

The `Config` object in `Model.kt` manages the backend URL and API key configuration.

```kotlin
object Config {
    var baseUrl: String = "http://192.168.178.77:5080"
    var secret: String = ""

    fun save(ctx: Context, url: String, sec: String) {
        baseUrl = url.trim().trimEnd('/')
        secret = sec.trim()

        ctx.getSharedPreferences("pm", Context.MODE_PRIVATE)
            .edit()
            .putString("baseUrl", baseUrl)
            .putString("secret", secret)
            .apply()
    }
}
```

This allows the application to save its server settings locally, making it possible to configure the backend without changing the source code.

### Searching for Music

The Android client sends HTTP requests to the backend and converts the JSON response into Kotlin `Track` objects.

```kotlin
fun searchServer(q: String): List<Track>? {
    return try {
        val url = Config.baseUrl +
            "/api/search?query=" +
            URLEncoder.encode(q, "UTF-8")

        val connection = apiGet(url)
        val response = connection.inputStream
            .bufferedReader()
            .use { it.readText() }

        val tracks = JSONObject(response)
            .getJSONArray("tracks")

        val results = ArrayList<Track>()

        for (i in 0 until tracks.length()) {
            val track = tracks.getJSONObject(i)

            val id = track.optString("id")
            val title = track.optString("title")
            val stream = track.optString("streamUrl")

            if (id.isEmpty() || title.isEmpty() || stream.isEmpty()) {
                continue
            }

            results.add(
                Track(
                    id,
                    title,
                    track.optString("artist"),
                    track.optString("artwork"),
                    stream,
                    track.optString("source")
                )
            )
        }

        results
    } catch (e: Exception) {
        null
    }
}
```

This example demonstrates how the Android application requests search results, processes the JSON response, and creates track models for the interface.

---

## .NET Backend

The backend uses C# and ASP.NET Core, with separate files for HTTP endpoints, middleware, services, and database operations.

### HTTP Endpoints

| File | Responsibility |
|---|---|
| `HealthEndpoints.cs` | Server health checks. |
| `SearchEndpoints.cs` | Music search requests. |
| `AudioEndpoints.cs` | Audio-related requests and streaming. |
| `LikedSongEndpoints.cs` | Managing favorite songs. |
| `PlaybackEndpoints.cs` | Playback state management. |

### API Key Middleware

`ApiKeyMiddleware.cs` centralizes API key validation in the HTTP request pipeline, keeping authentication separate from individual endpoint handlers.

### Services and Database

| Component | Responsibility |
|---|---|
| `YouTubeService.cs` | YouTube-related search and media operations. |
| `AudioCacheService.cs` | Managing cached audio data. |
| `MusicDatabase.cs` | Database operations. |
| SQLite and Dapper | Persistent storage and database access. |
| `MusicHub.cs` | SignalR real-time communication. |

---

## Self-Hosted Infrastructure

I host my backend inside a **Proxmox LXC container running Debian 12**, dedicated exclusively to running my C# .NET API and SQLite database.

This lightweight setup keeps the infrastructure simple, affordable, and under my control.

<div align="center">

<img src="Docs/proxmox-screenshot.png" alt="Proxmox server running the Personal Music App backend" width="800"/>

*Proxmox hosting the Personal Music App backend.*

</div>
