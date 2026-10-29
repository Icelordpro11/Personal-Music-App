using PersonalMusicApi.Services;

namespace PersonalMusicApi.Endpoints;

/// <summary>Maps basic health and service information endpoints.</summary>
public static class HealthEndpoints
{
    public static IEndpointRouteBuilder MapHealthEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet("/api/ping", () => Results.Ok(new { ok = true }));

        endpoints.MapGet("/", (AudioCacheService cache) =>
            Results.Ok(new
            {
                name = "Personal Music API",
                cacheMb = cache.CacheSizeMb()
            }));

        return endpoints;
    }
}
