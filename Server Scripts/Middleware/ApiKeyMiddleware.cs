namespace PersonalMusicApi.Middleware;

/// <summary>
/// Optionally protects API routes with the MUSIC_API_SECRET environment variable.
/// Requests must include the matching value in the X-Api-Key header when a secret is configured.
/// </summary>
public sealed class ApiKeyMiddleware
{
    private readonly RequestDelegate _next;
    private readonly string _apiSecret;

    public ApiKeyMiddleware(RequestDelegate next)
    {
        _next = next;
        _apiSecret = Environment.GetEnvironmentVariable("MUSIC_API_SECRET") ?? "";
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var isApiRequest = context.Request.Path.StartsWithSegments("/api");

        if (isApiRequest && !string.IsNullOrEmpty(_apiSecret) && context.Request.Headers["X-Api-Key"].ToString() != _apiSecret)
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            await context.Response.WriteAsync("unauthorized");
            return;
        }

        await _next(context);
    }
}
