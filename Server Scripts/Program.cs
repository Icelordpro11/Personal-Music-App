using PersonalMusicApi.Data;
using PersonalMusicApi.Endpoints;
using PersonalMusicApi.Hubs;
using PersonalMusicApi.Middleware;
using PersonalMusicApi.Services;

var builder = WebApplication.CreateBuilder(args);

// Register application services.
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.AllowAnyOrigin()
              .AllowAnyHeader()
              .AllowAnyMethod());
});

builder.Services.AddHttpClient("yt", client =>
{
    // Audio streams can remain open for a long time.
    client.Timeout = Timeout.InfiniteTimeSpan;
    client.DefaultRequestHeaders.UserAgent.ParseAdd("PersonalMusic/1.0");
});

builder.Services.AddSignalR();

builder.Services.AddSingleton<MusicDatabase>();
builder.Services.AddSingleton<YouTubeService>();
builder.Services.AddSingleton<AudioCacheService>();

var app = builder.Build();

app.UseCors();
app.UseMiddleware<ApiKeyMiddleware>();

// Keep endpoint definitions outside Program.cs to make the application easier to maintain.
app.MapHealthEndpoints();
app.MapSearchEndpoints();
app.MapAudioEndpoints();
app.MapLikedSongEndpoints();
app.MapPlaybackEndpoints();

app.MapHub<MusicHub>("/hub/music");

app.Run("http://0.0.0.0:5080");
