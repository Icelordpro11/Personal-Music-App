using Dapper;
using Microsoft.Data.Sqlite;

namespace PersonalMusicApi.Data;

/// <summary>Initializes the SQLite database and exposes its connection string.</summary>
public sealed class MusicDatabase
{
    public string ConnectionString { get; }

    public MusicDatabase()
    {
        // Keep user data outside the published application folder so deployments do not overwrite it.
        const string databaseDirectory = "/opt/music-api/data";
        Directory.CreateDirectory(databaseDirectory);

        ConnectionString = $"Data Source={Path.Combine(databaseDirectory, "music.db")}";

        using var database = new SqliteConnection(ConnectionString);
        database.Execute("""
            CREATE TABLE IF NOT EXISTS liked_songs (
                id        TEXT PRIMARY KEY,
                title     TEXT NOT NULL,
                artist    TEXT,
                source    TEXT,
                thumbnail TEXT,
                added_at  INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS playback_state (
                id            INTEGER PRIMARY KEY CHECK (id = 1),
                track_id      TEXT,
                title         TEXT,
                artist        TEXT,
                source        TEXT,
                position      REAL    NOT NULL DEFAULT 0,
                is_playing    INTEGER NOT NULL DEFAULT 0,
                active_device TEXT,
                queue_json    TEXT,
                updated_at    INTEGER NOT NULL DEFAULT 0
            );

            INSERT OR IGNORE INTO playback_state (id) VALUES (1);
            """);
    }
}
