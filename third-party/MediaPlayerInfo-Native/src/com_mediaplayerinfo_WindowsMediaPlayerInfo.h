#include <jni.h>

#ifndef _Included_com_mediaplayerinfo_WindowsMediaPlayerInfo
#define _Included_com_mediaplayerinfo_WindowsMediaPlayerInfo
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jobject JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_getMediaSessions
  (JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_disposeSession
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_play
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_pause
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_next
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_previous
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_togglePlayPause
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_stop
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_seek
  (JNIEnv *, jobject, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
