# PocketOps Android Bug Reporting Guide

Thank you for helping improve PocketOps.

To debug issues effectively, the most helpful artifact is still a full Android
bug report. For PocketOps specifically, it is also useful to include a small
amount of runtime context because the app depends on a local on-device inference
service and a shared-storage model directory.

This guide covers:

- the recommended on-device bug report flow
- optional `adb` collection for advanced users
- extra PocketOps-specific context that makes reports much easier to triage

## Before you capture the report

Please try to note the following right after reproducing the issue:

- device model and Android version
- PocketOps app version if visible
- whether the issue happens before login, during the loading screen, or inside
  diagnosis/chat
- whether the issue is text, image, video, Bluetooth-code, or PDF-export
  related
- whether PocketOps was granted access to `/sdcard/GenieModels`
- whether a model directory exists under
  `/sdcard/GenieModels/<model-dir>/config.json`
- screenshots or a short screen recording if the UI is involved
- the exact prompt, image, or video used to reproduce the problem when possible

## Part 1: Recommended method on the device

This is the fastest and easiest way to generate a complete bug report.

### 1. Enable Developer Options

1. Open your phone's **Settings** app.
2. Scroll down and tap **About phone**.
3. Find **Build number** and tap it **7 times** until you see the developer
   confirmation message.

### 2. Capture the bug report

Capture the report immediately after reproducing the issue.

1. Return to **Settings** and open **Developer options**.
2. Tap **Take bug report**.
3. Choose **Full report**.

### 3. Share the report

1. Wait until Android shows the **Bug report captured** notification.
2. Tap the notification.
3. Share the generated `.zip` file with the development team, or attach it to
   the issue tracker entry if one is being used.

## Part 2: Advanced collection with ADB

This section is for users comfortable with the Android Debug Bridge (`adb`).

### Capture a full bug report

For a single connected device:

```shell
adb bugreport C:\Reports\PocketOpsBugReports
```

For multiple connected devices:

```shell
adb devices
adb -s <your_device_serial_number> bugreport
```

### Capture PocketOps-focused logcat

If you can reproduce the issue while connected to a computer, a focused logcat
is often useful in addition to the full bug report.

```shell
adb logcat PocketOps:D GenieHttpService:D AGMaintenanceKG:D *:S > pocketops-logcat.txt
```

If you need the full logcat instead:

```shell
adb logcat -d > pocketops-full-logcat.txt
```

### Pull older saved bug reports from the device

```shell
adb shell ls /bugreports/
adb pull /bugreports/<bug_report_filename.zip>
```

## Part 3: What makes a PocketOps report especially useful

If possible, include a short note answering these questions:

- Did the loading screen ever finish?
- Did the app ask for all-files access to `/sdcard/GenieModels`?
- Was the failure a timeout, a crash, a blank response, or a wrong diagnosis?
- If the issue involved PDF export, did Android show the share sheet?
- If the issue involved image/video diagnosis, did media selection succeed
  before the failure?

These details help separate:

- UI issues in `PocketOpsActivity`
- startup or model-readiness issues in `GenieHttpService`
- GraphRAG / data issues in `AGMaintenanceKG`
- output-format or parsing issues from the local inference service

## Understanding the report file

The generated bug report is a `.zip` file. Inside it, the most important file
is usually `bugreport-...txt`, which contains:

- `logcat`
- `dumpstate`
- `dumpsys`
- other low-level system diagnostics

That file gives engineers a much clearer picture of the device state at the
time of failure.
