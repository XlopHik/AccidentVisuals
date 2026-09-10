package com.mediaplayerinfo;

import java.util.Arrays;

public final class MediaInfo {
    private final String title;
    private final String artist;
    private final String album;
    private final long duration;
    private final long position;
    private final byte[] artworkPng;

    public MediaInfo(
            String title,
            String artist,
            String album,
            long duration,
            long position,
            byte[] artworkPng) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.duration = Math.max(0L, duration);
        this.position = Math.max(0L, position);
        this.artworkPng = artworkPng == null ? null : artworkPng.clone();
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public long getDuration() {
        return duration;
    }

    public long getPosition() {
        return position;
    }

    public byte[] getArtworkPng() {
        return artworkPng == null ? null : artworkPng.clone();
    }

    @Override
    public String toString() {
        return "MediaInfo{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", duration=" + duration +
                ", position=" + position +
                ", artworkPng=" + (artworkPng == null ? 0 : artworkPng.length) +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MediaInfo other)) return false;
        return duration == other.duration
                && position == other.position
                && title.equals(other.title)
                && artist.equals(other.artist)
                && album.equals(other.album)
                && Arrays.equals(artworkPng, other.artworkPng);
    }

    @Override
    public int hashCode() {
        int result = title.hashCode();
        result = 31 * result + artist.hashCode();
        result = 31 * result + album.hashCode();
        result = 31 * result + Long.hashCode(duration);
        result = 31 * result + Long.hashCode(position);
        result = 31 * result + Arrays.hashCode(artworkPng);
        return result;
    }
}
