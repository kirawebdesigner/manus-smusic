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
  - Build real-time 1000ms sliding-window speed and ETA calculator.
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
- [ ] **P4.2 Build Verification & Cleanup:**
  - Clean up obsolete v0.1.0 data classes to prevent compiler ambiguities.
  - Verify Gradle configuration and version bump to `0.2.0`.
  - Execute build tasks (`assembleDebug` or `compileDebugKotlin`).
- [ ] **P4.3 Git Commit & GitHub Release:**
  - Stage all architecture improvements and docs.
  - Push branch to `origin/main`.
  - Create GitHub release `v0.2.0` with release notes and changelog.
