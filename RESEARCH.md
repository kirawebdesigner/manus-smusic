# Smusic research notes

Research was performed against the repositories and Android documentation below on 2026-10-03.

| Source | Signals | License | Smusic use |
| --- | --- | --- | --- |
| [Now in Android](https://github.com/android/nowinandroid) | 21k+ stars; current Kotlin/Compose reference; layered repositories, StateFlow, DI/test seams | Apache-2.0 | Adapt the UI/data separation and state-driven screen model; no source copied. |
| [AndroidX Media3](https://github.com/androidx/media) | Official media playback and offline-download primitives | Apache-2.0 | Use Media3 dependency and preserve a future DownloadService/DownloadManager integration seam. |
| [NewPipe](https://github.com/TeamNewPipe/NewPipe) | 39k+ stars; provider/extractor separation and explicit source support | GPL-3.0 | Use the provider-registry idea only; do not copy GPL code into this project. |
| [Seal](https://github.com/Junkfood02/Seal) | 29k+ stars; downloader UX and yt-dlp-based source strategy | GPL-3.0 | Inform download-progress and format-selection UX; no code copied. |
| [ViMusic](https://github.com/vfsfitvnm/ViMusic) | 9k+ stars; offline-first music-library direction | GPL-3.0 | Inform library/player flow; no code copied. |
| [Compose Samples](https://github.com/android/compose-samples) | 23k+ stars; official Compose patterns | Apache-2.0 | Validate Compose component patterns and theming choices. |

## Product boundary

Smusic currently analyzes pasted URLs through a provider interface. The included implementation safely recognizes direct media URLs and returns a clear unsupported-source result for platform URLs that need a permitted official integration. It does **not** bypass DRM, authentication, or platform download restrictions. Media3 is the intended playback/download foundation for a later provider implementation that is authorized for a given source.

## Architecture

`UI -> SmusicViewModel -> MediaRepository -> MediaProviderRegistry` is the first vertical slice. The in-memory repository is deliberately replaceable with Room/DataStore and a Media3 DownloadService without coupling Compose to provider code.
