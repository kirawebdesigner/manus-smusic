# AppFlow – User Journey & Architecture Flow

## 1. Overview
The Smusic app follows a **four‑tab** navigation pattern (Home, Queue, Library, Settings). All user actions travel through the **Domain Layer** where providers analyze URLs, the **DownloadEngine** streams data, and the **Data Layer** persists job state and media metadata.

## 2. User Journey Maps
| Step | Screen | Action | Resulting State |
|---|---|---|---|
| 1 | **Home** – URL Input | User pastes a media URL and taps **Analyze** | `DownloadManager.analyze(url)` → `ProviderRegistry` selects `DirectUrlProvider` or `SpotifyMetadataProvider`. UI displays **Analysis Card** with detected formats and metadata. |
| 2 | **Home** – Format Picker | User selects desired format (e.g., MP3 320kbps) and target storage (Music/Video) | `DownloadJob` created with `JobState.ANALYZING`, queued in WorkManager. |
| 3 | **Queue** – Live Job | Background `MediaDownloadWorker` streams bytes, updates notification & UI progress bar. User can **Pause**, **Cancel**, or **Retry**. | State transitions through `DOWNLOADING → PROCESSING → COMPLETED`. |
| 4 | **Library** – Media Item | Upon completion, `StorageManager` moves the file into public scoped storage, registers with `MediaScanner`, and inserts a row into `library_media`. UI shows playable tile with cover art. | Playback via **Media3 ExoPlayer** service. |
| 5 | **Settings** – Preferences | User toggles **Wi‑Fi‑only** downloads, **Concurrent Job Limit**, or **Dark Mode**. Preferences stored in `DataStore` and observed by `DownloadManager`. | Immediate effect on future jobs. |

## 3. Component Hierarchy (Compose)
```
MainActivity
 └─ Scaffold (BottomNavigation)
     ├─ HomeScreen (AnalyzeCard, FormatPicker)
     ├─ DownloadsScreen (LazyColumn of JobItems)
     ├─ LibraryScreen (LazyGrid of MediaTiles)
     └─ SettingsScreen (PreferenceItems)
```

## 4. Core Logic Flow Diagram (Simplified)
```
User Input → ProviderRegistry → AnalysisResult
    ↓ (if downloadable)
DownloadManager.createJob → WorkManager.enqueue(MediaDownloadWorker)
    ↓
MediaDownloadWorker.run → DownloadEngine.download → StorageManager.storePart → 
    ↳ On success: MediaProcessor.process (optional FFmpeg) →
    ↳ StorageManager.finalize → Library insertion → UI refresh
    ↳ On failure: retry policy → UI error state
```

---

## 5. Data Flow Highlights
- **StateFlow** in `SmusicViewModel` streams `QueueState` and `LibraryState` to UI.
- **WorkManager** guarantees job persistence across process death.
- **Media3 Session** exposes playback controls to system UI (lock screen, Bluetooth).

---

## 6. Edge Cases & Recovery
1. **Network Drop** – `DownloadEngine` writes bytes to `.part` file, records `downloadedBytes` in the job DB. On restart, the engine resumes via HTTP `Range`.
2. **File Conflict** – `StorageManager` checks for existing file name, appends `(1)`, `(2)` etc.
3. **Unsupported URL** – ProviderRegistry returns `AnalysisResult.Unsupported`; UI shows user‑friendly error.
