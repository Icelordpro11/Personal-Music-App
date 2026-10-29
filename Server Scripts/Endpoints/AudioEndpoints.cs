using PersonalMusicApi.Services;
namespace PersonalMusicApi.Endpoints;

/// <summary>Maps audio streaming and direct URL resolution endpoints.</summary>
public static class AudioEndpoints
{
    public static IEndpointRouteBuilder MapAudioEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet("/api/yt/audio", async (string id, HttpContext context, AudioCacheService cache, CancellationToken cancellationToken) =>
        {
            if (!YouTubeService.IsValidId(id))
            {
                return Results.BadRequest();
            }

            var cachedFile = cache.FindCached(id);
            if (cachedFile is not null)
            {
                cache.Touch(cachedFile);
                return Results.File(
                    cachedFile,
                    AudioCacheService.GetContentType(cachedFile),
                    enableRangeProcessing: true);
            }

            await cache.StreamAndCacheAsync(context, id, cancellationToken);
            return Results.Empty;
        });

        ///Returns a temporary direct source URL for the app's download feature
        endpoints.MapGet("/api/yt/resolve", async (string id, YouTubeService youtube, CancellationToken cancellationToken) =>
        {
            if (!YouTubeService.IsValidId(id))
            {
                return Results.BadRequest();
            }

            var url = await youtube.ResolveAsync(id, cancellationToken);

            return string.IsNullOrWhiteSpace(url)
                ? Results.NotFound()
                : Results.Ok(new { url });
        });

        return endpoints;
    }
}
