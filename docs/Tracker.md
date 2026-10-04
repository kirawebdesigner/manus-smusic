# Task Board & Progress Tracker (Tracker) — Smusic v0.2.0

## 1. Current Sprint Focus
**Objective:** Smusic v0.2.0 end-to-end delivery is complete and independently verified (`test` / `assembleDebug` / `lint` all green, 71 unit tests). Phase 6 now adds source-neutral discovery and duplicate-safe playlist foundations; streaming-platform adapters remain explicitly gated by dependency, credential, and reliability review.

---

## 2. Task Board

### 2.1 Documentation & Architecture (8 MD Deliverables)
- [x] 1. `PRD.md` — Product Requirement Document
- [x] 2. `TechSpec.md` — Technical Specification Document (updated: storage layout, ProviderError taxonomy, retry/recovery, playback, settings)
- [x] 3. `AppFlow.md` — User Journey & Architecture Flow
- [x] 4. `Design.md` — Design System & UI/UX Guidelines
- [x] 5. `Schema.md` — Database Schema & Relations (rewritten to match the actual v2 DDL + migrations)
- [x] 6. `ImplementationPlan.md` — Phased Roadmap (P4.2 complete; P5 delivery phase added)
- [x] 7. `Tracker.md` — The Task Board (this file, updated to post-Phase-10 state)
- [x] 8. `Rules.md` — AI Coding Guardrails

### 2.2 Core Engine & Domain Layer
- [x] Create `MediaInfo`, `MediaFormat`, `DownloadJob`, `JobState` domain models
- [x] Implement `MediaProvider` interface & `ProviderRegistry`
- [x] Implement `DirectUrlProvider` with HEAD probe → ranged-GET fallback, filename derivation, error taxonomy
- [x] Implement `SpotifyMetadataProvider` using unauthenticated oEmbed (metadata-only)
- [x] Implement `DownloadEngine` with HTTP Range resume, manual redirects, transient/permanent error split
- [x] Implement `StorageManager` with `downloads/audio|video|other`, traversal-proof sanitization, `.part` staging
- [x] Implement `MediaProcessor` seam for post-processing
- [x] Implement `DownloadManager` orchestrator with recovery, event-driven refresh, concurrency limiter

### 2.3 Data Layer & Persistence
- [x] Implement `SmusicDatabase` with SQLiteOpenHelper, WAL, and job/library tables
- [x] Implement `DatabaseSchema` (pure-Kotlin DDL + additive v1→v2 migrations, SQLite-driver tested)
- [x] Implement `MediaDownloadWorker` with foreground service, concurrency gate, retry policy, integrity + sniffing
- [x] Add stale record cleanup and orphan-file library recovery

### 2.4 Presentation & UI Layer
- [x] Implement `SmusicTheme` dark design system
- [x] Implement `HomeScreen` with URL probe, format picker & storage selectors, `Failed` analysis state
- [x] Implement `DownloadsScreen` with sectioned queue (active/queued/completed/failed), share, retry, delete
- [x] Implement `LibraryScreen` with search (title/artist/album/playlist/filename), favorites, play counts
- [x] Implement `SmusicPlaybackService` with AndroidX Media3 ExoPlayer
- [x] Implement `PlayerController`, `PlayerScreen`, and `MiniPlayer` with persisted resume/shuffle/repeat
- [x] Implement `SettingsScreen` (downloads, playback, storage, about) bound to `AppSettings`

### 2.5 Release & Deployment
- [x] Deprecate/remove conflicting v0.1.0 legacy data files (superseded by `domain/` + `data/` layout)
- [x] Version bump `versionCode = 2`, `versionName = "0.2.0"` in `app/build.gradle.kts`
- [x] Verify `.gitignore` rules (`local.properties`, build caches incl. `.kotlin/`)
- [x] Verify `./gradlew test` (71 tests), `./gradlew assembleDebug` (APK), `./gradlew lint` (0 errors)
- [x] Stage and commit all files to git
- [ ] Push to `origin/main` on GitHub
- [ ] Publish GitHub Release `v0.2.0` with changelog and notes

### 2.6 Discovery & Playlist Safety (Phase 6)
- [x] Add `DiscoveryQuery`, `DiscoveryItem`, `PlaylistEntry`, `DiscoveryResult`, and `DiscoveryProvider` contracts.
- [x] Add version-aware `DuplicateResolver` with ISRC/source ID/canonical URL/metadata matching.
- [x] Add five duplicate and variant unit tests.
- [ ] Add a production YouTube provider with real search and playlist expansion.
- [ ] Add Spotify Web API PKCE integration for full playlist track metadata.
- [ ] Add playlist/source-reference schema migration and selection UI.

---

## 3. Bug Tracking & Known Issues Log

| ID | Severity | Description | Status | Resolution / Action |
|---|---|---|---|---|
| **BUG-001** | Medium | Duplicate class/type conflict between old `data/MediaModels.kt` and new `domain/model/*` | Resolved | Legacy root files removed; `DownloadEngineTest` replaced `DirectMediaDownloaderTest` |
| **BUG-002** | Low | Gradle/Kotlin daemon OOM on low-memory machines ("daemon disappeared unexpectedly") | Resolved | `gradle.properties` pins `-Xmx1024m` + in-process Kotlin compilation |
| **BUG-003** | Low | Ensure `local.properties` does not get checked into git repository | Verified | Ignore verified in `.gitignore` |
| **BUG-004** | Low | JUnit rejects non-void test methods (`InvalidTestClassError`) | Resolved | `DownloadConcurrencyTest` bodies wrapped in `runBlocking` instead of expression form |

## 4. Current honest limitations

- Direct HTTP(S) downloads are fully implemented.
- Spotify oEmbed metadata is available; protected Spotify audio is not downloadable.
- YouTube search, playlist expansion, and streaming-platform download are not yet enabled.
- No FFmpeg binary is bundled.
