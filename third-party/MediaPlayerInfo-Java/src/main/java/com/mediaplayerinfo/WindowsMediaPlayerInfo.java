package com.mediaplayerinfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class WindowsMediaPlayerInfo implements AutoCloseable {
    private static final String LIBRARY_NAME = "MediaPlayerInfo";
    private static final String RESOURCE =
            "/native/windows-x64/MediaPlayerInfo.dll";

    private static final WindowsMediaPlayerInfo INSTANCE = new WindowsMediaPlayerInfo();

    private volatile boolean closed;

    static {
        if (!isWindows()) {
            throw new MediaPlayerInfoException(
                    "MediaPlayerInfo is supported only on Windows.");
        }
        NativeLoader.load();
    }

    private WindowsMediaPlayerInfo() {
    }

    public static WindowsMediaPlayerInfo getInstance() {
        return INSTANCE;
    }

    private native List<MediaSession> getMediaSessions();

    public native void disposeSession(long nativePtr);

    private native boolean play(long nativePtr);
    private native boolean pause(long nativePtr);
    private native boolean next(long nativePtr);
    private native boolean previous(long nativePtr);
    private native boolean togglePlayPause(long nativePtr);
    private native boolean stop(long nativePtr);
    private native boolean seek(long nativePtr, long positionSeconds);

    public List<IMediaSession> getSessions() {
        if (closed) {
            return Collections.emptyList();
        }

        List<MediaSession> nativeSessions = getMediaSessions();
        if (nativeSessions == null || nativeSessions.isEmpty()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(nativeSessions);
    }

    public boolean isAvailable() {
        return !closed;
    }

    boolean play(MediaSession session) {
        return valid(session) && play(session.nativePtr());
    }

    boolean pause(MediaSession session) {
        return valid(session) && pause(session.nativePtr());
    }

    boolean next(MediaSession session) {
        return valid(session) && next(session.nativePtr());
    }

    boolean previous(MediaSession session) {
        return valid(session) && previous(session.nativePtr());
    }

    boolean togglePlayPause(MediaSession session) {
        return valid(session) && togglePlayPause(session.nativePtr());
    }

    boolean stop(MediaSession session) {
        return valid(session) && stop(session.nativePtr());
    }

    boolean seek(MediaSession session, long positionSeconds) {
        return valid(session) && seek(session.nativePtr(), Math.max(0L, positionSeconds));
    }

    private boolean valid(MediaSession session) {
        return session != null && !session.isClosed() && session.nativePtr() != 0L && !closed;
    }

    public void dispose(MediaSession session) {
        if (session != null) {
            session.close();
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static final class NativeLoader {
        private static volatile boolean loaded;

        private NativeLoader() {
        }

        static synchronized void load() {
            if (loaded) {
                return;
            }

            try (InputStream in =
                         WindowsMediaPlayerInfo.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    System.loadLibrary(LIBRARY_NAME);
                    loaded = true;
                    return;
                }

                Path dir = Files.createTempDirectory("mediaplayerinfo-native-");
                Path dll = dir.resolve(LIBRARY_NAME + ".dll");
                Files.copy(in, dll, StandardCopyOption.REPLACE_EXISTING);

                dll.toFile().deleteOnExit();
                dir.toFile().deleteOnExit();

                System.load(dll.toAbsolutePath().toString());
                loaded = true;
            } catch (IOException | UnsatisfiedLinkError e) {
                throw new MediaPlayerInfoException(
                        "Unable to load " + LIBRARY_NAME + ".dll", e);
            }
        }
    }
}
