# PocketOps Android Sources

This directory is only a wrapper around the Android application sources used by
PocketOps.

The actual Gradle project root is:

- `Android/src`

Open `Android/src` in Android Studio if you want to build, run, or debug the
app.

## Current launcher path

The default production entry points are:

- launcher activity:
  `Android/src/app/src/main/java/com/pocketops/app/PocketOpsActivity.kt`
- embedded foreground service:
  `Android/src/app/src/main/java/com/pocketops/app/GenieHttpService.kt`

The old Gallery shell has been removed from the Android source tree. The
PocketOps launcher path above is the only production app entry point.

## Build configuration snapshot

- application id: `com.pocketops.app`
- namespace: `com.pocketops.app`
- version: `4.8`
- minSdk / targetSdk / compileSdk: `31` / `35` / `35`
- ABI: `arm64-v8a`

## Runtime prerequisites

PocketOps currently expects:

- a compatible Android 12+ device
- packaged native libraries inside the APK
- a model directory under `/sdcard/GenieModels/<model-dir>/config.json`
- all-files access on Android 11+ so the app can scan that directory
- a usable local HTTP inference endpoint on `127.0.0.1:8910`

If the endpoint is not already running, `GenieHttpService` will try to start it
from inside the app.

## Important Android-side files

- manifest permissions and service/provider wiring:
  `Android/src/app/src/main/AndroidManifest.xml`
- shared FileProvider paths:
  `Android/src/app/src/main/res/xml/file_paths.xml`
- PocketOps launcher UI and diagnosis flow:
  `Android/src/app/src/main/java/com/pocketops/app/PocketOpsActivity.kt`
- embedded native service wrapper:
  `Android/src/app/src/main/java/com/pocketops/app/GenieHttpService.kt`
- knowledge graph asset:
  `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`

## Command-line build

From `Android/src`:

```shell
# Windows
.\gradlew.bat installDebug

# macOS / Linux
./gradlew installDebug
```

After installation, the app drawer entry is `PocketOps`.
