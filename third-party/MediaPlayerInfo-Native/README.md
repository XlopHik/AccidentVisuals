# MediaPlayerInfo-Native

Windows x64 JNI DLL for `com.mediaplayerinfo`.

## Backend

The implementation uses `Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager`
(SMTC/Global System Media Transport Controls). It can enumerate multiple media sessions and
query media properties, playback state, timeline, source application and album artwork.

Microsoft documents `GetSessions()`, `GetCurrentSession()`, and the media/session control
operations on the Windows Media Control API.

## Requirements

- Windows 10 1809+ / Windows 11 x64
- Visual Studio 2022 with Desktop C++ workload
- Windows 10/11 SDK
- JDK 21+ x64
- CMake 3.24+

`JAVA_HOME` must point to the JDK used by the Java project.

## Build

From a Visual Studio Developer PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cmake -S . -B build -A x64
cmake --build build --config Release
```

The DLL will be:

```text
build\Release\MediaPlayerInfo.dll
```

## JNI header

The Java project contains the native declarations. If you want to regenerate the JNI
header exactly as in the requested workflow:

```powershell
javac -h ..\MediaPlayerInfo-Native\src `
  ..\MediaPlayerInfo-Java\src\main\java\dev\redstones\mediaplayerinfo\WindowsMediaPlayerInfo.java
```

The C++ source uses explicit JNI signatures, so the generated header is not required by CMake.

## Native lifetime

Every returned Java `MediaSession` owns one `MediaSessionWrapper*`. Calling `disposeSession`
deletes that wrapper. The wrapper itself holds a C++/WinRT reference to the Windows media
session; C++/WinRT releases the underlying COM/WinRT object automatically when the wrapper
is destroyed.

No COM or WinRT work is performed from `DllMain`.

## Important limitation

SMTC exposes media sessions only for applications that publish media controls to Windows.
An application that does not expose an SMTC session cannot be made visible by this library
without a separate player-specific integration.
