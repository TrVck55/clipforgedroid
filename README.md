# ClipForge AI (Android)


Kotlin + Jetpack Compose Android app that turns long videos into short clips using AI (transcription + LLM) and FFmpeg.

**Package:** `dev.clipforge.app`  
**Min SDK:** 26 · **Target / Compile SDK:** 34 · **Kotlin:** 1.9.24 · **AGP:** 8.5.2

## Features

- Import local video or paste a URL (YouTube / Twitch / etc. via yt-dlp)
- Transcription: cloud (OpenAI Whisper API) or optional on-device (whisper.cpp)
- AI clip selection via OpenAI or Anthropic Claude
- Background processing with WorkManager + notifications
- Template-driven clip styles
- FFmpeg-based rendering (ffmpeg-kit)

## Requirements

- Android Studio Hedgehog / Iguana or newer (or JDK 17 + Android SDK)
- Internet for cloud AI / download features
- Optional: NDK 26 + CMake 3.22 if enabling on-device Whisper

## Quick start (local)

```bash
# Clone
git clone https://github.com/<you>/clipforge-android.git
cd clipforge-android

# Open in Android Studio, or from CLI (after generating the wrapper once):
./gradlew assembleDebug
```

If you don't have `./gradlew` yet:

```bash
# With a system Gradle install, or Android Studio's embedded Gradle:
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Configuration

API keys and preferences are stored at runtime via the in-app **Settings** screen (encrypted preferences). Nothing secret is hardcoded.

## Optional: on-device Whisper

The project builds **without** native code by default (cloud transcription only).

To enable on-device transcription:

1. Add the [whisper.cpp](https://github.com/ggerganov/whisper.cpp) submodule:

   ```bash
   git submodule add https://github.com/ggerganov/whisper.cpp.git app/src/main/cpp/whisper.cpp
   git submodule update --init --recursive
   ```

2. Ensure `app/src/main/cpp/CMakeLists.txt` exists and points at the submodule (add one if missing; the Gradle script only enables `externalNativeBuild` when `whisper.cpp/CMakeLists.txt` is present).

3. Build with NDK installed. CI already installs NDK + CMake when the workflow runs.

## GitHub Actions

Workflow: [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml)

- Triggers: push to `main`, pull requests, manual `workflow_dispatch`
- Builds `assembleDebug` by default (or `assembleRelease` via input)
- Uploads the APK as a workflow artifact named `clipforge-apk`
- Uses JDK 17, Android SDK, optional NDK/CMake
- Does **not** require a committed Gradle Wrapper binary (uses `gradle/actions/setup-gradle`)

## Project layout

```
app/
  src/main/
    java/dev/clipforge/app/   # Kotlin sources (UI, data, work)
    res/                      # resources
    assets/templates/         # default clip templates
    cpp/                      # optional native (whisper.cpp)
  build.gradle.kts
build.gradle.kts
settings.gradle.kts
.github/workflows/android-build.yml
```

## License

Add your preferred license (MIT / Apache-2.0 recommended). Third-party components (ffmpeg-kit LGPL, yt-dlp, etc.) retain their own licenses.
