package com.mediaplayerinfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MediaPlayerInfo {
    public static final MediaPlayerInfo Instance = new MediaPlayerInfo();

    private final WindowsMediaPlayerInfo nativeInfo =
            WindowsMediaPlayerInfo.getInstance();

    private MediaPlayerInfo() {
    }

    public static MediaPlayerInfo getInstance() {
        return Instance;
    }

    public List<IMediaSession> getMediaSessions() {
        return getSessions();
    }

    public List<IMediaSession> getSessions() {
        return nativeInfo.getSessions();
    }

    public MediaSessionWatcher watch(long intervalMillis, MediaSessionListener listener) {
        if (intervalMillis < 100L) {
            throw new IllegalArgumentException("intervalMillis must be >= 100");
        }
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        return new MediaSessionWatcher(intervalMillis, listener);
    }

    public interface MediaSessionListener {
        void onSessionsChanged(List<IMediaSession> sessions);
    }

    public final class MediaSessionWatcher implements AutoCloseable {
        private final ScheduledExecutorService executor;
        private final MediaSessionListener listener;

        private volatile String lastKey = "";

        private MediaSessionWatcher(long intervalMillis, MediaSessionListener listener) {
            this.listener = listener;
            this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MediaPlayerInfo-Watcher");
                t.setDaemon(true);
                return t;
            });

            executor.scheduleWithFixedDelay(
                    this::poll,
                    0L,
                    intervalMillis,
                    TimeUnit.MILLISECONDS);
        }

        private void poll() {
            List<IMediaSession> sessions = Collections.emptyList();
            try {
                sessions = getSessions();

                StringBuilder key = new StringBuilder();
                for (IMediaSession s : sessions) {
                    key.append(s.getAppName()).append('\u0000')
                            .append(s.getTitle()).append('\u0000')
                            .append(s.getArtist()).append('\u0000')
                            .append(s.getPosition()).append('\u0000')
                            .append(s.isPlaying()).append('\u0001');
                }

                String current = key.toString();
                if (!current.equals(lastKey)) {
                    lastKey = current;
                    listener.onSessionsChanged(
                            Collections.unmodifiableList(new ArrayList<>(sessions)));
                }
            } catch (Throwable ignored) {
            } finally {
                for (IMediaSession session : sessions) {
                    if (session instanceof MediaSession nativeSession) {
                        nativeSession.close();
                    }
                }
            }
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
