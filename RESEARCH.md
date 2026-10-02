# Smusic research notes

Research was performed against the repositories and Android documentation below on 2026-10-03.

| Source | Signals | License | Smusic use |
| --- | --- | --- | --- |
| [Now in Android](https://github.com/android/nowinandroid) | 21k+ stars; current Kotlin/Compose reference; layered repositories, StateFlow, DI/test seams | Apache-2.0 | Adapt the UI/data separation and state-driven screen model; no source copied. |
| [AndroidX Media3](https://github.com/androidx/media) | Official media playback and offline-download primitives | Apache-2.0 | Use Media3 ExoPlayer and preserve a future MediaSession/DownloadService integration seam. |
| [NewPipe](https://github.com/TeamNewPipe/NewPipe) | 39k+ stars; provider/extractor separation and explicit source support | GPL-3.0 | Use the provider-registry idea only; do not copy GPL code into this project. |
| [Seal](https://github.com/Junkfood02/Seal) | 29k+ stars; downloader UX and yt-dlp-based source strategy | GPL-3.0 | Inform download-progress and format-selection UX; no code copied. |
| [ViMusic](https://github.com/vfsfitvnm/ViMusic) | 9k+ stars; offline-first music-library direction | GPL-3.0 | Inform library/player flow; no code copied. |
| [Compose Samples](https://github.com/android/compose-samples) | 23k+ stars; official Compose patterns | Apache-2.0 | Validate Compose component patterns and theming choices. |

## Functional architecture

`UI -> SmusicViewModel -> MediaRepository -> MediaProviderRegistry` remains the application boundary. `MediaRepository` enriches direct URLs through a HEAD probe, stores records in `MediaDatabase`, and schedules `MediaDownloadWorker`. The worker uses `DirectMediaDownloader` for real HTTP bytes and foreground WorkManager progress. Completed file paths are persisted and handed to Media3 ExoPlayer by the player screen.

## Product boundary

Smusic supports direct HTTPS media URLs when the server makes the media available. It does not bypass DRM, authentication, private-content controls, or platform download restrictions. Providers for platforms such as YouTube, TikTok, or Spotify require an authorized API/SDK and are not claimed as implemented here.
