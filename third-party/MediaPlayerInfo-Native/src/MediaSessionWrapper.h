#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.Control.h>
#include <winrt/Windows.Storage.Streams.h>

namespace media_player_info {

struct MediaSnapshot {
    std::wstring title;
    std::wstring artist;
    std::wstring album;
    std::wstring appName;
    std::vector<std::uint8_t> artworkPng;

    bool playing = false;
    bool paused = false;
    std::int64_t positionSeconds = 0;
    std::int64_t durationSeconds = 0;
};

class MediaSessionWrapper {
public:
    explicit MediaSessionWrapper(
        winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSession const& session);

    MediaSnapshot snapshot() const;

    bool play() const;
    bool pause() const;
    bool next() const;
    bool previous() const;
    bool togglePlayPause() const;
    bool stop() const;
    bool seek(std::int64_t positionSeconds) const;

private:
    static std::vector<std::uint8_t> readStream(
        winrt::Windows::Storage::Streams::IRandomAccessStream const& stream);

    static std::wstring friendlyAppName(std::wstring value);

    winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSession m_session{nullptr};
};

}
