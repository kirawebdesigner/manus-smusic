# Smusic

A premium, offline-first media library for Android built with Kotlin and Jetpack Compose.

## What it does

- **Analyze a pasted link.** `ProviderRegistry` routes the URL to a provider: `DirectUrlProvider` probes direct HTTP(S) media links (HEAD first, ranged `GET bytes=0-0` fallback for HEAD-hostile servers), derives the real filename (Content-Disposition → URL path → query parameter, sanitized against path traversal), classifies audio/video from MIME + extension, and reports only honest format metadata — no invented codecs, bitrates, or resolutions. `SpotifyMetadataProvider` resolves public oEmbed metadata only and clearly reports that downloading DRM-protected content is unsupported.
- **Download through a persistent queue.** Enqueued jobs run in `MediaDownloadWorker` (WorkManager foreground service with progress notification), stream bytes to `.part` staging files, resume via HTTP Range when the server supports it, follow redirects manually (loop/malformed/too-many detection), and report real byte progress, speed, and ETA. Transient errors (408/429/5xx/I/O) auto-retry; permanent ones (404/401/403/empty body) fail fast with a user-facing message. Interrupted jobs are recovered on launch; a 1–3 slot concurrency limiter is configurable in Settings.
- **Finalize into a local library.** On completion the file is integrity-checked, given a truthful extension via magic-byte sniffing when the server provided none, duration-read, scanned into the Android MediaStore, and recorded in SQLite (`smusic_v2.db`, WAL, additive v1→v2 migration). Orphaned files are re-scanned into the library; rows whose files vanished are pruned.
- **Browse and search the library.** Case-insensitive search over title, artist, album, playlist, and filename; favorites, play counts, share via `FileProvider`, and delete (file removed with the row).
- **Play with Media3.** A full-screen player (seek bar, shuffle, repeat, buffering/error states) plus a mini player above the bottom navigation; playback runs in `SmusicPlaybackService` (Media3 `MediaSessionService`) so audio survives navigation and exposes the system media notification. Position, shuffle/repeat, and "resume where you left off" persist across restarts.
- **Configure in Settings:** default destination category, Wi-Fi-only downloads, max concurrent downloads (1–3), auto-retry, autoplay-next, resume-playback, shuffle/repeat defaults, storage usage, clear temporary files, and clear download history.

### Honest limitations

- Direct HTTP(S) media URLs only. No YouTube, TikTok, Spotify, or other streaming-platform downloads — and no DRM/access-control circumvention, ever.
- Spotify links yield public metadata (title/artist/artwork), not audio.
- No FFmpeg transcoding; files are stored as delivered. `MediaProcessor` remains the seam if that is added later.
- Play counts and durations are best-effort (duration 0 when the retriever cannot parse the file).

The provider boundary is explicit. Add an authorized provider by implementing `MediaProvider` and registering it in `ProviderRegistry` (`app/src/main/java/com/smusic/app/domain/provider/`); keep provider/network code out of Compose.

## Build and test

Requirements: JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties` or export `ANDROID_HOME`).

```bash
./gradlew test          # 71 unit tests (engine, provider probing, schema/migration, storage, search, formatting, sniffer, concurrency)
./gradlew assembleDebug
./gradlew lint          # 0 errors
```

On low-memory machines the project pins `org.gradle.jvmargs=-Xmx1024m` and in-process Kotlin compilation in `gradle.properties` so builds run reliably in small sandboxes.

Open the project in Android Studio and run the `app` configuration. The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## License

Smusic is licensed under the Apache License, Version 2.0 — see [LICENSE](LICENSE). Dependency licenses and clean-room attribution notes are documented in [OPEN_SOURCE.md](OPEN_SOURCE.md).

See [RESEARCH.md](RESEARCH.md) for the open-source project review and license notes.
