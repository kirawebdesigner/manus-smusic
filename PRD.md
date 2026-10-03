# Product Requirement Document (PRD) — Smusic v0.2.0

## 1. Executive Summary
**Smusic** is a high-performance, native Android media downloader, manager, and audio player built with Kotlin and Jetpack Compose. Inspired by architectural paradigms from **YTDLnis** (modular provider routing, persistent download queues, background WorkManager jobs) and **spotDL** (clean metadata-first workflows, structured track extraction), Smusic delivers a sovereign, privacy-respecting, and resilient offline media experience without bloated web-views or simulated progress.

---

## 2. Problem Statement
Existing mobile media downloaders and players suffer from several fatal flaws:
- **Flaky & Unreliable Downloads:** Most mobile downloaders lack true HTTP Range resume capability, dropping connections upon network switching or failing silently on large media files.
- **Unverified & GPL-Entangled Codebases:** Many apps copy GPL code directly, risking legal entanglements and license contamination.
- **Simulated / Fake Progress Bars:** Tools frequently fabricate download speed, progress indicators, or metadata, frustrating power users.
- **Fragmented User Experiences:** Users switch between multiple apps to probe a URL, download content, scan files into storage, and play audio in the background.
- **Fragile Network Resilience:** In emerging regions (such as Ethiopia) with intermittent connectivity, standard downloaders repeatedly fail from start to finish without byte-level resume support.

---

## 3. Target Audience & Personas
- **The Offline Music & Media Enthusiast:** Users who commute, travel, or live in regions with costly or unstable internet and require offline audio/video storage with full playback controls.
- **The Open-Source Purist:** Devs and power users looking for a clean, permission-minimal, ad-free Android app with zero telemetry and transparent licensing.
- **Content Archivists:** Users saving public audio streams, lectures, podcasts, or direct media files with verified hashes and scoped storage protection.

---

## 4. MVP & v0.2.0 Feature Scope

### 4.1 Core Features (v0.2.0 In-Scope)
| Category | Feature | Description |
|---|---|---|
| **Discovery & Analysis** | Multi-Provider Engine | Modular URL analyzer routing Direct URLs (MP4, MP3, FLAC, M4A, WAV, OGG, WEBM) and Spotify track/album metadata via public oEmbed. |
| **Download Pipeline** | Real HTTP Resumable Engine | Native byte-level `Range: bytes=X-` resumption, automatic redirect following (301/302/307/308), `.part` staging, and real-time instantaneous speed & ETA calculation. |
| **Queue Management** | SQLite-Backed Persistent Queue | Persistent job states (`QUEUED`, `ANALYZING`, `DOWNLOADING`, `PROCESSING`, `COMPLETED`, `PAUSED`, `FAILED`, `CANCELLED`). Survives app termination. |
| **Background Processing** | AndroidX WorkManager Worker | Foreground service with progress notifications, WakeLock safety, and automatic retry policies. |
| **Library Management** | Scoped Media Library | Database-backed library, duplicate avoidance (`(1)` renaming), file validation, sharing via `FileProvider`, and auto-cleanup for stale/deleted files. |
| **Audio Playback** | Media3 ExoPlayer Service | Native background playback, play/pause/seek controls, lock-screen audio notification with playback state. |
| **Modern Native UI** | Jetpack Compose 4-Tab Interface | Home (Analyze & Download), Queue (Live Status), Library (Local Storage & Player), Settings (Directories & Engine Defaults). |

### 4.2 Out-of-Scope (Deferred to Future Versions)
- Direct unauthorized DRM decrypters or scraping copyrighted streams directly.
- Built-in bundled FFmpeg binary execution on-device (abstraction seam prepared in v0.2.0, binary bundle in v0.3.0).
- Cloud synchronization or remote server proxying (all processing remains 100% on-device).

---

## 5. Success Metrics & KPIs
1. **Download Completion Rate:** > 95% completion rate across poor or dropping network connections using byte-range resume.
2. **Cold Start Time:** < 800ms to interactive Compose UI on mid-tier Android devices (Android 10+).
3. **App Footprint:** APK bundle size < 25MB without bundled binaries.
4. **Zero GPL Contamination:** 100% clean-room Apache-2.0 / MIT license compliance.
