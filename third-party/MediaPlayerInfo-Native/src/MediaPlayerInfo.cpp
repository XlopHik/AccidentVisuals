#include <jni.h>

#include <limits>
#include <mutex>
#include <string>
#include <vector>

#include <windows.h>
#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Media.Control.h>

#include "MediaSessionWrapper.h"

using namespace winrt;
using namespace Windows::Foundation::Collections;
using namespace Windows::Media::Control;

namespace {

void ensureWinRtApartment() {
    thread_local bool initialized = false;
    if (initialized) return;

    try {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
    } catch (const winrt::hresult_error& e) {
        if (e.code() != RPC_E_CHANGED_MODE) {
            throw;
        }
    }

    initialized = true;
}

void throwJava(JNIEnv* env, char const* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls) {
        env->ThrowNew(cls, message);
    }
}

jstring toJString(JNIEnv* env, std::wstring const& value) {
    return env->NewString(
        reinterpret_cast<jchar const*>(value.data()),
        static_cast<jsize>(value.size()));
}

jobject newSession(
    JNIEnv* env,
    jclass cls,
    jmethodID ctor,
    media_player_info::MediaSessionWrapper* wrapper) {

    const auto data = wrapper->snapshot();

    jstring title = toJString(env, data.title);
    jstring artist = toJString(env, data.artist);
    jstring album = toJString(env, data.album);
    jstring appName = toJString(env, data.appName);

    if (!title || !artist || !album || !appName) {
        if (title) env->DeleteLocalRef(title);
        if (artist) env->DeleteLocalRef(artist);
        if (album) env->DeleteLocalRef(album);
        if (appName) env->DeleteLocalRef(appName);
        delete wrapper;
        throwJava(env, "Unable to allocate Java strings");
        return nullptr;
    }

    jbyteArray artwork = nullptr;
    if (!data.artworkPng.empty()) {
        if (data.artworkPng.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
            env->DeleteLocalRef(title);
            env->DeleteLocalRef(artist);
            env->DeleteLocalRef(album);
            env->DeleteLocalRef(appName);
            delete wrapper;
            throwJava(env, "Artwork is too large");
            return nullptr;
        }

        artwork = env->NewByteArray(static_cast<jsize>(data.artworkPng.size()));
        if (!artwork) {
            env->DeleteLocalRef(title);
            env->DeleteLocalRef(artist);
            env->DeleteLocalRef(album);
            env->DeleteLocalRef(appName);
            delete wrapper;
            throwJava(env, "Unable to allocate artwork array");
            return nullptr;
        }

        env->SetByteArrayRegion(
            artwork,
            0,
            static_cast<jsize>(data.artworkPng.size()),
            reinterpret_cast<jbyte const*>(data.artworkPng.data()));
    }

    jobject result = env->NewObject(
        cls,
        ctor,
        reinterpret_cast<jlong>(wrapper),
        title,
        artist,
        album,
        data.playing ? JNI_TRUE : JNI_FALSE,
        data.paused ? JNI_TRUE : JNI_FALSE,
        static_cast<jlong>(data.positionSeconds),
        static_cast<jlong>(data.durationSeconds),
        appName,
        artwork);

    env->DeleteLocalRef(title);
    env->DeleteLocalRef(artist);
    env->DeleteLocalRef(album);
    env->DeleteLocalRef(appName);
    if (artwork) env->DeleteLocalRef(artwork);

    if (!result) {
        delete wrapper;
    }

    return result;
}

}

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_getMediaSessions(
    JNIEnv* env, jobject) {
    try {
        ensureWinRtApartment();

        auto manager = GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();
        if (!manager) {
            return nullptr;
        }

        auto sessions = manager.GetSessions();

        jclass sessionClass =
            env->FindClass("com/mediaplayerinfo/MediaSession");
        if (!sessionClass) {
            throwJava(env, "MediaSession class not found");
            return nullptr;
        }

        jmethodID ctor = env->GetMethodID(
            sessionClass,
            "<init>",
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZJJLjava/lang/String;[B)V");

        if (!ctor) {
            throwJava(env, "MediaSession constructor not found");
            return nullptr;
        }

        jclass arrayListClass = env->FindClass("java/util/ArrayList");
        jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
        jmethodID add = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

        if (!arrayListCtor || !add) {
            throwJava(env, "java.util.ArrayList methods not found");
            return nullptr;
        }

        jobject result = env->NewObject(arrayListClass, arrayListCtor);
        if (!result) {
            return nullptr;
        }

        uint32_t count = sessions.Size();
        for (uint32_t i = 0; i < count; i++) {
            try {
                auto session = sessions.GetAt(i);
                if (!session) continue;

                auto* wrapper = new media_player_info::MediaSessionWrapper(session);
                jobject javaSession = newSession(env, sessionClass, ctor, wrapper);

                if (env->ExceptionCheck()) {
                    return nullptr;
                }

                if (!javaSession) {
                    continue;
                }

                env->CallBooleanMethod(result, add, javaSession);
                env->DeleteLocalRef(javaSession);

                if (env->ExceptionCheck()) {
                    return nullptr;
                }
            } catch (...) {
            }
        }

        return result;
    } catch (const winrt::hresult_error& e) {
        std::string msg = "Windows Media Control error: ";
        msg += winrt::to_string(e.message());
        throwJava(env, msg.c_str());
        return nullptr;
    } catch (const std::exception& e) {
        throwJava(env, e.what());
        return nullptr;
    } catch (...) {
        throwJava(env, "Unknown native error while enumerating media sessions");
        return nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_disposeSession(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        delete reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
    } catch (...) {
        throwJava(env, "Failed to dispose media session");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_play(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->play() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native play() failed");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_pause(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->pause() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native pause() failed");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_next(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->next() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native next() failed");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_previous(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->previous() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native previous() failed");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_togglePlayPause(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->togglePlayPause() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native togglePlayPause() failed");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_stop(
    JNIEnv* env, jobject, jlong nativePtr) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->stop() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native stop() failed");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_mediaplayerinfo_WindowsMediaPlayerInfo_seek(
    JNIEnv* env, jobject, jlong nativePtr, jlong positionSeconds) {
    try {
        auto* wrapper = reinterpret_cast<media_player_info::MediaSessionWrapper*>(nativePtr);
        return wrapper && wrapper->seek(static_cast<std::int64_t>(positionSeconds))
            ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        throwJava(env, "Native seek() failed");
        return JNI_FALSE;
    }
}

}
