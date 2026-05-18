# Development Notes

## Read this first

This repository no longer behaves like a stock Google AI Edge Gallery build.

The current production path is:

- launcher activity: `com.pocketops.app.PocketOpsActivity`
- embedded foreground service: `com.pocketops.app.GenieHttpService`
- app id: `com.pocketops.app`
- namespace: `com.pocketops.app`
- application class: none
- local inference endpoint: `127.0.0.1:8910`
- build target: Android 12+ (`minSdk 31`) on `arm64-v8a`

If you are trying to understand "what the app does today", start from
`PocketOpsActivity.kt` and `GenieHttpService.kt`.

## Production runtime path

### Startup sequence

`PocketOpsActivity.refreshGenieServiceStatusWithDemo()` currently does the following:

1. normalize the configured demo server URL
2. if a demo server is configured, fetch the remote manifest and sync the boot
   knowledge graph resource
3. if remote sync fails, fall back to the cached demo graph when available
4. otherwise load `assets/maintenance/knowledge_graph.json`
5. render a staged sync / loading experience
6. probe `GET http://127.0.0.1:8910/v1/models`
7. treat the service as ready only if it returns model metadata that is loaded
8. if the model is not ready and Android 11+ has not granted all-files access,
   surface a settings handoff for `/sdcard/GenieModels`
9. otherwise start `GenieHttpService` as a foreground service
10. poll until the embedded native HTTP service reports readiness

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

The maintenance knowledge-graph path is part of the production flow. Its main
responsibilities are:

- equipment matching
- symptom matching
- up to 4-hop graph traversal
- structured diagnosis assembly
- related work order lookup
- partial context generation when falling back to the LLM

Primary files:

- `Android/src/app/src/main/java/com/pocketops/app/MaintenanceKnowledgeGraph.kt`
- `Android/src/app/src/main/java/com/pocketops/app/MaintenanceTypes.kt`
- `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`

## HTTP contract

The current PocketOps mainline uses:

- `GET /v1/models` for readiness checks
- `POST /v1/chat/completions` for text, image, and video diagnosis
- `POST /clear` to reset service-side chat state before each text, image, or
  video diagnosis request

Operational notes:

- readiness requires returned model metadata, not just an open localhost port
- text inference is consumed as a stream
- image/video requests are sent as non-streaming multimodal payloads
- PocketOps resets the service-side conversation before each new diagnosis
  request

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
  - app id, namespace, SDK levels, ABI filters, dependency surface

The old Gallery allowlist files were removed. Treat the model id in
`PocketOpsActivity` and the `/v1/models` response from the local service as the
runtime source of truth.

## Removed HuggingFace / Gallery modules

Inherited Google AI Edge Gallery modules for model management, HuggingFace auth,
downloads, agent skills, custom tasks, Proto DataStore, Hilt, WorkManager, and
task navigation have been removed from the Android source tree. Reintroducing
one of those features should be treated as a new scoped integration, including
dependencies, manifest entries, tests, and docs.

## Directory ownership

Use this as the current code-ownership map:

- `Android/src/app/src/main/java/com/pocketops/app/`
  - production launcher path, PocketOps UI, GraphRAG logic, embedded service
- `Android/src/app/src/main/assets/maintenance/`
  - knowledge graph payload

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
- `PocketOpsKG`

Example filtered logcat:

```shell
adb logcat PocketOps:D GenieHttpService:D PocketOpsKG:D *:S
```

## Removed non-default modules

These paths were removed because they were not part of the production mainline:

- `MainActivity` + Gallery navigation shell
- `MaintenanceTask` + `MaintenanceScreen`
- `GenieNative.kt`
- `GenieModelHelper.kt`
- `OnnxVIT.kt`
- `skills/` guide + sample skills
- `customtasks/mobileactions/`
- `genie_jni.cpp`
- `model_allowlist.json` and `model_allowlists/`
- `Android/src/app/src/main/proto/`
- `Android/src/app/src/main/assets/tinygarden/`

Do not add references to these paths back to docs unless the corresponding code
is restored intentionally.
