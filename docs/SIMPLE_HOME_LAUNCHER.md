# Simple Home launcher brief

## Product

Simple Home is an Android 13+ home-screen replacement with a user-controlled dashboard and automatically organized category pages.

The dashboard is the stable personal surface: pinned apps and widgets remain where the user puts them. Category pages are generated from Android metadata and maintained local rules, with manual overrides taking precedence. Optional usage access changes ordering within categories; it never rearranges the dashboard.

## First-run contract

1. Load enough app data to show a truthful preview.
2. Explain the dashboard and category pages.
3. Explain optional usage access and allow it to be skipped.
4. Preview widget placement.
5. Request the HOME role only after the user explicitly chooses to make Simple Home the default.

If app discovery fails, show the error and retry path before offering the HOME role.

## Required V1 behavior

- Editable dashboard grid with app shortcuts and widgets.
- Pin, move, resize where supported, remove, and restore after process death or reboot.
- Complete widget pick, bind, configure, place, cancel, resize, remove, and stale-provider recovery flow.
- Category pages with deterministic inference and manual app overrides.
- Distinct personal and work-profile identities.
- Searchable, near-full-screen all-apps drawer.
- Reactive settings and explicit loading, empty, degraded, failed, and recovery states.
- Back behavior ordered as keyboard/search, drawer, edit mode, then launcher root.

## Deliberately deferred

- Cloud synchronization
- Remote or ML categorization
- Automatic dashboard rearrangement
- Hidden Android APIs
- Pre-Android-13 support
- Icon-pack and theme marketplaces
- Folders until the dashboard and widget model pass device acceptance

## Architecture boundaries

- Platform adapters discover and launch apps, query usage, and host widgets.
- Domain models own stable app/profile identity, categories, workspace placement, and explicit load/recovery states.
- Repositories expose Flow-based app, workspace, category, and preference state.
- Compose renders state and dispatches actions; it does not own persistence or platform lifecycle decisions.
- Workspace writes are versioned and atomic. Parse/write failure preserves the last valid data.

## Build matrix

The checked-in matrix is intentionally conservative and should be changed only through a separate, verified dependency update:

| Component | Version |
|---|---:|
| Android Gradle Plugin | 9.1.0 |
| Gradle wrapper | 9.3.1 |
| Kotlin / Compose plugin | 2.2.10 |
| Compose BOM | 2024.09.03 |
| minSdk | 33 |
| compileSdk / targetSdk | 36 |
| JVM target | 17 |

The Compose BOM owns versions for Compose UI, Foundation, Runtime, Material 3, Graphics, tooling, icons, and UI-test artifacts. The Kotlin Compose plugin owns compiler configuration.

## Source of truth

[`restart-plan.html`](restart-plan.html) contains the implementation sequence, acceptance checkpoints, and status ledger. [`IMPLEMENTATION_NOTES.md`](IMPLEMENTATION_NOTES.md) records the current baseline without claiming incomplete features are done.
