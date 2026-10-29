# Personal Music API

An ASP.NET Core API for searching tracks, streaming audio, caching frequently played tracks, and synchronizing liked songs and playback state between devices.

## Project structure

```text
PersonalMusicApi/
├── Data/
│   └── MusicDatabase.cs
├── Endpoints/
│   ├── AudioEndpoints.cs
│   ├── HealthEndpoints.cs
│   ├── LikedSongEndpoints.cs
│   ├── PlaybackEndpoints.cs
│   └── SearchEndpoints.cs
├── Hubs/
│   └── MusicHub.cs
├── Middleware/
│   └── ApiKeyMiddleware.cs
├── Models/
│   ├── LikedSong.cs
│   ├── PlaybackState.cs
│   └── TrackSearchResult.cs
├── Services/
│   ├── AudioCacheService.cs
│   └── YouTubeService.cs
├── Program.cs
├── appsettings.json
└── PersonalMusicApi.csproj
```

## Responsibilities

- **Program.cs** — configures the application and registers services.
- **Endpoints/** — defines HTTP routes, grouped by feature.
- **Services/YouTubeService.cs** — searches YouTube and resolves temporary audio URLs using `yt-dlp`.
- **Services/AudioCacheService.cs** — streams audio and manages the local audio cache.
- **Data/MusicDatabase.cs** — creates the SQLite database and tables.
- **Models/** — contains data types used by the API.
- **Hubs/MusicHub.cs** — broadcasts live changes through SignalR.
- **Middleware/ApiKeyMiddleware.cs** — optionally protects `/api` routes with an API key.

## Requirements

- .NET 8 SDK/runtime
- `yt-dlp` installed and available on `PATH`
- Write access to `/opt/music-api/data`
- Write access to the application's `audio-cache` directory

## Run locally

```bash
dotnet restore
dotnet run
```

The API listens on port `5080`.

## API routes

| Method | Route | Purpose |
|---|---|---|
| GET | `/` | API name and cache size |
| GET | `/api/ping` | Basic health check |
| GET | `/api/search?query=...` | Search tracks |
| GET | `/api/yt/audio?id=...` | Stream audio |
| GET | `/api/yt/resolve?id=...` | Resolve a direct audio URL |
| GET | `/api/liked` | List liked songs |
| POST | `/api/liked` | Add or update a liked song |
| DELETE | `/api/liked/{id}` | Remove a liked song |
| GET | `/api/playback-state` | Get shared playback state |
| PUT | `/api/playback-state` | Update shared playback state |
| SignalR | `/hub/music` | Receive live change notifications |

## Optional API key

Set `MUSIC_API_SECRET` in the server environment. When configured, requests to `/api` must include:

```http
X-Api-Key: your-secret-value
```

The SignalR hub is outside `/api` and is not protected by this middleware. Configure hub authentication separately if it needs to be private.

## Notes

- The audio cache is limited to approximately 6 GB; old files are removed when the limit is exceeded.
- The SQLite database is stored at `/opt/music-api/data/music.db`.
- This project invokes `yt-dlp` to retrieve YouTube search and stream information. Make sure your use complies with the source service's terms and applicable copyright law.
- Review and test the project in your target environment before deploying it.
