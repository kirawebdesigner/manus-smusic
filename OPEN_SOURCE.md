# Open Source Licenses & Attribution

This document records the open-source libraries, frameworks, references, and licensing compliance for the Smusic Android project.

---

## 1. Direct Dependencies

| Library / Component | Version | License | Repository / Home | Purpose in Smusic |
| :--- | :--- | :--- | :--- | :--- |
| **AndroidX Media3** (`media3-exoplayer`, `media3-session`, `media3-ui`) | 1.5.1 | Apache-2.0 | [androidx/media](https://github.com/androidx/media) | Core audio & video playback, MediaSessionService, background playback, and system media notifications. |
| **Jetpack Compose BOM & UI** (`compose-bom`, `foundation`, `material3`, `material-icons-extended`) | 2024.12.01 | Apache-2.0 | [AndroidX Platform](https://developer.android.com/jetpack/compose) | Declarative UI layer, design system tokens, typography, and animations. |
| **AndroidX WorkManager** (`work-runtime-ktx`) | 2.10.0 | Apache-2.0 | [AndroidX Work](https://developer.android.com/topic/libraries/architecture/workmanager) | Reliable background download tasks, constraint enforcement, and persistent execution. |
| **AndroidX Lifecycle** (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`) | 2.8.7 | Apache-2.0 | [AndroidX Lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle) | ViewModel state holders, coroutine scopes, and UI lifecycle binding. |
| **AndroidX Core KTX** (`core-ktx`, `activity-compose`) | 1.15.0 / 1.10.0 | Apache-2.0 | [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core) | Kotlin extensions and ComponentActivity Compose integration. |
| **OkHttp MockWebServer** | 4.12.0 | Apache-2.0 | [square/okhttp](https://github.com/square/okhttp) | Controlled HTTP server for unit and integration testing of range requests and download streaming. |
| **JUnit 4** | 4.13.2 | EPL-1.0 | [junit-team/junit4](https://github.com/junit-team/junit4) | Unit testing framework. |

---

## 2. Architectural References & Licensing Compliance

Smusic’s design and workflow were informed by the study of two prominent open-source projects. **No source code from these projects was copied into Smusic.**

### YTDLnis
* **Repository**: [https://github.com/deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis)
* **License**: GNU General Public License v3.0 (GPL-3.0)
* **Architectural Concepts Studied**:
  - Persistent queue state machine (`QUEUED`, `ANALYZING`, `DOWNLOADING`, `PROCESSING`, `COMPLETED`, `PAUSED`, `FAILED`, `CANCELLED`).
  - Separation between format parsing/resolution and actual download execution.
  - WorkManager foreground worker lifecycle with speed calculation, resume headers (`Range: bytes=X-`), and progress notification channels.
  - MediaStore and Scoped Storage handling for user-selected storage folders (Music, Videos, Downloads).
  - MediaProcessor abstraction layer isolating FFmpeg/Muxer commands.
* **Licensing Adherence**:
  Smusic is an independent, clean-room implementation written in idiomatic Kotlin with zero code copied from YTDLnis. This preserves Smusic's independence from GPL-3.0 copyleft requirements.

### spotDL
* **Repository**: [https://github.com/spotDL/spotify-downloader](https://github.com/spotDL/spotify-downloader)
* **License**: MIT License
* **Architectural Concepts Studied**:
  - **Decoupling Metadata Discovery from Media Acquisition**: Querying public metadata (track name, artist, album, duration, ISRC, cover art) independently of the media download pipeline.
  - Track / Album / Playlist normalized models.
  - Clear separation of concerns: A metadata provider returns information; an authorized media resolver locates an accessible stream; a downloader fetches it; a post-processor applies tagging and folder structuring.
* **Licensing Adherence**:
  Conceptually referenced. No proprietary or bypass code is included. Smusic explicitly respects access controls and does not provide unauthorized decryption or DRM bypass.

---

## 3. License Notices

### Apache License 2.0
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
