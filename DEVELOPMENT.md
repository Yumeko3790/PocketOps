# Development Notes

## Read this first

This repository no longer behaves like a stock Google AI Edge Gallery build.

The current production path is:

- launcher activity: `com.pocketops.app.PocketOpsActivity`
- embedded foreground service: `com.pocketops.app.GenieHttpService`
- app id: `com.pocketops.app`
- retained namespace: `com.google.ai.edge.gallery`
- retained application class: `com.google.ai.edge.gallery.GalleryApplication`
- local inference endpoint: `127.0.0.1:8910`
- build target: Android 12+ (`minSdk 31`) on `arm64-v8a`

If you are trying to understand "what the app does today", start from
`PocketOpsActivity.kt` and `GenieHttpService.kt`, not from the retained Gallery
shell.

## Production runtime path

### Startup sequence

`PocketOpsActivity.refreshGenieServiceStatus()` currently does the following:

1. load `assets/maintenance/knowledge_graph.json`
2. render a staged sync / loading experience
3. probe `GET http://127.0.0.1:8910/v1/models`
4. treat the service as ready only if it returns model metadata that is loaded
5. if the model is not ready and Android 11+ has not granted all-files access,
   surface a settings handoff for `/sdcard/GenieModels`
6. otherwise start `GenieHttpService` as a foreground service
7. poll until the embedded native HTTP service reports readiness

The current polling window is roughly 180 seconds.

### PocketOpsActivity responsibilities

The current launcher path is responsible for:

- login and staged loading UX
- text diagnosis
- image diagnosis
- video diagnosis via key-frame extraction + contact sheet generation
- GraphRAG lookup and structured diagnosis rendering
- related work order lookup
- Bluetooth diagnostic-code assisted prompting
- diagnosis history
- work-order preview plus PDF export/share

### Embedded service responsibilities

`GenieHttpService` currently:

- scans `/sdcard/GenieModels`
- picks the first child directory that contains `config.json`
- depends on all-files access on Android 11+ when reading shared-storage model
  directories
- sets `ADSP_LIBRARY_PATH` and `LD_LIBRARY_PATH`
- starts the native service through
  `com.example.genieapiservice.MyNativeLib`
- runs as an Android foreground service
- clears in-memory state if the native service exits, so the app can retry

If a compatible service is already listening on `127.0.0.1:8910`, PocketOps
reuses it instead of starting another instance.

### GraphRAG responsibilities

The retained maintenance knowledge-graph path is still part of the production
flow. Its main responsibilities are:

- equipment matching
- symptom matching
- up to 4-hop graph traversal
- structured diagnosis assembly
- related work order lookup
- partial context generation when falling back to the LLM

Primary files:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/maintenance/MaintenanceKnowledgeGraph.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/maintenance/MaintenanceTypes.kt`
- `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`

## HTTP contract

The current PocketOps mainline uses:

- `GET /v1/models` for readiness checks
- `POST /v1/chat/completions` for text, image, and video diagnosis
- `POST /clear` to reset service-side chat state before text inference

Operational notes:

- readiness requires returned model metadata, not just an open localhost port
- text inference is consumed as a stream
- image/video requests are sent as non-streaming multimodal payloads
- PocketOps resets the service-side text conversation before each new text
  diagnosis request

## Runtime dependencies

The current mainline depends on all of the following:

- packaged native libraries in the APK
- a valid model directory under `/sdcard/GenieModels/<model-dir>/config.json`
- all-files access on Android 11+ so the app can scan that directory
- a usable local HTTP inference endpoint on `127.0.0.1:8910`

Without those pieces, the PocketOps diagnosis flow is incomplete.

## Files to change together

When you change the deployed model, local service contract, or runtime startup
behavior, inspect these files together:

- `Android/src/app/src/main/java/com/pocketops/app/PocketOpsActivity.kt`
  - prompts, model id, request payloads, image/video flow, startup readiness UX
- `Android/src/app/src/main/java/com/pocketops/app/GenieHttpService.kt`
  - model-root scanning, native startup args, lifecycle handling
- `Android/src/app/src/main/AndroidManifest.xml`
  - launcher/service wiring, permissions, FileProvider, cleartext allowance
- `Android/src/app/build.gradle.kts`
  - app id, SDK levels, ABI filters, manifest placeholders
- root `model_allowlist.json`
  - retained Gallery-era model metadata path
- `model_allowlists/*.json`
  - historical or release-specific allowlist snapshots

Do not assume the PocketOps runtime model id and the retained Gallery allowlist
model id are the same thing.

## Retained HuggingFace / Gallery modules

This codebase still contains inherited Google AI Edge Gallery modules for model
management, HuggingFace auth, downloads, agent skills, and task navigation.

If you intentionally re-enable those retained upstream features, you may also
need to update:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt`
- `Android/src/app/build.gradle.kts`
  - `manifestPlaceholders["appAuthRedirectScheme"]`

Those settings are not part of the default PocketOps diagnosis flow.

## Directory ownership

Use this as the current code-ownership map:

- `Android/src/app/src/main/java/com/pocketops/app/`
  - production launcher path, PocketOps UI, embedded service
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/maintenance/`
  - knowledge graph logic and retained maintenance data structures
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/`
  - inherited Gallery shell, app infrastructure, retained task framework
- `Android/src/app/src/main/assets/maintenance/`
  - knowledge graph payload and related runtime assets
- `skills/` and `Android/src/app/src/main/assets/skills/`
  - retained skills documentation and bundled skill resources

## Build and debug locally

Open `Android/src` in Android Studio if you want to build, run, or debug the
app.

Command-line builds from `Android/src`:

```shell
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug

# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug
```

Useful runtime log tags:

- `PocketOps`
- `GenieHttpService`
- `AGMaintenanceKG`

Example filtered logcat:

```shell
adb logcat PocketOps:D GenieHttpService:D AGMaintenanceKG:D *:S
```

## Retained but non-default modules

These paths still exist, but they are not the default production mainline:

- `MainActivity` + Gallery navigation shell
- `MaintenanceTask` + `MaintenanceScreen`
- `GenieNative.kt`
- `GenieModelHelper.kt`
- `OnnxVIT.kt`
- `skills/` guide + sample skills
- `customtasks/mobileactions/`
- `genie_jni.cpp`

They remain useful for historical context, debugging, or future experiments,
but they should not be confused with the current
`PocketOpsActivity + GenieHttpService` path.
