# MediaPlayerInfo-Java

Java 21 library for the Windows native MediaPlayerInfo JNI bridge.

## Public API

```java
MediaPlayerInfo media = MediaPlayerInfo.getInstance();

for (IMediaSession session : media.getSessions()) {
    System.out.println(session.getAppName());
    System.out.println(session.getTitle());
    System.out.println(session.getArtist());
    System.out.println(session.getAlbum());
    System.out.println(session.getPosition() + "/" + session.getDuration());
    System.out.println(session.isPlaying());

    byte[] png = session.getArtworkPng();

    session.close();
}
```

The old API shape used by the Minecraft mod is also preserved:

```java
List<IMediaSession> sessions = MediaPlayerInfo.Instance.getMediaSessions();

for (IMediaSession session : sessions) {
    MediaInfo info = ((MediaSession) session).getMedia();
    String owner = session.getAppName();

    String title = info.getTitle();
    String artist = info.getArtist();
    long position = info.getPosition();
    long duration = info.getDuration();
    byte[] artwork = info.getArtworkPng();
}
```

## Controls

```java
session.play();
session.pause();
session.next();
session.previous();
session.togglePlayPause();
session.stop();
session.seek(120);
```

A control operation returns `false` when Windows/the player refuses the request.

## Track/session change events

```java
try (MediaPlayerInfo.MediaSessionWatcher watcher =
         media.watch(1000, sessions -> {
             // called when the observed snapshot changes
         })) {
    // application work
}
```

The watcher is intentionally Java-side polling. It avoids JNI callbacks from native
WinRT event handlers into an arbitrary JVM thread. The native backend itself uses the
Windows session API.

## DLL inside the JAR

The native DLL belongs here:

```text
src/main/resources/native/windows-x64/MediaPlayerInfo.dll
```

A DLL stored inside a JAR cannot be passed directly to `System.loadLibrary()`, because
`System.loadLibrary()` resolves a native library from the native library path rather than
from a ZIP/JAR entry. Therefore the library extracts the resource to a temporary directory
and calls `System.load(absolutePath)`.

If the resource is absent, the loader falls back to:

```java
System.loadLibrary("MediaPlayerInfo");
```

This is useful for development with `-Djava.library.path=...`.

## Building

1. Build the native project first.
2. Copy:

```text
MediaPlayerInfo-Native/build/Release/MediaPlayerInfo.dll
```

to:

```text
MediaPlayerInfo-Java/src/main/resources/native/windows-x64/MediaPlayerInfo.dll
```

3. Build the Java library:

```powershell
.\gradlew build
```

or:

```powershell
mvn package
```

The final JAR contains the native DLL.

## Minecraft compatibility

The API intentionally preserves:

- `MediaPlayerInfo.Instance.getMediaSessions()`
- `IMediaSession`
- `MediaSession.getMedia()`
- `MediaInfo.getTitle()`
- `MediaInfo.getArtist()`
- `MediaInfo.getDuration()`
- `MediaInfo.getPosition()`
- `MediaInfo.getArtworkPng()`
- `IMediaSession.getAppName()`

This means the media data flow used by the supplied HUD can be migrated with very few
changes. The supplied HUD additionally expects `LyricsProvider`, which is not a media-player
API and therefore remains outside this library.


## JNI declaration generation

The native project includes a generated-style header for convenience, but the authoritative
Java declaration is `WindowsMediaPlayerInfo.java`. To regenerate the real JNI header:

```powershell
cd MediaPlayerInfo-Java
javac -h ..\MediaPlayerInfo-Native\src `
  src\main\java\dev\redstones\mediaplayerinfo\WindowsMediaPlayerInfo.java
```

If your JDK needs a classpath containing the other Java sources, compile the Java sources
together or generate the header from the compiled class. The C++ implementation uses the
standard JNI exported names and does not require the generated header to be included.

## One important API detail

The requested name `getMediaSessions()` is retained for compatibility, while the clean
public API is `getSessions()`. The old Minecraft code also imported `MediaInfo`, so this
project includes that DTO and `MediaSession.getMedia()` compatibility method.

The supplied HUD's `LyricsProvider` is intentionally not implemented here: lyrics are a
separate service and the Windows media session API only supplies media metadata,
playback/timeline information and artwork.
