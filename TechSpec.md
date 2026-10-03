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
| **Language & Platform** | Kotlin & Android SDK | Kotlin 1.9.22 / Min SDK 26 (Android 8.0) / Compile & Target SDK 34-35 |
| **UI Framework** | Jetpack Compose & Material 3 | Compose BOM 2024.12.01, Material3 1.3.1 |
| **Concurrency** | Kotlin Coroutines & Flow | Coroutines Core & Android 1.8.0 |
| **Persistence** | SQLite / Android SQLiteOpenHelper | Native SQLite with WAL (Write-Ahead Logging) enabled |
| **Background Tasks** | AndroidX WorkManager | WorkManager Runtime KTX 2.10.0 |
| **Media Playback** | AndroidX Media3 | Media3 ExoPlayer & Media3 Session 1.5.1 |
| **Networking** | Java HTTP / HttpsURLConnection | Zero external bulky HTTP dependencies; native streaming with connection pools |
| **Build System** | Gradle Kotlin DSL | Gradle 8.2+ with Android Gradle Plugin 8.2.2 |

---

## 3. Security, Permissions & Storage Architecture

### 3.1 Permissions Matrix
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- Android < 10 fallback -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
```

### 3.2 Scoped Storage Integration
- Public destinations: `Environment.DIRECTORY_MUSIC` / `Smusic` and `Environment.DIRECTORY_MOVIES` / `Smusic`.
- Staging files: Temporary downloads stored as `.part` files in internal cache or target folder, atomically renamed on completion.
- Content Scanning: `MediaScannerConnection` registers completed media with Android's MediaStore immediately.

---

## 4. API & Provider Protocol Specifications

### 4.1 Direct URL Provider
- **Detection:** Inspects scheme (`http/https`) and path extensions (`.mp3`, `.mp4`, `.m4a`, `.flac`, `.wav`, `.ogg`, `.webm`, `.aac`).
- **Probe Request (HEAD):**
  - Sends HTTP `HEAD` to inspect `Content-Length`, `Content-Type`, `Accept-Ranges: bytes`, and final redirected URL.
  - Falls back to `GET` with `Range: bytes=0-1` if server forbids `HEAD`.
- **Response Mapping:** Generates `MediaInfo` with extracted title, MIME type, size, and selectable `MediaFormat`.

### 4.2 Spotify Metadata Provider (spotDL-inspired)
- **Detection:** `open.spotify.com/track/*` or `open.spotify.com/album/*`.
- **Endpoint:** `https://open.spotify.com/oembed?url={URL}`
- **Protocol:** Public unauthenticated oEmbed JSON parsing. Extracts title, artist/author, thumbnail artwork URL, and provider name.
- **Contract:** Returns `AnalysisResult.MetadataOnly` with track metadata to prevent unauthorized streams while enabling structured metadata queries.

---

## 5. Download Engine Specification

### 5.1 Resumption Protocol
```
1. Check existing target file and `.part` file size.
2. If existingSize > 0 and server supports Accept-Ranges:
   - Header: Range: bytes={existingSize}-
   - Expect HTTP 206 Partial Content
3. If HTTP 200 returned:
   - Server does not support resume → truncate and restart stream from byte 0.
4. If HTTP 416 (Range Not Satisfiable):
   - Existing file is already complete or invalid → verify and complete.
```

### 5.2 Real-time Rate Calculation
- Speed calculated using a 1000ms sliding window:
  $$\text{Speed (bytes/sec)} = \frac{\Delta \text{Bytes}}{\Delta \text{Time (sec)}}$$
- ETA calculated dynamically based on remaining bytes divided by smoothed speed.
