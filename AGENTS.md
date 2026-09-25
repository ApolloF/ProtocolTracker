# ProtocolTracker

Offline Android app for dose plans, one-tap logging, reminders and estimated level curves.
Rewrite of the CycleTracker web app (ApolloF/cycletracker), which serves only as a feature reference.

## Stack
- Kotlin 2.4, Jetpack Compose + Material 3, AGP 9 (built-in Kotlin), Gradle 9, JDK 21 toolchain, JVM target 17.
- minSdk 26, targetSdk 36, compileSdk 37.
- Room (KSP) for data, DataStore for settings, AlarmManager + WorkManager for reminders, Glance for the widget.
- Manual dependency injection (`AppContainer`); no Hilt.
- Local-only data. No network, no accounts. Android cloud backup is disabled; users export JSON backups.

## Commands
Set `JAVA_HOME` to a JDK 21 and `ANDROID_HOME` to the SDK first (on this machine both live under `%LOCALAPPDATA%`).
```
./gradlew :core:domain:test           # fast pure-JVM tests (schedule, PK, import)
./gradlew :core:domain:test testDebugUnitTest   # all unit tests (domain JVM + Robolectric)
./gradlew assembleDebug               # debug APK -> app/build/outputs/apk/debug
./gradlew lint assembleRelease        # R8-minified release APK
```
Kotlin compiles in-process (`gradle.properties`) because the Kotlin daemon locked build dirs on Windows.
If Gradle reports `Unable to delete directory` or `AccessDeniedException` under `build/`, delete that directory (e.g. `rm -rf core/data/build/intermediates/*lint*`) and rerun; it is a local file-lock quirk, not a code error.

## Layout
- `core/domain` — pure Kotlin, no Android. Models, schedule engine (`schedule/`), PK engine and presets (`pk/`), unit conversion, backup and legacy import (`io/`). All business logic lives here and is unit tested.
- `core/data` — Room entities/DAOs/mappers, `TrackerRepository` (single write path), `SettingsStore`.
- `app` — Compose UI per screen (`ui/today`, `ui/plan`, `ui/levels`, `ui/history`, `ui/settings`), reminders (`reminders/`), widget (`widget/`), `DoseActions` (logging shared by UI, notifications, widget).
- `docs/MODELS.md` — PK equations and preset sources.

## Conventions
- UI copy: plain labels and short instructions. No slogans, motivational or promotional text. Empty states say what is missing and the action.
- Screens hold no business logic: compute in `core/domain`, write via `TrackerRepository`.
- Logged doses carry a snapshot (PK params, formulation); never recompute history from the current plan.
- Occurrence keys are `itemId@epochSecond`; the DB enforces one log per key.
- Level curves are labelled as estimates. Change preset parameters only with a source note in `docs/MODELS.md`.
- Accessibility: 48 dp touch targets, content descriptions on icon buttons, status never by colour alone.
- Add or update tests with every behaviour change; run `:core:domain:test testDebugUnitTest assembleDebug` before committing.
