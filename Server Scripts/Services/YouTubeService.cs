using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text.Json;
using PersonalMusicApi.Models;

namespace PersonalMusicApi.Services;

/// <summary>Handles YouTube searches, temporary stream URL resolution, and yt-dlp processes.</summary>
public sealed class YouTubeService
{
    private readonly ConcurrentDictionary<string, (string Url, DateTime CachedAt)> _urlCache = new();
    private readonly SemaphoreSlim _resolveGate = new(4, 4);

    /// <summary>Searches YouTube and returns track details for the client application.</summary>
    public async Task<List<TrackSearchResult>> SearchAsync(
        string query,
        string baseUrl,
        CancellationToken cancellationToken)
    {
        var json = await RunYtDlpAsync(
            cancellationToken,
            "-J", "--flat-playlist", "--no-warnings", $"ytsearch20:{query}");

        var tracks = new List<TrackSearchResult>();
        if (string.IsNullOrWhiteSpace(json))
        {
            return tracks;
        }

        using var document = JsonDocument.Parse(json);

        if (!document.RootElement.TryGetProperty("entries", out var entries) ||
            entries.ValueKind != JsonValueKind.Array)
        {
            return tracks;
        }

        foreach (var entry in entries.EnumerateArray())
        {
            if (entry.ValueKind != JsonValueKind.Object)
            {
                continue;
            }

            var id = GetString(entry, "id");
            var title = GetString(entry, "title");

            if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(title))
            {
                continue;
            }

            var artist = GetString(entry, "channel");
            if (string.IsNullOrWhiteSpace(artist))
            {
                artist = GetString(entry, "uploader");
            }

            tracks.Add(new TrackSearchResult(
                id,
                title,
                string.IsNullOrWhiteSpace(artist) ? null : artist,
                "YouTube",
                $"https://i.ytimg.com/vi/{id}/hqdefault.jpg",
                $"{baseUrl}/api/yt/audio?id={id}"));
        }

        return tracks;
    }

    /// <summary>Resolves a direct audio URL and caches it in memory for up to four hours.</summary>
    public async Task<string> ResolveAsync(string id, CancellationToken cancellationToken)
    {
        if (TryGetCachedUrl(id, out var cachedUrl))
        {
            return cachedUrl;
        }

        await _resolveGate.WaitAsync(cancellationToken);

        try
        {
            // Check again because another request may have resolved this ID while we waited.
            if (TryGetCachedUrl(id, out cachedUrl))
            {
                return cachedUrl;
            }

            var url = (await RunYtDlpAsync(
                cancellationToken,
                "-f", "140/bestaudio[ext=m4a]/bestaudio",
                "--extractor-args", "youtube:player_client=android",
                "--no-warnings", "--socket-timeout", "15",
                "-g", $"https://www.youtube.com/watch?v={id}"))
                .Split('\n')[0].Trim();

            if (string.IsNullOrWhiteSpace(url))
            {
                url = (await RunYtDlpAsync(
                    cancellationToken,
                    "-f", "bestaudio", "--no-warnings",
                    "-g", $"https://www.youtube.com/watch?v={id}"))
                    .Split('\n')[0].Trim();
            }

            if (!string.IsNullOrWhiteSpace(url))
            {
                _urlCache[id] = (url, DateTime.UtcNow);
            }

            return url;
        }
        finally
        {
            _resolveGate.Release();
        }
    }

    /// <summary>Validates a YouTube video ID before it is used in a request or file path.</summary>
    public static bool IsValidId(string id) =>
        !string.IsNullOrEmpty(id) &&
        id.Length <= 20 &&
        id.All(character => char.IsLetterOrDigit(character) || character is '-' or '_');

    /// <summary>Runs yt-dlp with argument-list handling instead of passing arguments through a shell.</summary>
    public static async Task<string> RunYtDlpAsync(
        CancellationToken cancellationToken,
        params string[] arguments)
    {
        try
        {
            var startInfo = new ProcessStartInfo
            {
                FileName = "yt-dlp",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false
            };

            foreach (var argument in arguments)
            {
                startInfo.ArgumentList.Add(argument);
            }

            using var process = Process.Start(startInfo);
            if (process is null)
            {
                return "";
            }

            var output = await process.StandardOutput.ReadToEndAsync(cancellationToken);
            await process.WaitForExitAsync(cancellationToken);
            return output;
        }
        catch
        {
            // Return an empty result if yt-dlp is unavailable or the process fails.
            return "";
        }
    }

    private bool TryGetCachedUrl(string id, out string url)
    {
        if (_urlCache.TryGetValue(id, out var cached) &&
            DateTime.UtcNow - cached.CachedAt < TimeSpan.FromHours(4))
        {
            url = cached.Url;
            return true;
        }

        url = "";
        return false;
    }

    private static string GetString(JsonElement element, string propertyName) =>
        element.TryGetProperty(propertyName, out var value) &&
        value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? ""
            : "";
}
