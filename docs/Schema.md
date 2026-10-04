# Database Schema & Data Models (Schema) — Smusic v0.2.0

## 1. Overview
Smusic uses an SQLite database (`smusic_v2.db`) managed via `SQLiteOpenHelper` with Write-Ahead Logging (WAL) enabled for safe concurrent writes from background `WorkManager` workers and reads from the main UI thread.

The DDL and migrations live in one place — `app/src/main/java/com/smusic/app/data/database/DatabaseSchema.kt` — as pure Kotlin strings, so the exact SQL executed on-device is unit-tested against a real SQLite driver (`DatabaseSchemaTest`). Fresh installs run `createStatements` (v1 tables + all migrations); upgrades run `migrationStatements(oldVersion)` in order. Migrations are **additive only**: existing rows are never dropped or rewritten.

Current schema version: **2** (`DatabaseSchema.VERSION`).

---

## 2. Table Definitions

### 2.1 Table: `download_jobs`
Stores persistent download tasks across app lifecycles.

```sql
CREATE TABLE IF NOT EXISTS download_jobs (
    id TEXT PRIMARY KEY,
    original_url TEXT NOT NULL,
    title TEXT NOT NULL,
    uploader TEXT NOT NULL,
    thumbnail TEXT,
    duration TEXT NOT NULL,
    media_type TEXT NOT NULL,
    format_id TEXT NOT NULL,
    format_label TEXT NOT NULL,
    container TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    dest_category TEXT NOT NULL,
    dest_subfolder TEXT,
    state TEXT NOT NULL,
    progress REAL NOT NULL DEFAULT 0.0,
    speed_bytes INTEGER NOT NULL DEFAULT 0,
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    total_bytes INTEGER NOT NULL DEFAULT 0,
    local_path TEXT,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    -- v2 additions (migrationStatements(1)):
    filename TEXT,              -- server/URL-derived filename
    temp_path TEXT,             -- active .part staging path (recovery/cleanup)
    completed_at INTEGER        -- completion timestamp
);
```

`state` values (`JobState`): `QUEUED`, `ANALYZING`, `WAITING`, `DOWNLOADING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `PAUSED`.
`dest_category` values (`StorageCategory`): `MUSIC`, `VIDEOS`, `OTHER`.

### 2.2 Table: `library_media`
Catalogs completed, verified offline media ready for playback, sharing, or management.

```sql
CREATE TABLE IF NOT EXISTS library_media (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    creator TEXT NOT NULL,
    album TEXT NOT NULL,
    source TEXT NOT NULL,
    duration_ms INTEGER NOT NULL DEFAULT 0,
    file_size_bytes INTEGER NOT NULL DEFAULT 0,
    readable_size TEXT NOT NULL,
    media_type TEXT NOT NULL,
    local_path TEXT NOT NULL UNIQUE,
    mime_type TEXT,
    added_date INTEGER NOT NULL,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    playlist_name TEXT,
    -- v2 additions (migrationStatements(1)):
    last_played INTEGER NOT NULL DEFAULT 0,   -- epoch ms of most recent play
    play_count INTEGER NOT NULL DEFAULT 0
);
```

`local_path` is `UNIQUE`; library inserts use `CONFLICT_IGNORE` so repeated scans can never duplicate rows or reset user state (favorites, play counts).

---

## 3. Versioned Migration

| From | To | Statements |
|---|---|---|
| 1 | 2 | `ALTER TABLE download_jobs ADD COLUMN filename TEXT` |
| | | `ALTER TABLE download_jobs ADD COLUMN temp_path TEXT` |
| | | `ALTER TABLE download_jobs ADD COLUMN completed_at INTEGER` |
| | | `ALTER TABLE library_media ADD COLUMN last_played INTEGER NOT NULL DEFAULT 0` |
| | | `ALTER TABLE library_media ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0` |

`SmusicDatabase.onUpgrade` tolerates a "duplicate column" error (interrupted upgrade) but never wipes data to "fix" a migration. Pre-existing rows receive honest defaults: `filename`/`temp_path`/`completed_at` = NULL, `last_played`/`play_count` = 0. Verified by `DatabaseSchemaTest.migration from v1 preserves rows and adds columns with honest defaults`.

---

## 4. Data Entities & Kotlin Domain Mapping

### 4.1 `DownloadJob` (Domain Model — `domain/model/DownloadJob.kt`)
- `id: String` (UUID)
- `mediaInfo: MediaInfo` (title, uploader, thumbnail, duration, original URL, media type)
- `selectedFormat: MediaFormat` (id, label, container, MIME — probed, never invented)
- `destination: DownloadDestination` (category, subfolder, custom path)
- `state: JobState`, `progress: Float`, `speedBytesPerSecond: Long`
- `downloadedBytes: Long`, `totalBytes: Long`
- `localPath: String?`, `tempPath: String?`, `filename: String?`
- `errorMessage: String?`, `retryCount: Int`
- `createdAt: Long`, `updatedAt: Long`, `completedAt: Long?`

### 4.2 `LibraryItem` (Domain Model — `data/database/SmusicDatabase.kt`)
- `id: String` (`file:<path>` for scan-recovered entries, UUID otherwise)
- `title: String`, `creator: String`, `album: String`, `source: String`
- `durationMs: Long`, `fileSizeBytes: Long`, `readableSize: String`
- `mediaType: MediaType`, `localPath: String`, `mimeType: String?`
- `addedDate: Long`, `isFavorite: Boolean`, `playlistName: String?`
- `lastPlayed: Long`, `playCount: Int`

---

## 5. Stale Record Cleanup & Integrity Safeguards
- **Stale Pruning:** `cleanStaleRecords()` removes `library_media` rows whose file no longer exists on disk (user deleted it in a file manager) and returns the removed count.
- **Orphan Recovery:** `StorageManager.scanMediaFiles()` lists known media files under the app-controlled `downloads/` root (excluding `.part` and dotfiles); files without a row are inserted back into the library with id `file:<path>`.
- **Atomic Staging:** downloads write to `<name>.part` beside the target and rename only after `validateIntegrity()` (size check) plus extension enforcement via `MediaSniffer` — no corrupted or mislabeled rows reach the library.
