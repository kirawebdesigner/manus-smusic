# Task Board & Progress Tracker (Tracker) — Smusic v0.2.0

## 1. Current Sprint Focus
**Objective:** Finalize Smusic v0.2.0 modular architecture release, prune deprecated v0.1.0 data classes, verify build, commit, push to GitHub, and create release `v0.2.0`.

---

## 2. Task Board

### 2.1 Documentation & Architecture (8 MD Deliverables)
- [x] 1. `PRD.md` — Product Requirement Document
- [x] 2. `TechSpec.md` — Technical Specification Document
- [x] 3. `AppFlow.md` — User Journey & Architecture Flow
- [x] 4. `Design.md` — Design System & UI/UX Guidelines
- [x] 5. `Schema.md` — Database Schema & Relations
- [x] 6. `ImplementationPlan.md` — Phased Roadmap
- [x] 7. `Tracker.md` — The Task Board
- [/] 8. `Rules.md` — AI Coding Guardrails

### 2.2 Core Engine & Domain Layer
- [x] Create `MediaInfo`, `MediaFormat`, `DownloadJob`, `JobState` domain models
- [x] Implement `MediaProvider` interface & `ProviderRegistry`
- [x] Implement `DirectUrlProvider` with HEAD probes & MIME detection
- [x] Implement `SpotifyMetadataProvider` using unauthenticated oEmbed
- [x] Implement `DownloadEngine` with HTTP Range resume & speed measurement
- [x] Implement `StorageManager` with scoped storage & `.part` staging
- [x] Implement `MediaProcessor` seam for post-processing
- [x] Implement `DownloadManager` orchestrator

### 2.3 Data Layer & Persistence
- [x] Implement `SmusicDatabase` with SQLiteOpenHelper, WAL, and job/library tables
- [x] Implement `MediaDownloadWorker` with foreground service & notification updates
- [x] Add stale record cleanup on library initialization

### 2.4 Presentation & UI Layer
- [x] Implement `SmusicTheme` dark design system
- [x] Implement `HomeScreen` with URL probe, format picker & storage selectors
- [x] Implement `DownloadsScreen` with active queue progress and actions
- [x] Implement `LibraryScreen` with search, filter, share, and delete
- [x] Implement `SmusicPlaybackService` with AndroidX Media3 ExoPlayer

### 2.5 Release & Deployment
- [/] Deprecate/remove conflicting v0.1.0 legacy data files (`app/src/main/java/com/smusic/app/data/*.kt` root files)
- [ ] Version bump `versionCode = 2`, `versionName = "0.2.0"` in `app/build.gradle.kts`
- [ ] Verify `.gitignore` rules (ensure `local.properties` and local SDK configs are ignored)
- [ ] Stage and commit all files to git
- [ ] Push to `origin/main` on GitHub
- [ ] Publish GitHub Release `v0.2.0` with changelog and notes

---

## 3. Bug Tracking & Known Issues Log

| ID | Severity | Description | Status | Resolution / Action |
|---|---|---|---|---|
| **BUG-001** | Medium | Duplicate class/type conflict between old `data/MediaModels.kt` and new `domain/model/*` | [/] In Progress | Clean up legacy root files in `app/src/main/java/com/smusic/app/data/` |
| **BUG-002** | Low | Network timeouts when downloading Gradle dependencies under unstable connectivity | Mitigated | Offline-first / local SDK configured in `C:\android-sdk` |
| **BUG-003** | Low | Ensure `local.properties` does not get checked into git repository | Verified | Ignore verified in `.gitignore` |
