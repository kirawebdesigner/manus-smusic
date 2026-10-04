# Technical Specification (TechSpec) — Smusic v0.2.0

## 1. System Architecture Overview
Smusic adheres to clean architecture principles with unidirectional data flow (UDF) powered by Kotlin Coroutines and StateFlow. The system is divided into Domain, Data, and Presentation layers.

```
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│        Jetpack Compose UI (Material 3 + Canvas)        │
│       SmusicViewModel + StateFlow + Navigation         │
└──────────────────────────┬─────────────────────────────┘
                           │ Observes State / Dispatches Actions
┌──────────────────────────▼─────────────────────────────┐
│                      Domain Layer                      │
│  - DownloadManager (Orchestrator)                      │
│  - ProviderRegistry (DirectUrlProvider, SpotifyMeta)   │
│  - DownloadEngine (Resumable HTTP Streamer)            │
│  - StorageManager (Scoped Storage / SAF / Mime types)  │
│  - MediaProcessor (Seam for FFmpeg / Audio Extract)    │
└──────────────────────────┬─────────────────────────────┘
                           │ Coordinates
┌──────────────────────────▼─────────────────────────────┐
│                       Data Layer                       │
│  - SmusicDatabase (SQLiteOpenHelper, Jobs & Library)   │
│  - MediaDownloadWorker (AndroidX CoroutineWorker)       │
│  - SmusicPlaybackService (Media3 MediaSessionService)  │
└────────────────────────────────────────────────────────┘
```

---

## 2. Technology Stack

| Layer | Technology | Specification / Version |
|---|---|---|
| **Language & Platform** | Kotlin & Android SDK | Kotlin 2.0.21 / Min SDK 26 (Android 8.0) / Compile & Target SDK 35 |
| **UI Framework** | Jetpack Compose & Material 3 | Compose BOM 2024.12.01, Material3 1.3.1 |
| **Concurrency** | Kotlin Coroutines & Flow | Coroutines Core & Android 1.8.0 |
| **Persistence** | SQLite / Android SQLiteOpenHelper | Native SQLite with WAL (Write-Ahead Logging) enabled |
| **Background Tasks** | AndroidX WorkManager | WorkManager Runtime KTX 2.10.0 |
| **Media Playback** | AndroidX Media3 | Media3 ExoPlayer & Media3 Session 1.5.1 |
| **Networking** | Java HTTP / HttpsURLConnection | Zero external bulky HTTP dependencies; native streaming with connection pools |
| **Build System** | Gradle Kotlin DSL | Gradle 8.10.2 with Android Gradle Plugin 8.7.3 |

---

## 3. Security, Permissions & Storage Architecture

### 3.1 Permissions Matrix (matches `app/src/main/AndroidManifest.xml`)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Deliberately **not** requested (least-privilege):
- `WAKE_LOCK` — no wake lock is acquired anywhere in the codebase; WorkManager already
  holds the necessary system-level constraints while a foreground worker runs.
- `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` — downloads write to
  app-specific external storage (`getExternalFilesDir`), which requires no storage
  permission on any supported API level.

### 3.2 Scoped Storage Integration
- Layout (all under `getExternalFilesDir(null)` — no storage permission required):

  ```
  downloads/
  ├── audio/    # StorageCategory.MUSIC
  ├── video/    # StorageCategory.VIDEOS
  └── other/    # StorageCategory.OTHER
  ```

- Filenames: server/URL-derived names are sanitized (path separators, traversal
  sequences, reserved/control characters stripped, length capped); canonical-path
  checks guarantee a target can never resolve outside `downloads/`. Duplicate
  names get `(1)`, `(2)` suffixes instead of overwriting.
- Staging files: downloads stream to `<name>.part` beside the target, renamed
  atomically only after integrity validation and extension enforcement.
- Content Scanning: `MediaScannerConnection` registers completed media with Android's MediaStore immediately.
- Settings can clear `*.part` temporary files and clear download history
  (database rows only — media files are kept).

---

## 4. API & Provider Protocol Specifications

### 4.1 Direct URL Provider
- **Detection:** Inspects scheme (`http/https`) and path extensions (`.mp3`, `.mp4`, `.m4a`, `.flac`, `.wav`, `.ogg`, `.webm`, `.aac`).
- **Probe Request (HEAD):**
  - Sends HTTP `HEAD` to inspect `Content-Length`, `Content-Type`, `Accept-Ranges: bytes`, and final redirected URL.
  - Falls back to `GET` with `Range: bytes=0-1` if server forbids `HEAD`.
- **Response Mapping:** Generates `MediaInfo` with extracted title, MIME type, size, and a single honest `MediaFormat`. Codec/resolution/bitrate remain `null` unless the server actually reported them; `supportsRangeRequests` reflects `Accept-Ranges`/`Content-Range` evidence.

### 4.3 Error Taxonomy (`ProviderError`)
Every analysis failure is categorized (never a raw exception or silent failure):

| Variant | Raised when |
|---|---|
| `InvalidUrl` | Malformed URL, missing host, or non-http(s) scheme |
| `NetworkUnavailable` | Connection refused/DNS failure/timeouts while probing |
| `MediaUnavailable` | HTTP 404/410 — the media is gone |
| `ServerRejected` | HTTP 401/403 — access denied by the server |
| `ProtectedContent` | Source is DRM-protected or access-controlled (e.g. Spotify streams) |
| `UnknownMediaType` | Response is not audio/video (e.g. `text/html` page) |
| `AnalysisFailed` | Unexpected provider failure (caught in `ProviderRegistry`, logged) |

`AnalysisResult.Failed(error)` carries the variant to the UI, which renders an
honest title + message and a "Try again" action. HTTP status handling in the
download engine mirrors this: 408/429/5xx → `TransientDownloadException`
(retryable), 404/410/401/403/other → `PermanentDownloadException` (fail fast
with a user-facing message).

### 4.2 Spotify Metadata Provider (spotDL-inspired)
- **Detection:** `open.spotify.com/track/*` or `open.spotify.com/album/*`.
- **Endpoint:** `https://open.spotify.com/oembed?url={URL}`
- **Protocol:** Public unauthenticated oEmbed JSON parsing. Extracts title, artist/author, thumbnail artwork URL, and provider name.
- **Contract:** Returns `AnalysisResult.MetadataOnly` with track metadata to prevent unauthorized streams while enabling structured metadata queries.

### 4.3 Discovery and duplicate contracts (Phase 6)
- `DiscoveryQuery` distinguishes pasted URLs from text searches.
- `DiscoveryProvider` separates search/playlist resolution from media acquisition; a provider must not advertise a capability it cannot execute.
- `DiscoveryItem` carries source ID, provider ID, normalized metadata inputs, duration, ISRC when available, and an explicit `TrackVersion`.
- `PlaylistEntry` preserves playlist position even when an item is unavailable or skipped as a duplicate.
- `DuplicateResolver` checks ISRC, provider/source ID, canonical URL, normalized creator/title, duration tolerance, and version markers in that order.
- Decisions are explicit: `NEW`, `DUPLICATE_EXISTING`, `DUPLICATE_QUEUED`, `DIFFERENT_VERSION`, `NEEDS_REVIEW`, or `UNRESOLVED`.
- Playlist membership must be stored separately from physical library files so the same file can appear in multiple playlists without duplicate downloads.

YouTube search/playlist execution is not enabled in this build. Spotify full playlist enumeration requires a registered Web API client ID and PKCE authorization; oEmbed remains metadata-only.

---

## 5. Download Engine Specification

### 5.1 Resumption & Redirect Protocol
```
1. Check existing target file and `.part` file size.
2. If existingSize > 0, send header: Range: bytes={existingSize}-
3. HTTP 206 → append the range body to the `.part` file.
4. HTTP 200 → the server ignored Range: discard the `.part` file and
   restart the stream from byte 0 (never append a full body to a partial file).
5. HTTP 416 → parse Content-Range: bytes */<total>:
   - .part already holds <total> bytes → verify and finalize (download complete).
   - Otherwise → fail with a descriptive error, preserving the `.part` file
     for a later retry.
6. Redirects (301/302/307/308) are followed **manually** (automatic
   `instanceFollowRedirects` is disabled) so the Range header is re-attached
   on every hop. Relative `Location` targets are resolved against the current
   URL; the chain is capped at 5 hops with loop detection and rejection of
   malformed or non-http(s) targets.
```

### 5.2 Real-time Rate Calculation
- Progress and speed are reported on a rolling interval of **~250 ms** (configurable via the engine's `progressIntervalMs`):
  $$\text{Speed (bytes/sec)} = \frac{\Delta \text{Bytes over interval}}{\Delta \text{Interval (sec)}}$$
- ETA is calculated dynamically based on remaining bytes divided by the latest measured speed.
- The worker smooths the raw rate with an exponential moving average
  (`(raw·3 + smoothed·7) / 10`) before persisting it, so the notification and
  Downloads screen do not jitter.

---

## 6. Background Execution, Retry & Recovery
- **Worker:** `MediaDownloadWorker` (AndroidX `CoroutineWorker`) runs as a
  `dataSync` foreground service with an ongoing progress notification.
- **Concurrency:** a semaphore gate (`DownloadConcurrencyLimiter`) caps
  simultaneous byte-streaming to the configured 1–3 slots; each worker releases
  the exact gate it acquired, so changing the limit never strands permits.
- **Retry policy:** transient failures requeue the job (up to 3 attempts, when
  auto-retry is enabled); permanent failures mark it `FAILED` immediately with a
  user-facing message. Cancellation marks `CANCELLED` only from cancellable states.
- **Recovery:** on launch, jobs stuck in unfinished states are re-queued via
  `getWorkInfosByTag` (capped attempts, then `FAILED`); the library is rebuilt
  from the database plus an orphan-file disk scan.
- **Constraints:** work is enqueued with `ExistingWorkPolicy.KEEP` and a
  `UNMETERED` network constraint when Wi-Fi-only is enabled.
- **Persistence events:** a lightweight revision flow (`DownloadEvents.revision`)
  triggers throttled state refreshes so the UI stays live without polling the DB.

---

## 7. Playback Architecture
- **`SmusicPlaybackService`:** Media3 `MediaSessionService` owning the ExoPlayer
  instance, so playback and the system media notification survive navigation and
  process-level UI teardown.
- **`PlayerController` (domain/player):** connects a `MediaController` via
  `SessionToken` on the main executor, exposes a single `PlayerState` StateFlow
  (playing/buffering, current item, position/duration, shuffle/repeat,
  previous/next availability, error message), polls position at 500 ms only while
  active, and persists the last position/media id for resume. Queue edits map
  `LibraryItem` → `MediaItem` with real local file URIs.
- **UI:** full-screen `PlayerScreen` (seek, shuffle, repeat, buffering/error
  states) plus a `MiniPlayer` docked above the bottom navigation; both read the
  same controller state.

---

## 8. Settings (`data/settings/AppSettings`)
SharedPreferences (`smusic_settings`) exposed as `StateFlow`s: default
destination category, Wi-Fi-only, max concurrent downloads (1–3), auto-retry,
autoplay-next, resume-playback, shuffle/repeat defaults, and last playback
position. All UI controls on the Settings screen are bound to these flows.
