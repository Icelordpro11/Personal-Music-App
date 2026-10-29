namespace PersonalMusicApi.Models;

/// <summary>Represents a song saved to the user's liked songs list.</summary>
public sealed record LikedSong(
    string Id,
    string Title,
    string? Artist,
    string? Source,
    string? Thumbnail,
    long AddedAt);
