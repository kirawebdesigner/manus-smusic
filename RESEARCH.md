# Smusic Architectural Research: YTDLnis & spotDL

This document synthesizes the architectural study of **YTDLnis** and **spotDL**, detailing how their proven architectural patterns are adapted into Smusic's native Kotlin + Jetpack Compose Android codebase.

---

## 1. Study of YTDLnis

**Repository**: `https://github.com/deniscerri/ytdlnis`  
**License**: GPL-3.0  
**Domain**: Advanced Android media downloader and front-end for yt-dlp.

### Architectural Takeaways

1. **Persistent Download Queue & State Machine**:
   - YTDLnis treats downloads as first-class persistent entities rather than ephemeral memory tasks.
   - States: `QUEUED` -> `ANALYZING` -> `WAITING` -> `DOWNLOADING` -> `PROCESSING` -> `COMPLETED` / `PAUSED` / `FAILED` / `CANCELLED`.
   - **Smusic Adaptation**: Smusic implements a persistent database queue where each job tracks bytes downloaded, total bytes, speed, retry counts, target destination, and lifecycle status. Restarting the app retains full queue state.

2. **WorkManager Foreground Execution**:
   - Downloads run via AndroidX `CoroutineWorker` promoted to a foreground service with `setForegroundAsync`.
   - Continuous notification updates display active speed (e.g. `3.2 MB/s`), remaining bytes, and progress percentage.
   - Support for connection interruption and range requests (`Range: bytes=X-`) allows automatic resume of partially downloaded files (`.part` files).

3. **Format Selection & Technical Metadata**:
   - Rather than artificial or generic "High/Medium/Low" labels, YTDLnis inspects server-reported formats: container (`m4a`, `mp3`, `mp4`, `webm`), video resolution (`1080p`, `720p`, `480p`), audio bitrates, and exact file size.
   - **Smusic Adaptation**: Smusic's `FormatPicker` presents genuine technical metadata directly probed from the source.

4. **MediaProcessor / FFmpeg Isolation**:
   - Audio extraction, stream remuxing, metadata embedding, and thumbnail processing are encapsulated behind a clean processor interface.
   - This keeps heavy or platform-dependent media commands strictly decoupled from network and UI layers.

5. **Modern Android Storage & MediaStore**:
   - Files are routed to categorized directories (`Music/`, `Movies/`, `Download/`) using Scoped Storage and registered with `MediaScannerConnection` so local players and system indexers recognize new media immediately.

---

## 2. Study of spotDL

**Repository**: `https://github.com/spotDL/spotify-downloader`  
**License**: MIT  
**Domain**: Music metadata discovery and multi-provider downloader.

### Architectural Takeaways

1. **Decoupling Metadata Discovery from Media Acquisition**:
   - spotDL's core design strength is separating *what to get* (Metadata) from *how to get it* (Media Acquisition).
   ```text
   Spotify Link ──> Spotify Metadata Provider ──> Normalized Track Model
                                                         │
   Authorized Media Source <─────────────────────────────┘
          │
          ▼
   Download Engine ──> Media Processor (Tagging & Art) ──> Local Library
   ```
   - **Smusic Adaptation**: In Smusic, `MetadataProvider` and `MediaProvider` are separate modular contracts:
     - `MetadataProvider.analyze(url)` resolves tracks, artists, albums, durations, and cover art.
     - For direct URLs, `DirectUrlProvider` handles both metadata extraction and download streaming.
     - For Spotify links, `SpotifyMetadataProvider` parses track/album metadata via public oEmbed and API contracts, presenting the rich metadata card, while clearly reporting that direct streaming/downloading of DRM-protected content is not permitted or supported.

2. **Track, Album, and Playlist Modeling**:
   - spotDL normalizes metadata into a universal domain model: `title`, `artists`, `album`, `year`, `trackNumber`, `discNumber`, `coverUrl`, `isrc`.
   - **Smusic Adaptation**: Smusic adopts a normalized `MediaInfo` model with embedded `TrackMetadata`, allowing both single tracks and collections to be represented consistently.

3. **Audio Pipeline & Metadata Writing**:
   - Downloaded media is post-processed: ID3 tags (artist, title, album, year) and cover artwork are embedded directly into the audio container.
   - Smusic introduces a `MetadataWriter` abstraction in the `MediaProcessor` pipeline for enriching local files.

---

## 3. Smusic Target Architecture

```text
                             Smusic UI (Jetpack Compose)
               [Home / Queue / Library / Player / Settings]
                                    │
                                    ▼
                            Download Manager
                                    │
                      ┌─────────────┴─────────────┐
                      ▼                           ▼
              Provider Registry             Queue Manager
                      │                           │
          ┌───────────┼───────────┐               ▼
          ▼           ▼           ▼        Persistent Storage
      Direct URL   Spotify      Future      (SQLite / Room)
       Provider    Metadata    Providers          │
          │           │                           │
          └─────┬─────┘                           │
                ▼                                 ▼
         Media Resolver                   Download Engine
                                           (Streaming / Range)
                                                  │
                                       ┌──────────┴──────────┐
                                       ▼                     ▼
                                  HTTP Stream         MediaProcessor
                                       │              (FFmpeg/Muxer)
                                       └──────────┬──────────┘
                                                  ▼
                                            Storage Manager
                                        (Scoped / App Storage)
                                                  │
                                                  ▼
                                         Local Media Library
                                                  │
                                                  ▼
                                       Media3 Playback Service
                                      (MediaSession + Background)
```

---

## 4. Licensing & Clean-Room Implementation Rules

1. **Zero GPL Code Copying**: YTDLnis is GPL-3.0. Smusic does NOT copy any source code, classes, or assets from YTDLnis.
2. **Independent Re-implementation**: All queue management, WorkManager routines, models, and UI components are written from scratch in Kotlin.
3. **Honest Capabilities**: Smusic never claims to download from unsupported or DRM-protected sources (such as Spotify). When given Spotify URLs, permitted public metadata is displayed with clear, honest indicators that direct downloading is unsupported.
