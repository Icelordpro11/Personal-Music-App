namespace PersonalMusicApi.Models;

/// <summary>Represents playback information shared between connected devices.</summary>
public sealed record PlaybackState(
    string? TrackId,
    string? Title,
    string? Artist,
    string? Source,
    double Position,
    bool IsPlaying,
    string? ActiveDevice,
    string? QueueJson,
    long UpdatedAt);
