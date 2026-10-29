using System.Collections.Concurrent;
using System.Net;

namespace PersonalMusicApi.Services;

/// <summary>Streams audio, stores cached tracks, and removes old files when the cache grows too large.</summary>
public sealed class AudioCacheService
{
    private const long CacheLimitBytes = 6L * 1024 * 1024 * 1024;
    private const int BufferSize = 1 << 16;

    private readonly string _cacheDirectory;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly YouTubeService _youtube;
    private readonly ConcurrentDictionary<string, SemaphoreSlim> _fileLocks = new();

    public AudioCacheService(IHttpClientFactory httpClientFactory, YouTubeService youtube)
    {
        _httpClientFactory = httpClientFactory;
        _youtube = youtube;

        _cacheDirectory = Path.Combine(AppContext.BaseDirectory, "audio-cache");
        Directory.CreateDirectory(_cacheDirectory);
    }

    /// <summary>Streams a track and caches it when the upstream response contains the complete file.</summary>
    public async Task StreamAndCacheAsync(HttpContext context, string id, CancellationToken cancellationToken)
    {
        var directUrl = await _youtube.ResolveAsync(id, cancellationToken);
        if (string.IsNullOrWhiteSpace(directUrl))
        {
            context.Response.StatusCode = StatusCodes.Status404NotFound;
            return;
        }

        var client = _httpClientFactory.CreateClient("yt");
        using var request = new HttpRequestMessage(HttpMethod.Get, directUrl);

        var rangeHeader = context.Request.Headers.Range.ToString();
        var isRangeRequest = !string.IsNullOrEmpty(rangeHeader);

        if (isRangeRequest)
        {
            request.Headers.TryAddWithoutValidation("Range", rangeHeader);
        }

        using var upstreamResponse = await client.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);

        var extension = GetExtensionFromContentType(upstreamResponse.Content.Headers.ContentType?.MediaType);

        context.Response.StatusCode = (int)upstreamResponse.StatusCode;
        context.Response.Headers["Accept-Ranges"] = "bytes";
        context.Response.ContentType =
            upstreamResponse.Content.Headers.ContentType?.ToString() ?? "audio/mp4";

        if (upstreamResponse.Content.Headers.ContentLength.HasValue)
        {
            context.Response.ContentLength = upstreamResponse.Content.Headers.ContentLength;
        }

        if (upstreamResponse.Content.Headers.ContentRange is not null)
        {
            context.Response.Headers["Content-Range"] =
                upstreamResponse.Content.Headers.ContentRange.ToString();
        }

        await using var upstreamStream = await upstreamResponse.Content.ReadAsStreamAsync(cancellationToken);

        SemaphoreSlim? fileLock = null;
        var shouldCache = false;

        // Partial range responses must not be stored as complete audio files.
        if (!isRangeRequest &&
            upstreamResponse.StatusCode == HttpStatusCode.OK &&
            FindCached(id) is null)
        {
            fileLock = _fileLocks.GetOrAdd(id, _ => new SemaphoreSlim(1, 1));
            shouldCache = fileLock.Wait(0);
        }

        if (!shouldCache)
        {
            try
            {
                await upstreamStream.CopyToAsync(context.Response.Body, BufferSize, cancellationToken);
            }
            catch
            {
                // The client may disconnect while audio is being streamed.
            }

            return;
        }

        EvictIfNeeded();

        var temporaryPath = Path.Combine(_cacheDirectory, id + ".part");
        var completed = false;

        try
        {
            await using var cacheStream = new FileStream(
                temporaryPath,
                FileMode.Create,
                FileAccess.Write,
                FileShare.None,
                BufferSize,
                useAsync: true);

            var buffer = new byte[BufferSize];
            int bytesRead;

            while ((bytesRead = await upstreamStream.ReadAsync(buffer, cancellationToken)) > 0)
            {
                await context.Response.Body.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
                await cacheStream.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
            }

            completed = true;
        }
        catch
        {
            // Do not keep an incomplete cache file after a disconnect or upstream error.
        }
        finally
        {
            try
            {
                if (completed)
                {
                    File.Move(temporaryPath, Path.Combine(_cacheDirectory, id + extension), true);
                }
                else
                {
                    File.Delete(temporaryPath);
                }
            }
            catch
            {
                // Cache cleanup should not interrupt the response.
            }

            fileLock!.Release();
        }
    }

    /// <summary>Downloads a track in the background to prepare it for faster future playback.</summary>
    public async Task<string?> EnsureCachedAsync(string id, CancellationToken cancellationToken)
    {
        if (!YouTubeService.IsValidId(id))
        {
            return null;
        }

        var cachedFile = FindCached(id);
        if (cachedFile is not null)
        {
            Touch(cachedFile);
            return cachedFile;
        }

        var fileLock = _fileLocks.GetOrAdd(id, _ => new SemaphoreSlim(1, 1));
        await fileLock.WaitAsync(cancellationToken);

        try
        {
            cachedFile = FindCached(id);
            if (cachedFile is not null)
            {
                return cachedFile;
            }

            EvictIfNeeded();

            var outputTemplate = Path.Combine(_cacheDirectory, id + ".%(ext)s");

            await YouTubeService.RunYtDlpAsync(
                cancellationToken,
                "-f", "140/bestaudio[ext=m4a]/bestaudio",
                "--extractor-args", "youtube:player_client=android",
                "--no-warnings", "--no-part",
                "-o", outputTemplate,
                $"https://www.youtube.com/watch?v={id}");

            return FindCached(id);
        }
        finally
        {
            fileLock.Release();
        }
    }

    /// <summary>Finds a cached audio file for a track ID.</summary>
    public string? FindCached(string id)
    {
        foreach (var file in Directory.EnumerateFiles(_cacheDirectory, id + ".*"))
        {
            if (!file.EndsWith(".part", StringComparison.OrdinalIgnoreCase))
            {
                return file;
            }
        }

        return null;
    }

    /// <summary>Updates a cached file's access time so older files can be evicted first.</summary>
    public void Touch(string filePath)
    {
        try
        {
            File.SetLastAccessTimeUtc(filePath, DateTime.UtcNow);
        }
        catch
        {
            // A failed timestamp update should not interrupt playback.
        }
    }

    /// <summary>Returns the cache size in megabytes.</summary>
    public long CacheSizeMb()
    {
        try
        {
            return new DirectoryInfo(_cacheDirectory)
                .GetFiles()
                .Sum(file => file.Length) / (1024 * 1024);
        }
        catch
        {
            return 0;
        }
    }

    /// <summary>Removes least-recently-used files if the cache exceeds its size limit.</summary>
    private void EvictIfNeeded()
    {
        try
        {
            var files = new DirectoryInfo(_cacheDirectory)
                .GetFiles()
                .Where(file => !file.Name.EndsWith(".part", StringComparison.OrdinalIgnoreCase))
                .ToArray();

            long totalBytes = files.Sum(file => file.Length);
            if (totalBytes <= CacheLimitBytes)
            {
                return;
            }

            foreach (var file in files.OrderBy(file => file.LastAccessTimeUtc))
            {
                try
                {
                    totalBytes -= file.Length;
                    file.Delete();
                }
                catch
                {
                    // Skip files that cannot be removed.
                }

                if (totalBytes <= CacheLimitBytes * 8 / 10)
                {
                    break;
                }
            }
        }
        catch
        {
            // Cache maintenance is best-effort and should not take down the API.
        }
    }

    /// <summary>Maps an upstream content type to the file extension used by the cache.</summary>
    private static string GetExtensionFromContentType(string? mediaType) => mediaType switch
    {
        "audio/webm" => ".webm",
        "audio/ogg" => ".ogg",
        _ => ".m4a"
    };

    /// <summary>Returns the content type that matches a cached audio file's extension.</summary>
    public static string GetContentType(string filePath) =>
        Path.GetExtension(filePath).ToLowerInvariant() switch
        {
            ".webm" => "audio/webm",
            ".opus" => "audio/ogg",
            ".ogg" => "audio/ogg",
            _ => "audio/mp4"
        };
}
