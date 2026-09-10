package com.mediaplayerinfo;

public class MediaPlayerInfoException extends RuntimeException {
    public MediaPlayerInfoException(String message) {
        super(message);
    }

    public MediaPlayerInfoException(String message, Throwable cause) {
        super(message, cause);
    }
}
