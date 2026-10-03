# Database Schema & Data Models (Schema) — Smusic v0.2.0

## 1. Overview
Smusic uses an SQLite database (`smusic.db`) managed via `SQLiteOpenHelper` with Write-Ahead Logging (WAL) enabled for safe concurrent writes from background `WorkManager` workers and reads from the main UI thread.

---

## 2. Table Definitions

### 2.1 Table: `download_jobs`
Stores persistent download tasks across app lifecycles.

```sql
CREATE TABLE download_jobs (
    id TEXT PRIMARY KEY NOT NULL,
    source_url TEXT NOT NULL,
    title TEXT NOT NULL,
    author TEXT,
    thumbnail_url TEXT,
    format_id TEXT NOT NULL,
    format_label TEXT NOT NULL,
    media_type TEXT NOT NULL,           -- 'AUDIO' or 'VIDEO'
    state TEXT NOT NULL,                -- 'QUEUED','ANALYZING','DOWNLOADING','PROCESSING','COMPLETED','FAILED','CANCELLED','PAUSED'
    total_bytes INTEGER NOT NULL DEFAULT 0,
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    speed_bytes_per_sec INTEGER NOT NULL DEFAULT 0,
    destination_path TEXT,
    destination_category TEXT NOT NULL, -- 'MUSIC','MOVIES','DOWNLOADS','CUSTOM'
    error_message TEXT,
    created_at INTEGER NOT NULL,
    completed_at INTEGER
);

CREATE INDEX idx_jobs_state ON download_jobs(state);
CREATE INDEX idx_jobs_created_at ON download_jobs(created_at DESC);
```

### 2.2 Table: `library_media`
Catalogs completed, verified offline media ready for playback, sharing, or management.

```sql
CREATE TABLE library_media (
    id TEXT PRIMARY KEY NOT NULL,
    job_id TEXT,
    file_path TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT,
    album TEXT,
    duration_ms INTEGER NOT NULL DEFAULT 0,
    file_size_bytes INTEGER NOT NULL DEFAULT 0,
    mime_type TEXT NOT NULL,
    media_type TEXT NOT NULL,           -- 'AUDIO' or 'VIDEO'
    thumbnail_uri TEXT,
    date_added INTEGER NOT NULL,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    play_count INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(job_id) REFERENCES download_jobs(id) ON DELETE SET NULL
);

CREATE INDEX idx_library_media_type ON library_media(media_type);
CREATE INDEX idx_library_favorite ON library_media(is_favorite);
CREATE INDEX idx_library_date_added ON library_media(date_added DESC);
```

---

## 3. Data Entities & Kotlin Domain Mapping

### 3.1 `DownloadJob` (Domain Model)
- `id: String` (UUID)
- `sourceUrl: String`
- `mediaInfo: MediaInfo` (title, author, thumbnail, format)
- `state: JobState` (enum with UI status mapping)
- `progress: Float` (downloadedBytes / totalBytes)
- `speedBytesPerSec: Long` (formatted to KB/s or MB/s in UI)
- `destinationPath: String?`
- `createdAt: Long`, `completedAt: Long?`

### 3.2 `LibraryItem` (Domain Model)
- `id: String` (UUID)
- `filePath: String` (scoped file path)
- `fileName: String`
- `title: String`, `artist: String?`, `album: String?`
- `durationMs: Long`
- `fileSizeBytes: Long`
- `mimeType: String`, `mediaType: MediaType`
- `isFavorite: Boolean`
- `dateAdded: Long`

---

## 4. Stale Record Cleanup & Integrity Safeguards
- **File Validation on Boot:** At application launch and library load, `StorageManager.validateLibrary()` verifies each `file_path` exists on disk.
- **Stale Pruning:** If a user deleted a file via an external file manager, the orphaned row in `library_media` is automatically purged.
- **Atomic Renaming:** `.part` files are renamed only after checksum/size verification, ensuring zero corrupted records in the library.
