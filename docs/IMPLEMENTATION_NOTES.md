# Simple Home implementation status

Last reviewed: 2026-07-13

Simple Home is an early launcher prototype undergoing an honest restart. Existing code is useful evidence and may contain reusable Android adapters, but the product is not feature-complete and should not be treated as release-ready.

The decision-complete restart plan and checkpoint ledger live in [`restart-plan.html`](restart-plan.html). [`SIMPLE_HOME_LAUNCHER.md`](SIMPLE_HOME_LAUNCHER.md) is the compact product and engineering brief.

## Current baseline

- Android application ID: `com.stonecode.simplehomescreen`
- Minimum SDK: 33 (Android 13)
- Compile and target SDK: 36
- Build matrix: AGP 9.1.0, Gradle 9.3.1, Kotlin 2.2.10
- Compose libraries use BOM `2024.09.03`; individual Compose artifacts do not declare competing versions.
- Java and Kotlin bytecode target JVM 17.
- The Kotlin Compose plugin owns compiler configuration; there is no legacy `composeOptions.kotlinCompilerExtensionVersion` setting.
- Widget binding uses the platform consent flow. The app does not request the signature-only `BIND_APPWIDGET` permission.

## What exists today

The prototype contains launcher discovery, usage ranking, categories, search, settings, widget-host scaffolding, persistence, and a Compose home surface. Those names describe code areas, not completed product features.

Known restart drivers:

- Workspace persistence does not yet model a complete editable grid.
- Favorites and categories do not yet match the intended user-controlled behavior.
- Widget configuration, placement, resizing, cancellation, and recovery are incomplete as one lifecycle.
- App loading, launch failure, permission state, and persistence failures need explicit UI states.
- Settings and package changes need a single reactive state pipeline.
- Personal and work-profile app identity must remain distinct.
- Gestures, Back handling, the drawer, and edit mode need one deliberate navigation contract.
- Existing tests are not sufficient evidence for launcher behavior.

## Development gates

Work proceeds in this order. A failed gate is recorded and fixed before moving on.

1. Android Studio sync and debug build.
2. Domain model, versioned persistence, and preference migration.
3. Static Compose state previews approved in Android Studio.
4. App discovery, categories, profile identity, and usage ranking on device.
5. Dashboard placement and editing on device.
6. Complete widget lifecycle with multiple providers.
7. Integrated launcher navigation, settings, recovery states, and reboot restoration.
8. Release build and installed-artifact smoke test.

## Verification record

Do not convert checklist items into completion claims without evidence. Record the date, device or Android Studio surface, build variant, command or interaction, and result in the HTML checkpoint ledger.

For command-line verification, keep logs under `build/codex-logs/` and inspect focused excerpts. Android Studio and the installed app on Monroe's device remain the visual and behavioral authority.
