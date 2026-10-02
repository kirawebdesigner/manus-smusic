# Smusic

A premium, offline-first media library for Android built with Kotlin and Jetpack Compose.

## Current vertical slice

- Paste and analyze a URL.
- Recognize direct HTTPS audio/video links.
- Choose the available format.
- Download with visible progress into the local library state.
- Browse the library and open the local player surface.
- Review download, app, and open-source settings.
- Return a clear unsupported-source message instead of bypassing access controls.

The provider boundary is intentionally explicit. Add an authorized provider by implementing `MediaProvider` and registering it in `MediaProviderRegistry`; keep provider/network code out of Compose. The repository can later be backed by Room/DataStore and Media3 `DownloadService`/`DownloadManager` for durable background downloads.

## Build

```bash
export ANDROID_SDK_ROOT=/home/ubuntu/android-sdk
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:/home/ubuntu/.local/gradle/gradle-8.10.2/bin:$PATH"
gradle :app:assembleDebug
```

Open the project in Android Studio and run the `app` configuration. The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

See [RESEARCH.md](RESEARCH.md) for the open-source project review and license notes.
