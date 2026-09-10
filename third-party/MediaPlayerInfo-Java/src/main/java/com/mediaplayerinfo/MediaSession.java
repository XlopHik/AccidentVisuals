package com.mediaplayerinfo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaSession implements IMediaSession, AutoCloseable {
    private long nativePtr;

    private final String title;
    private final String artist;
    private final String album;
    private final boolean playing;
    private final boolean paused;
    private final long position;
    private final long duration;
    private final String appName;
    private final byte[] artworkPng;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    MediaSession(
            long nativePtr,
            String title,
            String artist,
            String album,
            boolean playing,
            boolean paused,
            long position,
            long duration,
            String appName,
            byte[] artworkPng) {
        this.nativePtr = nativePtr;
        this.title = Objects.requireNonNullElse(title, "");
        this.artist = Objects.requireNonNullElse(artist, "");
        this.album = Objects.requireNonNullElse(album, "");
        this.playing = playing;
        this.paused = paused;
        this.position = Math.max(0L, position);
        this.duration = Math.max(0L, duration);
        this.appName = Objects.requireNonNullElse(appName, "");
        this.artworkPng = artworkPng == null ? null : artworkPng.clone();
    }

    long nativePtr() {
        return nativePtr;
    }

    boolean isClosed() {
        return closed.get();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getArtist() {
        return artist;
    }

    @Override
    public String getAlbum() {
        return album;
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public String getAppName() {
        return appName;
    }

    @Override
    public byte[] getArtworkPng() {
        return artworkPng == null ? null : artworkPng.clone();
    }

    public MediaInfo getMedia() {
        return new MediaInfo(title, artist, album, duration, position, artworkPng);
    }

    @Override
    public boolean play() {
        return WindowsMediaPlayerInfo.getInstance().play(this);
    }

    @Override
    public boolean pause() {
        return WindowsMediaPlayerInfo.getInstance().pause(this);
    }

    @Override
    public boolean next() {
        return WindowsMediaPlayerInfo.getInstance().next(this);
    }

    @Override
    public boolean previous() {
        return WindowsMediaPlayerInfo.getInstance().previous(this);
    }

    @Override
    public boolean togglePlayPause() {
        return WindowsMediaPlayerInfo.getInstance().togglePlayPause(this);
    }

    @Override
    public boolean stop() {
        return WindowsMediaPlayerInfo.getInstance().stop(this);
    }

    @Override
    public boolean seek(long positionSeconds) {
        return WindowsMediaPlayerInfo.getInstance().seek(this, positionSeconds);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            long ptr = nativePtr;
            nativePtr = 0L;
            WindowsMediaPlayerInfo.getInstance().disposeSession(ptr);
        }
    }

    @Override
    public String toString() {
        return "MediaSession{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", playing=" + playing +
                ", paused=" + paused +
                ", position=" + position +
                ", duration=" + duration +
                ", appName='" + appName + '\'' +
                '}';
    }
}
