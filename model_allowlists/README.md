# Model Allowlists

This directory stores versioned snapshots of the model allowlist metadata that
ships with retained Gallery-era task flows.

## What lives here

- `1_0_4.json` ... `1_0_11.json`
  - versioned snapshots used by different app builds
- `ios_1_0_0.json`
  - a platform-specific snapshot kept for reference

## How this relates to the rest of the repo

- Root-level [`model_allowlist.json`](../model_allowlist.json) is the current
  allowlist file that retained Gallery modules still read by default.
- `PocketOpsActivity` does not use these files as its primary runtime source of
  truth for inference requests. The PocketOps mainline talks directly to the
  local HTTP service on `127.0.0.1:8910`.
- These files still matter if you keep the inherited Gallery model picker, task
  metadata, or historical experiments alive.

## When to update these files

Review this directory when you change any of the following:

- model ids exposed by retained Gallery tasks
- model labels or download metadata shown in inherited model-management screens
- versioned release packaging that still expects an allowlist snapshot

If you only changed the PocketOps production path, also update the real runtime
configuration:

- `Android/src/app/src/main/java/com/pocketops/app/PocketOpsActivity.kt`
- root `model_allowlist.json`

Do not assume a change in one place automatically updates the other.
