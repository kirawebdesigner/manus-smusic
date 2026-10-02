# Smusic

A premium, offline-first media library for Android built with Kotlin and Jetpack Compose.

## Current functional slice

- Validate and analyze direct HTTPS audio/video links.
- Probe content type and content length where the server permits HEAD requests.
- Choose the available format.
- Schedule a real HTTP download through WorkManager with a connectivity constraint and foreground progress notification.
- Stream bytes to a local file, resume when the server supports range requests, report real byte progress, retry transient I/O failures, validate the resulting file, and preserve failed/cancelled state.
- Persist media metadata and download state in SQLite across process restarts.
- Browse completed local files and open them in a real Media3 ExoPlayer instance with play/pause, seek, and duration progress.
- Return a clear unsupported-source message instead of bypassing access controls.

The provider boundary is explicit. Add an authorized provider by implementing `MediaProvider` and registering it in `MediaProviderRegistry`; keep provider/network code out of Compose. The current direct provider is intentionally limited to direct HTTPS media URLs.

## Build and test

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$HOME/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin:$PATH"
./gradlew test
./gradlew assembleDebug
```

Open the project in Android Studio and run the `app` configuration. The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

See [RESEARCH.md](RESEARCH.md) for the open-source project review and license notes.
