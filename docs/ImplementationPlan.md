# Implementation Plan (Phased Roadmap) — Smusic v0.2.0

## 1. Overview
This roadmap outlines the phased development and upgrade of Smusic from a basic direct-downloader to an enterprise-grade, modular media suite inspired by **YTDLnis** and **spotDL**.

---

## 2. Phases & Milestones

### Phase 1: Foundation & Data Architecture
- [x] **P1.1 Data Layer Re-architecture:**
  - Create SQLite database schema (`download_jobs`, `library_media`) with WAL.
  - Implement CRUD queries and cursor mappers.
  - Implement stale record cleanup to synchronize disk and database.
- [x] **P1.2 Scoped Storage Manager:**
  - Implement Android Scoped Storage handler targeting `Music/Smusic` and `Movies/Smusic`.
  - Add collision prevention with automatic suffixing (`(1)`, `(2)`).
  - Add `.part` staging file lifecycle and atomic rename logic.

### Phase 2: Core Engine & Provider Subsystem
- [x] **P2.1 Modular Provider System:**
  - Create `MediaProvider` interface with capability flags (`DIRECT_STREAM`, `METADATA_ONLY`, `FORMAT_SELECTION`).
  - Implement `DirectUrlProvider` supporting HTTP HEAD probe, size detection, and format synthesis.
  - Implement `SpotifyMetadataProvider` parsing public oEmbed endpoints for metadata without unauthorized streams.
  - Build `ProviderRegistry` with URL regex matching and fallback handling.
- [x] **P2.2 Resumable HTTP Download Engine:**
  - Build native `DownloadEngine` supporting `Range: bytes=X-` HTTP requests.
  - Implement redirect tracing across 301, 302, 307, and 308 response codes.
  - Build real-time ~250ms rolling-interval speed and ETA calculator.
  - Ensure zero fake/simulated progress.
- [x] **P2.3 Media Processing Seam:**
  - Define `MediaProcessor` interface for post-processing (FFmpeg integration seam).
  - Implement `DefaultMediaProcessor` handling metadata tagging and safe command preparation.

### Phase 3: Background Worker & UI Overhaul
- [x] **P3.1 WorkManager Integration:**
  - Implement `MediaDownloadWorker` running as an Android Foreground Service.
  - Create system notifications with real-time download percentage and speed.
  - Add MediaScanner integration for instant gallery/audio recognition.
- [x] **P3.2 Jetpack Compose UI Refresh:**
  - Establish `SmusicTheme` dark palette (`Canvas`, `Panel`, `Accent`, `Ink`).
  - Rebuild `HomeScreen` with animated URL analyzer card, format selectors, and storage pickers.
  - Rebuild `DownloadsScreen` with tabbed queue states (`Active`, `Completed`, `Failed`).
  - Rebuild `LibraryScreen` with media search, filtering, audio playback launcher, and sharing.
  - Integrate `SmusicPlaybackService` with AndroidX Media3 ExoPlayer for background audio.

### Phase 4: Quality Assurance, Licensing & GitHub Release
- [x] **P4.1 Open-Source Licensing Compliance:**
  - Create `OPEN_SOURCE.md` confirming zero GPL code contamination from YTDLnis and clean-room implementation.
  - Ensure Apache-2.0 / MIT compatibility.
- [x] **P4.2 Build Verification & Cleanup:**
  - Clean up obsolete v0.1.0 data classes to prevent compiler ambiguities.
  - Verify Gradle configuration and version bump to `0.2.0` (`versionCode = 2`).
  - Execute build tasks: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew lint` — all green (71 unit tests, 0 lint errors).
- [ ] **P4.3 Git Commit & GitHub Release:**
  - Stage all architecture improvements and docs.
  - Push branch to `origin/main`.
  - Create GitHub release `v0.2.0` with release notes and changelog.

### Phase 5: v0.2.0 End-to-End Delivery (URL → Library → Player)
- [x] **P5.1 Provider hardening:** categorized `ProviderError` taxonomy, HEAD probe with ranged-GET fallback, manual redirect tracing (loop/malformed/too-many), Content-Disposition/query filename derivation with traversal sanitization, honest single-format analysis.
- [x] **P5.2 Storage layer:** `downloads/audio|video|other` layout, canonical-path containment checks, duplicate suffixing, `.part` cleanup, storage-usage accounting, orphan-file media scan.
- [x] **P5.3 Schema v2:** `DatabaseSchema` with byte-for-byte v1 baseline + additive v1→v2 migrations (filename/temp/completed tracking, last_played/play_count), verified against a real SQLite driver.
- [x] **P5.4 Download pipeline:** WorkManager persistent queue, foreground progress notification, concurrency limiter (1–3), transient/permanent error split, auto-retry, crash/interruption recovery, magic-byte extension enforcement, MediaScanner registration.
- [x] **P5.5 Library UX:** sectioned Downloads screen (active/queued/completed/failed), library search over title/artist/album/playlist/filename, favorites, play counts, share via FileProvider, stale-row pruning.
- [x] **P5.6 Playback:** `PlayerController` + Media3 `MediaSessionService`, full-screen player, mini player, persisted position/shuffle/repeat and resume-on-play.
- [x] **P5.7 Settings:** destination, Wi-Fi-only, concurrency, auto-retry, playback defaults, storage usage and cleanup actions, about section.
- [x] **P5.8 Test suite:** engine (12), provider probing (15), schema/migration (5), storage (8), library search (6), UI model (6), formatters (6), media sniffer (10), concurrency (3) — 71 tests via `./gradlew test`.
- [ ] **P5.9 Release delivery:** commit via the Freebuff Changes panel, push, and publish the `v0.2.0` GitHub release (not part of this pass).

### Phase 6: Discovery, Playlists & Duplicate Safety (in progress)
- [x] **P6.1 Discovery contracts:** source-neutral query, result, playlist-entry, status, and provider interfaces in `domain/discovery`.
- [x] **P6.2 Duplicate identity:** ISRC/source ID/canonical URL/normalized metadata matching with version preservation and review states.
- [x] **P6.3 Tests:** repeated playlist entries, cross-source ISRC matches, version variants, URL tracking cleanup, and uncertain-duration review.
- [ ] **P6.4 YouTube adapter:** add only after a reviewed Android-compatible dependency or verified provider runtime supports real single-item, search, and playlist behavior.
- [ ] **P6.5 Spotify Web API:** add PKCE-authenticated track/album/playlist metadata using a user-configured client ID; keep audio acquisition separate.
- [ ] **P6.6 Queue/database wiring:** persist source references, playlists, playlist positions, and duplicate decisions with additive migrations.
- [ ] **P6.7 Selection UI:** display search/playlist results, match confidence, skipped duplicates, unresolved items, and explicit user confirmation.
