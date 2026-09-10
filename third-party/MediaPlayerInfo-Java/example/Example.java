import com.mediaplayerinfo.IMediaSession;
import com.mediaplayerinfo.MediaPlayerInfo;

public final class Example {
    public static void main(String[] args) {
        MediaPlayerInfo api = MediaPlayerInfo.getInstance();

        for (IMediaSession session : api.getSessions()) {
            System.out.printf(
                    "%s | %s - %s | %d/%d | playing=%s%n",
                    session.getAppName(),
                    session.getArtist(),
                    session.getTitle(),
                    session.getPosition(),
                    session.getDuration(),
                    session.isPlaying()
            );
            session.close();
        }
    }
}
