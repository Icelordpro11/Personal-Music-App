## 💻 Code Examples

The Personal Music App uses a modular backend architecture built with **ASP.NET Core and C#**. The API is separated into endpoints, middleware, services, models, and a SignalR hub to keep the code organized and maintainable.

### 1. HTTP API Endpoints

The backend uses ASP.NET Core HTTP endpoints to handle requests from the Android application. These endpoints separate API functionality into individual files, making the project easier to maintain and extend.

**Example endpoint structure:**

```csharp
app.MapGet("/api/ping", () =>
{
    return Results.Ok(new
    {
        status = "online"
    });
});
```

This example illustrates how the API can expose a simple health-check endpoint. The Android application or another client can use it to check whether the server is reachable.

**Endpoint organization:**

| File | Responsibility |
|---|---|
| `AudioEndpoints.cs` | Audio streaming and audio-related requests |
| `HealthEndpoints.cs` | API health checks |
| `LikedSongEndpoints.cs` | Managing liked songs |
| `PlaybackEndpoints.cs` | Reading and updating playback state |
| `SearchEndpoints.cs` | Searching for music |

### 2. API Key Middleware

The backend includes an `ApiKeyMiddleware.cs` file for handling API key authentication.

Middleware provides a centralized place to inspect incoming HTTP requests before they reach the relevant endpoints.

```csharp
public async Task InvokeAsync(HttpContext context)
{
    // Validate the request before continuing.
    // Requests that fail authentication can be rejected here.

    await _next(context);
}
```

This example illustrates the middleware pipeline. The actual implementation should validate the configured API key and return an unauthorized response when a protected request does not provide valid credentials.

Keeping authentication logic separate from endpoint handlers makes the API easier to maintain.

### 3. Playback State Management

The `PlaybackEndpoints.cs` file handles playback-related API requests, while `PlaybackState.cs` represents the playback state.

Separating the model from the endpoint logic makes it easier to extend playback functionality without placing all the application logic in one file.

**Example playback model:**

```csharp
public sealed class PlaybackState
{
    public string? TrackId { get; set; }

    public double Position { get; set; }

    public bool IsPlaying { get; set; }
}
```

This simplified model illustrates the type of information a music application may need to represent:

- The currently selected track.
- The current playback position.
- Whether playback is active.

The exact properties should match the implementation in `Models/PlaybackState.cs`.

### 4. Real-Time Communication with SignalR

The `MusicHub.cs` file provides a SignalR hub for real-time communication between connected clients.

SignalR can be used to distribute playback updates without requiring clients to repeatedly poll the server.

**Example hub:**

```csharp
public sealed class MusicHub : Hub
{
    public async Task SendPlaybackUpdate(object playbackState)
    {
        await Clients.Others.SendAsync(
            "PlaybackUpdated",
            playbackState);
    }
}
```

This simplified example demonstrates how a hub can broadcast playback updates to other connected clients. The actual hub methods and event names should match the implementation in `Hubs/MusicHub.cs`.

### 5. Music Search and Audio Services

The backend separates external music search and audio processing into service classes.

| File | Responsibility |
|---|---|
| `YouTubeService.cs` | Encapsulates YouTube-related search and media operations |
| `AudioCacheService.cs` | Manages cached audio data |
| `MusicDatabase.cs` | Handles database operations |
| `TrackSearchResult.cs` | Represents a music search result |

This separation keeps endpoint handlers focused on HTTP requests while services manage the underlying operations.

### 6. Project Architecture

The backend follows a modular structure:

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
└── PersonalMusicApi.csproj
```

This organization separates responsibilities and makes individual components easier to understand, test, and extend.

**Note:** The code snippets above are illustrative examples based on the project structure, not verified copies of your implementation. Replace them with the corresponding code from your actual files before presenting them as implementation examples.
