using Microsoft.AspNetCore.SignalR;

namespace PersonalMusicApi.Hubs;

/// <summary>Real-time hub used to notify connected clients about music and playback changes.</summary>
public sealed class MusicHub : Hub
{
}
