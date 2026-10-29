using Dapper;
using Microsoft.Data.Sqlite;
using Microsoft.AspNetCore.SignalR;
using PersonalMusicApi.Data;
using PersonalMusicApi.Hubs;
using PersonalMusicApi.Models;

namespace PersonalMusicApi.Endpoints;

/// <summary>Maps endpoints for adding, listing, and removing liked songs.</summary>
public static class LikedSongEndpoints
{
    public static IEndpointRouteBuilder MapLikedSongEndpoints(this IEndpointRouteBuilder endpoints)
    {
        ///Returns liked songs, newest first.
        endpoints.MapGet("/api/liked", (MusicDatabase database) =>
        {
            using var db = new SqliteConnection(database.ConnectionString);

            var songs = db.Query<LikedSong>("""
                SELECT id AS Id, title AS Title, artist AS Artist, source AS Source,
                       thumbnail AS Thumbnail, added_at AS AddedAt
                FROM liked_songs
                ORDER BY added_at DESC
                """);

            return Results.Ok(songs);
        });

        /// Saves a liked song and notifies connected devices.
        endpoints.MapPost("/api/liked", async (LikedSong song, MusicDatabase database, IHubContext<MusicHub> hub) =>
        {
            var songToSave = song with
            {
                AddedAt = song.AddedAt == 0
                    ? DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                    : song.AddedAt
            };

            using var db = new SqliteConnection(database.ConnectionString);
            db.Execute("""
                INSERT INTO liked_songs (id, title, artist, source, thumbnail, added_at)
                VALUES (@Id, @Title, @Artist, @Source, @Thumbnail, @AddedAt)
                ON CONFLICT(id) DO UPDATE SET
                    title = @Title,
                    artist = @Artist,
                    source = @Source,
                    thumbnail = @Thumbnail;
                """, songToSave);

            await hub.Clients.All.SendAsync("LikedChanged");
            return Results.Ok(songToSave);
        });

        /// Removes a liked song and notifies connected devices.
        endpoints.MapDelete("/api/liked/{id}", async (string id, MusicDatabase database, IHubContext<MusicHub> hub) =>
        {
            using var db = new SqliteConnection(database.ConnectionString);
            db.Execute("DELETE FROM liked_songs WHERE id = @id", new { id });

            await hub.Clients.All.SendAsync("LikedChanged");
            return Results.Ok();
        });

        return endpoints;
    }
}
