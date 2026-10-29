namespace PersonalMusicApi.Models;

/// <summary>Describes a searchable track returned to the music client.</summary>
public sealed record TrackSearchResult(
    string Id,
    string Title,
    string? Artist,
    string Source,
    string Artwork,
    string StreamUrl);
