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
If Gradle reports `Unable to delete directory` or `AccessDeniedException` under `build/`, delete that directory (e.g. `rm -rf core/data/build/intermediates/*lint*`) and rerun; it is a local file-lock quirk, not a code error. If it keeps happening, run any task with an init script that moves every project's `layout.buildDirectory` to a folder outside the project (e.g. `C:/Users/<you>/.ptbuild/<project path>`); unit tests work that way too.
When the project is opened through a Google Drive virtual drive, dexing (`assembleDebug`) fails with "this and base files have different roots". Build APKs with an init script that sets `layout.buildDirectory` of every project to a folder on a local disk, and add `-Pkotlin.incremental=false` for release tasks. Run unit tests without it (they need the build folder on the project's drive).

## Layout
- `core/domain` — pure Kotlin, no Android. Models, schedule engine (`schedule/`), PK engine and presets (`pk/`), unit conversion, backup and legacy import (`io/`). All business logic lives here and is unit tested.
- `core/data` — Room entities/DAOs/mappers, `TrackerRepository` (single write path), `SettingsStore`.
- `app` — Compose UI per screen (`ui/today`, `ui/plan`, `ui/levels`, `ui/journal`, `ui/settings`), shared components (`ui/components`), reminders (`reminders/`), widget (`widget/`), `DoseActions` (logging shared by UI, notifications, widget).
- `docs/MODELS.md` — level model (Steroid Plotter method) and preset sources.

## Conventions
- UI copy: plain labels and short instructions. No slogans, motivational or promotional text. Empty states say what is missing and the action.
- Screens hold no business logic: compute in `core/domain`, write via `TrackerRepository`.
- Logged doses carry a snapshot (name, category, PK params, formulation) and the planned amount; never recompute history from the current plan. Adjusting a logged amount never changes the plan.
- Occurrence keys: `itemId@epochSecond` for exact times, `itemId@yyyy-MM-dd/SLOT` for parts of the day (stable when slot clock times change). The DB enforces one log per key.
- Interval schedules (`EveryNDays` with n > 1, `EveryHours`) with `fromLastDose` restart from the last taken dose. `occurrences()` and the agenda functions take `IntervalAnchors` built from *all* taken plan doses (`TrackerRepository.anchors`/`anchorsNow()`, or `IntervalAnchors.from(allLogs)`), never from a windowed log list. Skipped and unscheduled doses never move the plan.
- Naming: presets use `commonName` + scientific `name`, shown as "Anavar (oxandrolone)"; peptides use the compound name only. Sections: injectable steroids → oral steroids → support (by `SupportKind`) → peptides.
- Injectables are planned with `DoseBasis.PER_WEEK`; per-dose amounts come from `PlanItem.dosePerOccurrence()`.
- Level curves are labelled as estimates. Compounds without reliable data have `pk = null` (logged, not plotted). Change preset parameters only with a source note in `docs/MODELS.md` and bump `Presets.VERSION`.
- Exports: JSON backup (`protocoltracker-backup-2`, restorable), HTML and Markdown reports (`domain/io/Report.kt`).
- Room schema changes need a migration from version 2 on (version 1 is dropped destructively).
- Accessibility: 48 dp touch targets, content descriptions on icon buttons, status never by colour alone.
- Add or update tests with every behaviour change; run `:core:domain:test testDebugUnitTest assembleDebug` before committing.
