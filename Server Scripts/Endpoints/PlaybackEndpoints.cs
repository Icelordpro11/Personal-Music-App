using Dapper;
using Microsoft.AspNetCore.SignalR;
using Microsoft.Data.Sqlite;
using PersonalMusicApi.Data;
using PersonalMusicApi.Hubs;
using PersonalMusicApi.Models;

namespace PersonalMusicApi.Endpoints;

/// <summary>Maps endpoints for shared playback state.</summary>
public static class PlaybackEndpoints
{
    public static IEndpointRouteBuilder MapPlaybackEndpoints(this IEndpointRouteBuilder endpoints)
    {
        /// Returns the current shared playback state.
        endpoints.MapGet("/api/playback-state", (MusicDatabase database) =>
        {
            using var db = new SqliteConnection(database.ConnectionString);

            var state = db.QuerySingleOrDefault<PlaybackState>("""
                SELECT track_id AS TrackId, title AS Title, artist AS Artist, source AS Source,
                       position AS Position, is_playing AS IsPlaying, active_device AS ActiveDevice,
                       queue_json AS QueueJson, updated_at AS UpdatedAt
                FROM playback_state
                WHERE id = 1
                """);

            return Results.Ok(state);
        });

        /// Saves playback state and broadcasts it to connected devices.
        endpoints.MapPut("/api/playback-state", async (PlaybackState state, MusicDatabase database, IHubContext<MusicHub> hub) =>
        {
            var stateToSave = state with
            {
                UpdatedAt = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            };

            using var db = new SqliteConnection(database.ConnectionString);
            db.Execute("""
                UPDATE playback_state SET
                    track_id = @TrackId,
                    title = @Title,
                    artist = @Artist,
                    source = @Source,
                    position = @Position,
                    is_playing = @IsPlaying,
                    active_device = @ActiveDevice,
                    queue_json = @QueueJson,
                    updated_at = @UpdatedAt
                WHERE id = 1
                """, stateToSave);

            await hub.Clients.All.SendAsync("PlaybackChanged", stateToSave);
            return Results.Ok(stateToSave);
        });

        return endpoints;
    }
}
