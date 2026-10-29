using PersonalMusicApi.Services;

namespace PersonalMusicApi.Endpoints;

/// <summary>Maps track search endpoints.</summary>
public static class SearchEndpoints
{
    public static IEndpointRouteBuilder MapSearchEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet("/api/search", async (string query, HttpRequest request, YouTubeService youtube, AudioCacheService cache, CancellationToken cancellationToken) =>
        {
            if (string.IsNullOrWhiteSpace(query))
            {
                return Results.BadRequest(new { error = "Enter a query." });
            }

            var baseUrl = $"{request.Scheme}://{request.Host}";
            var tracks = await youtube.SearchAsync(query, baseUrl, cancellationToken);

            foreach (var track in tracks.Take(3))
            {
                var trackId = track.Id;
                _ = Task.Run(() => cache.EnsureCachedAsync(trackId, CancellationToken.None));
            }

            return Results.Ok(new { query, count = tracks.Count, tracks });
        });

        return endpoints;
    }
}
