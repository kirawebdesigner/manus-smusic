# Smusic v0.2.0 — Ten-Phase Delivery Report

Status: **Implemented and verified.** `./gradlew test` 71/71 · `./gradlew assembleDebug` APK · `./gradlew lint` 0 errors.

---

## Phase 1 — Foundation & Data Architecture (baseline, pre-existing)
- SQLite persistence via `SQLiteOpenHelper` (WAL) with `download_jobs` and `library_media`.
- `StorageManager` with app-controlled storage, duplicate suffixing, `.part` staging.
- `DownloadEngine` streaming bytes with real progress; replaced the obsolete `data/` package (BUG-001 resolved).

## Phase 2 — Provider Subsystem
- `domain/provider/ProviderError.kt`: sealed taxonomy — `InvalidUrl`, `NetworkUnavailable`, `MediaUnavailable`, `ServerRejected`, `ProtectedContent`, `UnknownMediaType`, `AnalysisFailed` — every analysis failure is categorized, never a raw exception.
- `domain/model/DownloadRequest.kt`: typed resolution output (`url`, `mediaType`, `mimeType`, `container`, `suggestedFilename`, `providerId`, size).
- `DirectUrlProvider` rewritten: URI validation (http/https only) → HEAD probe with ranged `GET bytes=0-0` fallback → manual redirect tracing (5-hop cap, loop/malformed/non-http rejection) → media-type classification from MIME + extension → filename derivation from Content-Disposition (RFC 6266/5987), URL path, or query parameter, sanitized against path traversal (`../../../etc/passwd.mp3` → `passwd.mp3`) → one honest `MediaFormat` (codec/resolution/bitrate stay null unless the server reported them); `Accept-Ranges`/`Content-Range` detection.
- `MediaProvider.resolveMedia` default method; `ProviderRegistry` catches provider exceptions into `AnalysisResult.Failed` (rethrows cancellation) and passes `resolveMedia` through.
- 15 MockWebServer tests: probing, fallback, filename rules, hostile names, redirect, HTTP error mapping, network failure.

## Phase 3 — Storage & Database Schema v2
- `StorageManager` rewritten: `downloads/audio|video|other` under `getExternalFilesDir(null)` (no storage permission), canonical-path containment guard, `(1)`/`(2)` duplicate suffixing, `.part` cleanup returning counts, `StorageUsage` aggregation, `scanMediaFiles()` orphan discovery, pure unit-testable helpers.
- `data/database/DatabaseSchema.kt`: pure-Kotlin versioned DDL — v1 tables byte-for-byte + additive v1→v2 `ALTER` statements; fresh installs run all, upgrades run the delta.
- `SmusicDatabase` on `smusic_v2.db`: tolerant `onUpgrade` (duplicate-column safe, never wipes data), `filename`/`temp_path`/`completed_at` on jobs, `last_played`/`play_count` on library, `CONFLICT_IGNORE` library inserts keyed by unique `local_path`, `getJobsInStates`, `incrementRetryCount`, `recordPlay`, `cleanStaleRecords() → Int`, search including `local_path`.

## Phase 4 — Download Pipeline & Worker
- `domain/engine/DownloadException.kt`: `PermanentDownloadException` (404/410/401/403, empty body, 416 mismatch, redirect failures) vs `TransientDownloadException` (408/429/5xx) driving retry policy.
- `domain/engine/DownloadConcurrency.kt`: semaphore limiter (1–3 slots); each worker releases the exact gate it acquired, so reconfiguring never strands permits.
- `domain/manager/DownloadEvents.kt`: revision flow for throttled, reactive UI refresh without DB polling.
- `MediaDownloadWorker` rewritten: acquires the gate, resolves the target with the provider filename, tracks `tempPath`, progress→DB+events, on completion validates integrity → enforces a truthful extension via magic-byte sniffing → reads duration → inserts the `LibraryItem` → MediaScanner → `COMPLETED`+`completedAt`; cancellation marks `CANCELLED` only from cancellable states; auto-retry capped at 3 attempts; permanent errors fail fast with a user-facing message.
- `DownloadManager` rewritten: launch-time recovery of interrupted jobs (via `getWorkInfosByTag`, capped), library rebuild with orphan scan, event-driven refresh, `ExistingWorkPolicy.KEEP` + `UNMETERED` constraint when Wi-Fi-only, idempotent terminal-state actions, staging cleanup on remove.

## Phase 5 — Library UX
- `ui/DownloadUiModel.kt`: pure state→section mapping (active/queued/completed/failed; every `JobState` lands in exactly one section — tested).
- `DownloadsScreen` rebuilt: sectioned list with animated progress, speed (EMA-smoothed), ETA, byte counts, cancel/retry/share (FileProvider)/delete actions, differentiated cards.
- `domain/library/LibrarySearch.kt`: case-insensitive in-memory filter over title, creator, album, playlist, and filename.
- `LibraryScreen`: loading state, search-empty vs library-empty states, duration and play-count rows, per-item animations.
- `HomeScreen`: `AnalysisResult.Failed` branch (error title/message + Try again), range-support hint, single honest format row.

## Phase 6 — Settings
- `data/settings/AppSettings.kt`: SharedPreferences exposed as `StateFlow`s — default category, Wi-Fi-only, max concurrent (1–3), auto-retry, autoplay-next, resume-playback, shuffle/repeat defaults, last media id/position.
- `SettingsScreen`: Downloads, Playback, Storage (real disk usage, clear temporary files, clear download history — files kept), and About (version, Apache-2.0, privacy, no-circumvention statement) — all bound to settings flows.

## Phase 7 — Playback
- `domain/player/PlayerController.kt`: connects a `MediaController` to `SmusicPlaybackService` via `SessionToken`; single `PlayerState` flow (playing/buffering, item metadata, position/duration, shuffle/repeat, previous/next availability, error); 500 ms position polling only while active; persisted resume position, shuffle/repeat defaults.
- `SmusicPlaybackService` (pre-existing Media3 `MediaSessionService`) now driven end-to-end: playback survives navigation, system media notification via Media3 session.

## Phase 8 — Player UI
- `ui/screens/PlayerScreen.kt`: full-screen player — seek bar with drag state, remaining time, shuffle/prev/play-pause/next/repeat with disabled states, buffering spinner, error banner, empty state.
- `ui/components/MiniPlayer.kt`: compact docked mini player (artwork, title/artist, play/pause, progress) shown whenever playback is active; tap opens the full player.
- `MainActivity` reworked: player as a tab-level destination with slide transitions, mini player animated above the bottom bar, snackbar surfacing of playback errors, POST_NOTIFICATIONS runtime request on Android 13+.

## Phase 9 — Support Infrastructure
- `domain/processor/MediaSniffer.kt`: magic-byte detection (mp3/flac/ogg/wav/mp4/m4a/mkv/webm) so extensionless downloads get truthful extensions Media3 can route.
- `domain/processor/MediaMetadataReader.kt`: defensive duration reading (0 on failure, never crashes the worker).
- `domain/util/Formatters.kt`: bytes/speed/ETA/time helpers shared by screens, worker, and tests.
- Manifest/resources: `FileProvider` + `file_paths.xml` for sharing, `dataSync` foreground-service merge, minimal permission set (INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE+DATA_SYNC+MEDIA_PLAYBACK, POST_NOTIFICATIONS).

## Phase 10 — Tests, Verification & Docs
- 71 unit tests across 9 suites (`app/src/test/java/com/smusic/app/...`):
  `DownloadEngineTest` (12, pre-existing baseline preserved) · `DirectUrlProviderTest` (15, real HTTP via MockWebServer) · `DatabaseSchemaTest` (5, exact on-device DDL/migrations executed against a real SQLite driver — fresh install, v1→v2 preserves rows with honest defaults, additive-only, version guards) · `StorageManagerTest` (8, sanitization/traversal containment) · `LibrarySearchTest` (6) · `DownloadUiModelTest` (6, all 9 states → exactly one section) · `FormattersTest` (6, pinned locale) · `MediaSnifferTest` (10, real temp files) · `DownloadConcurrencyTest` (3, blocking/clamping/gate-swap).
- Build verification: `./gradlew test` (debug + release) ✅ · `./gradlew assembleDebug` ✅ · `./gradlew lint` ✅ 0 errors, 9 advisory warnings (8 dependency-version notices, 1 exported-`MediaSessionService` notice).
- APK: `app/build/outputs/apk/debug/app-debug.apk` — 21,700,314 bytes, `com.smusic.app`, versionCode 2, versionName 0.2.0, minSdk 26, target/compile 35 (verified via `aapt dump badging`).
- Docs updated: README, `docs/Schema.md` (rewritten to actual DDL + migration table), `docs/TechSpec.md`, `docs/ImplementationPlan.md`, `OPEN_SOURCE.md` (sqlite-jdbc test dep), `docs/Tracker.md` (this release's board), and this report.

---

## Honest Limitations
- Direct HTTP(S) media URLs only. No YouTube/TikTok/streaming-platform downloads; Spotify links yield public oEmbed metadata only; **no DRM or access-control circumvention, ever.**
- No FFmpeg transcoding — `MediaProcessor` remains the seam; one format per direct URL.
- Duration and play counts are best-effort (0 when the retriever cannot parse).
- Release signing, CI pipelines, and the GitHub release are not part of this pass.

## Release Status
- [x] All ten phases implemented and verified
- [x] Committed on `main`
- [ ] Push to `origin` and publish the `v0.2.0` GitHub release (per release checklist)

## Post-v0.2.0 Phase 6 — Discovery foundation (in progress)

- Added source-neutral discovery contracts for text searches, pasted URLs, playlist entries, provider capability boundaries, and explicit result statuses.
- Added deterministic duplicate identity matching across ISRC, source IDs, canonical URLs, normalized creator/title, duration tolerance, and version markers.
- Added tests for repeated playlist entries, cross-source matches, different versions, tracking-parameter cleanup, and uncertain duration review.
- Added `docs/SourceIntegrationResearch.md` documenting the YouTube/Spotify adapter decision and licensing review.
- Not yet enabled: YouTube search/playlist extraction or Spotify full playlist enumeration. Those require a production-ready adapter and, for Spotify Web API metadata, a user-configured PKCE client ID.
