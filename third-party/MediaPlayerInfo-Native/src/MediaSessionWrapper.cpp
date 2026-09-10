#include "MediaSessionWrapper.h"

#include <algorithm>
#include <chrono>
#include <cwctype>
#include <string>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Storage.Streams.h>

using namespace winrt;
using namespace Windows::Media::Control;
using namespace Windows::Storage::Streams;

namespace media_player_info {

namespace {
    std::int64_t toSeconds(Windows::Foundation::TimeSpan value) {
        if (value.count() <= 0) {
            return 0;
        }
        return value.count() / 10'000'000LL;
    }

    std::wstring lower(std::wstring value) {
        std::transform(value.begin(), value.end(), value.begin(),
            [](wchar_t c) { return static_cast<wchar_t>(std::towlower(c)); });
        return value;
    }
}

MediaSessionWrapper::MediaSessionWrapper(
    GlobalSystemMediaTransportControlsSession const& session)
    : m_session(session) {
}

std::vector<std::uint8_t> MediaSessionWrapper::readStream(
    IRandomAccessStream const& stream) {
    const auto size = stream.Size();
    if (size == 0 || size > 32ull * 1024ull * 1024ull) {
        return {};
    }

    auto input = stream.GetInputStreamAt(0);
    auto reader = DataReader(input);
    const auto wanted = static_cast<uint32_t>(size);

    if (reader.LoadAsync(wanted).get() != wanted) {
        return {};
    }

    std::vector<std::uint8_t> bytes(wanted);
    reader.ReadBytes(bytes);
    return bytes;
}

MediaSnapshot MediaSessionWrapper::snapshot() const {
    MediaSnapshot result;

    if (!m_session) {
        return result;
    }

    try {
        auto playback = m_session.GetPlaybackInfo();
        if (playback) {
            const auto status = playback.PlaybackStatus();
            result.playing = status == GlobalSystemMediaTransportControlsSessionPlaybackStatus::Playing;
            result.paused = status == GlobalSystemMediaTransportControlsSessionPlaybackStatus::Paused;
        }
    } catch (...) {
    }

    try {
        auto props = m_session.TryGetMediaPropertiesAsync().get();
        if (props) {
            result.title = props.Title().c_str();
            result.artist = props.Artist().c_str();
            result.album = props.AlbumTitle().c_str();

            auto thumbnailStreamRef = props.Thumbnail();
            if (thumbnailStreamRef) {
                auto stream = thumbnailStreamRef.OpenReadAsync().get();
                if (stream) {
                    result.artworkPng = readStream(stream);
                }
            }
        }
    } catch (...) {
    }

    try {
        auto timeline = m_session.GetTimelineProperties();
        if (timeline) {
            if (result.playing) {
                auto now = winrt::clock::now();
                auto elapsed = now - timeline.LastUpdatedTime();
                auto position = timeline.Position() + elapsed;
                result.positionSeconds = std::chrono::duration_cast<std::chrono::seconds>(position).count();
            } else {
                result.positionSeconds = std::chrono::duration_cast<std::chrono::seconds>(timeline.Position()).count();
            }

            result.durationSeconds = std::chrono::duration_cast<std::chrono::seconds>(
                timeline.EndTime() - timeline.StartTime()
            ).count();

            if (result.durationSeconds < 0) {
                result.durationSeconds = 0;
            }
        }
    } catch (...) {
    }

    try {
        result.appName = friendlyAppName(std::wstring(m_session.SourceAppUserModelId().c_str()));
    } catch (...) {
    }

    return result;
}

std::wstring MediaSessionWrapper::friendlyAppName(std::wstring value) {
    if (value.empty()) {
        return L"";
    }

    const auto l = lower(value);

    if (l.find(L"spotify") != std::wstring::npos) return L"Spotify";
    if (l.find(L"chrome") != std::wstring::npos) return L"Chrome";
    if (l.find(L"msedge") != std::wstring::npos ||
        l.find(L"microsoft.microsoftedge") != std::wstring::npos ||
        l.find(L"edge") != std::wstring::npos) return L"Microsoft Edge";
    if (l.find(L"vlc") != std::wstring::npos) return L"VLC";
    if (l.find(L"yandex") != std::wstring::npos) return L"Yandex Music";
    if (l.find(L"telegram") != std::wstring::npos) return L"Telegram";
    if (l.find(L"ayugram") != std::wstring::npos) return L"AyuGram";
    if (l.find(L"vkontakte") != std::wstring::npos || l == L"vk") return L"VK";

    return value;
}

bool MediaSessionWrapper::play() const {
    try { return m_session && m_session.TryPlayAsync().get(); }
    catch (...) { return false; }
}

bool MediaSessionWrapper::pause() const {
    try { return m_session && m_session.TryPauseAsync().get(); }
    catch (...) { return false; }
}

bool MediaSessionWrapper::next() const {
    try { return m_session && m_session.TrySkipNextAsync().get(); }
    catch (...) { return false; }
}

bool MediaSessionWrapper::previous() const {
    try { return m_session && m_session.TrySkipPreviousAsync().get(); }
    catch (...) { return false; }
}

bool MediaSessionWrapper::togglePlayPause() const {
    try { return m_session && m_session.TryTogglePlayPauseAsync().get(); }
    catch (...) { return false; }
}

bool MediaSessionWrapper::stop() const {
    try { return m_session && m_session.TryStopAsync().get(); }
    catch (...) { return false; }
}

bool MediaSessionWrapper::seek(std::int64_t positionSeconds) const {
    if (positionSeconds < 0) {
        positionSeconds = 0;
    }

    try {
        const auto ticks = positionSeconds * 10'000'000LL;
        return m_session && m_session.TryChangePlaybackPositionAsync(ticks).get();
    } catch (...) {
        return false;
    }
}

}
