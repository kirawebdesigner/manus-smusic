# Source integration research — post-v0.2.0

## Decision

Smusic keeps the clean `MediaProvider`/`DownloadManager` architecture from `5dac4b0`. Streaming-platform providers are added as separate adapters; YTDLnis source is not copied or embedded.

## YouTube options evaluated

- [YTDLnis](https://github.com/deniscerri/ytdlnis) — GPL-3.0 Android application. It is an architectural reference only.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) — GPL-3.0 Android wrapper. It is not added because it would require a deliberate GPL-compliance decision for Smusic.
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — the repository/source distribution is Unlicense, but release executables can contain third-party code under other licenses. Directly packaging an executable also requires a reviewed update, verification, native-runtime, and attribution strategy.
- [lizz-yt-dlp](https://github.com/Lizzergas/lizz-yt-dlp) — Apache-2.0, but currently alpha, one-star, YouTube-only, audio-download oriented, with incomplete signature/anti-bot handling. It does not yet provide the search/playlist-resolution contract required by Smusic, so it is not silently adopted as a production dependency.

### Current YouTube status

No YouTube search, playlist expansion, or downloader is claimed as implemented. This is intentional: a provider must expose real metadata/formats and be reliable enough for WorkManager, rather than showing a fake format list or silently failing.

## Spotify options evaluated

- [Spotify oEmbed](https://developer.spotify.com/documentation/embeds/reference/oembed) — public metadata for supported Spotify entities; no protected audio download.
- [Spotify Web API](https://developer.spotify.com/documentation/web-api) — the supported route for full track/album/playlist metadata. A native Android app should use [Authorization Code with PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow), which requires a registered Spotify client ID and user authorization.
- [spotDL](https://github.com/spotDL/spotify-downloader) — MIT-licensed Python project that resolves Spotify metadata to YouTube sources. Smusic does not embed its Python source or claim direct Spotify audio downloading.

### Current Spotify status

Public oEmbed metadata is supported. Full playlist track enumeration and source matching require a configured Spotify Web API client ID and PKCE flow; no credentials are stored in source code.

## Adapter acceptance criteria

A future provider can be enabled only when it has:

1. A reviewed license and attribution record.
2. Real `analyze`, search, playlist, and format behavior for the capabilities it advertises.
3. Explicit unsupported/protected-content errors.
4. WorkManager-compatible cancellation and progress reporting.
5. Unit and controlled integration tests.
6. No user credentials, cookies, or private URLs in logs.

The new `domain.discovery` contracts and duplicate resolver provide the source-neutral layer for this work.
