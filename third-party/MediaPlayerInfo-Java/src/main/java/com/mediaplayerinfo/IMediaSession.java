package com.mediaplayerinfo;

public interface IMediaSession {
    String getTitle();
    String getArtist();
    String getAlbum();

    boolean isPlaying();
    boolean isPaused();

    long getPosition();
    long getDuration();

    String getAppName();

    byte[] getArtworkPng();

    MediaInfo getMedia();

    boolean play();
    boolean pause();
    boolean next();
    boolean previous();
    boolean togglePlayPause();
    boolean stop();

    boolean seek(long positionSeconds);
}
