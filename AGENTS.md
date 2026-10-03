# ProtocolTracker

Offline Android app for dose plans, one-tap logging, reminders and estimated level curves.
Rewrite of the CycleTracker web app (ApolloF/cycletracker), which serves only as a feature reference.

## Stack
- Kotlin 2.4, Jetpack Compose + Material 3, AGP 9 (built-in Kotlin), Gradle 9, JDK 21 toolchain, JVM target 17.
- minSdk 26, targetSdk 36, compileSdk 37.
- Room (KSP) for data, DataStore for settings, AlarmManager + WorkManager for reminders, Glance for the widget.
- Manual dependency injection (`AppContainer`); no Hilt.
- Local-only data. No network, no accounts. Android cloud backup is disabled; users export JSON backups. `ManifestPermissionsTest` fails if `INTERNET` enters either flavor's merged manifest (a dependency could add it).

## Commands
Set `JAVA_HOME` to a JDK 21 and `ANDROID_HOME` to the SDK first (on this machine both live under `%LOCALAPPDATA%`).
```
./gradlew :core:domain:test           # fast pure-JVM tests (schedule, PK, import)
./gradlew :core:domain:test testDebugUnitTest   # all unit tests (domain JVM + Robolectric, both app flavors)
./gradlew assembleDebug               # debug APKs -> app/build/outputs/apk/{stable,dev}/debug
./gradlew lintDebug assembleRelease   # R8-minified release APKs (stable and dev)
./gradlew connectedDevDebugAndroidTest connectedStableDebugAndroidTest   # on-device smoke test (emulator running)
```
Kotlin compiles in-process (`gradle.properties`) because the Kotlin daemon locked build dirs on Windows.
If Gradle reports `Unable to delete directory` or `AccessDeniedException` under `build/`, delete that directory (e.g. `rm -rf core/data/build/intermediates/*lint*`) and rerun; it is a local file-lock quirk, not a code error. A test task that fails at once with `java.io.EOFException` read a results store left half-written by a killed test JVM: delete that module's `test-results` folder and rerun. If it keeps happening, run any task with an init script that moves every project's `layout.buildDirectory` to a folder outside the project (e.g. `C:/Users/<you>/.ptbuild/<project path>`); unit tests work that way too.
When the project is opened through a Google Drive virtual drive, dexing (`assembleDebug`) fails with "this and base files have different roots". Build APKs with an init script that sets `layout.buildDirectory` of every project to a folder on a local disk, and add `-Pkotlin.incremental=false` for release tasks. Run unit tests without it (they need the build folder on the project's drive).

## Layout
- `core/domain` — pure Kotlin, no Android. Models (incl. symptom catalog, blood markers and the bloodwork rules shared by the sheet and importers, marker history, draw age and weekly BP averages for the trends, injection sites and their rotation rule), schedule engine (`schedule/`), PK engine, presets and lab units (`pk/`), unit conversion and display formats (`units/`), logs near a moment (`timeline/`), backup, legacy import and web history import (`io/`), the bloodwork import's text, value and date grammar, marker vocabulary, block reader with its refusal messages, the row pipeline (`RowReader`: units, meaning checks, left-out reasons), the draft and its review (`ImportDraft`, `ImportReview`: choices, duplicates, entries) and the AI prompt (`io/labimport/`). All business logic lives here and is unit tested.
- `core/data` — Room entities/DAOs/mappers, `TrackerRepository` (single write path), `SettingsStore`.
- `app` — Compose UI per screen (`ui/today`, `ui/plan`, `ui/levels`, `ui/journal`, `ui/settings`), symptom, bloodwork and marker sheets and the dev bloodwork import screen (`ui/health`; its route is registered only in dev, and a save hands Journal the Bloodwork chip and Undo through `JournalFocus`), shared components (`ui/components`, incl. `TrendChart`, the small time chart for bloodwork, BP and mood), reminders (`reminders/`), widget (`widget/`), `DoseActions` (logging shared by UI, notifications, widget).
- Flavors: `stable` (released app) and `dev` (`.dev` application id, "ProtocolTracker Dev", purple icon, own `versionName`; `VersionNameTest` checks both). Dev-only features (symptom logging, bloodwork and its import, lab results on curves, the BP chart, injection sites, the web history import; dev also always scrubs Levels and drops compare mode and Settings › Experimental) check `BuildConfig.DEV_FEATURES`; their data model, storage, backup and reports are shared, so a dev backup restores in stable. Dev-only copy and values use `devOr(dev = …, stable = …)` (`ui/Infra.kt`, stable side unchanged). Every gate gets a test in `DevEntryPointsTest` (runs in both flavors): present in dev, absent in stable, checked after something both flavors show.
- `docs/MODELS.md` — level model (Steroid Plotter method) and preset sources.

## Conventions
- UI copy: plain labels and short instructions. No slogans, motivational or promotional text. Empty states say what is missing and the action.
- Screens hold no business logic: compute in `core/domain`, write via `TrackerRepository`.
- Logged doses carry a snapshot (name, category, PK params, formulation) and the planned amount; never recompute history from the current plan. Adjusting a logged amount never changes the plan. Dev: a dose field that still shows the plan or the stored amount saves that amount itself (`DoseAdjust.fromField`), never its 4-decimal copy.
- Occurrence keys: `itemId@epochSecond` for exact times, `itemId@yyyy-MM-dd/SLOT` for parts of the day (stable when slot clock times change). The DB enforces one log per key.
- Logical day: `SlotTimes.dayStart` (Settings › Times of day; dev default 4:00, stable midnight) decides which day "today" is (`slotTimes.dateOf(now, zone)`), which day a log counts for (Journal headers, extras, logged late, interval restarts) and where agenda log windows start (`agendaLogsFrom`). Occurrences keep their calendar `localDate` and key. Never use `now.atZone(zone).toLocalDate()` for "today".
- Skips: a skipped log sits at its planned time (`DoseLog.shownAt`; `normalizedForWrite()` sets `takenAt = scheduledAt` and drops the site on every write). Skips are never "logged late", count apart from taken and missed (`DayStatus`, `daySummary`, `adherenceText`) and print no amount in reports.
- Interval schedules (`EveryNDays` with n > 1, `EveryHours`) with `fromLastDose` restart from the last taken dose. `occurrences()` and the agenda functions take `IntervalAnchors` built from *all* taken plan doses (`TrackerRepository.anchors`/`anchorsNow()`, or `IntervalAnchors.from(allLogs)`), never from a windowed log list. Skipped and unscheduled doses never move the plan.
- Naming: presets use `commonName` + scientific `name`, shown as "Anavar (oxandrolone)"; peptides use the compound name only. Sections: injectable steroids → oral steroids → support (by `SupportKind`) → peptides.
- Injectables are planned with `DoseBasis.PER_WEEK`; per-dose amounts come from `PlanItem.dosePerOccurrence()`.
- Level curves are labelled as estimates. Compounds without reliable data have `pk = null` (logged, not plotted). Change preset parameters only with a source note in `docs/MODELS.md` and bump `Presets.VERSION`.
- Exports: JSON backup (`protocoltracker-backup-2`, restorable; since dev.15 it carries the settings as `settings`, `SettingsStore.exportMap`/`importMap`, absent in older backups), HTML and Markdown reports (`domain/io/Report.kt`).
- Bloodwork results may carry the lab's range (`refLow`/`refHigh`), a `<`/`>` `qualifier`, and for `other:` keys the printed `name` and `unit`. These fields are `@EncodeDefault(NEVER)` with no `require` (plain results store byte for byte as before; bad values never throw). Flag stored results with `MarkerResult.flag()` (null = no flag; `unclear` = a range but no flag), not `BloodMarker.flag`. Show them with `BloodMarker.formatResult` (keeps the `<`/`>`) and `rangeText(labRange)` marked "(lab)", or `printedText()`/`printedLabRange()` for `other:` keys; censored results are never plotted.
- Injection sites (dev): `DoseLog.site` stores an `InjectionSites` key, never a label (unknown keys are kept and shown as they are). Rotation is per compound from *all* taken logs (`SiteRotation`; skipped ones ignored), never a windowed list. A one-tap (Today check, Log all, reminder Taken) records exactly the site its row showed, one per compound per tap (Today: a card's first pending row, after the site history has loaded; reminder: the compound's latest dose, `SiteRotation.latestDoses`, so the newest log keeps the chain); the Day sheet and the widget record none, which ends the suggestion until a site is picked. Writes take a `SiteWrite` (`Keep` for re-logs, so nothing drops a site); edits save with `log.copy`. Shown as the short label in the Log dose sheet, Today rows, reminders and Journal dose lines; reports do not print sites.
- Room schema changes need a migration from version 2 on (version 1 is dropped destructively). Current version 4 (dose log `site`); `MigrationTest` opens real version 2 and 3 databases.
- Accessibility: 48 dp touch targets, content descriptions on icon buttons, status never by colour alone.
- Styling: use `Tracker.colors`, `TrackerType`, `Spacing` and `Radii` (`ui/theme`), never raw colours or font sizes. Colour schemes live in `ui/theme/Palettes.kt`; `PaletteContrastTest` enforces WCAG AA for text pairs.
- Design review screenshots: `./gradlew :app:testDevDebugUnitTest --tests '*ScreenshotTest' -Pscreenshots.dir=<folder>`.
- Add or update tests with every behaviour change; run `:core:domain:test testDebugUnitTest assembleDebug` before committing.
- Times, dates and volumes follow Settings > Units and formats through `DisplayFormat.current` (set by `SettingsStore` on every read). Format with `Formats` (app) or `DisplayFormat`, never a hard-coded `ofPattern`. Reports keep ISO dates and mL.
- Regexes run on ICU on Android, which rejects a `{` or `}` that is not part of a quantifier or `\p{…}` (the JVM accepts it; this crashed Bloodwork in v0.5.0-dev.1–4). Escape literal braces; `AndroidRegexTest` checks every `Regex` held by a domain class.
- Insets: the app is edge-to-edge. Tab screens use `tabScreenTop()` (their Scaffold padding already holds the status bar); sheets end their column with `navigationBarsPadding()`; a sheet whose content can grow scrolls. Robolectric draws no system bars, so check insets on an emulator.
- Before a tag: with an emulator running (`emulator -avd <name>`), run `./gradlew connectedDevDebugAndroidTest connectedStableDebugAndroidTest` (`SmokeTest` opens every tab, sheet and settings page in the real app and fails on any crash), then install the dev release APK (`adb install -r`), open every changed screen and read `adb logcat -b crash`.
- Animations follow the Motion setting (`ui/theme/Motion.kt`): screen transitions never animate size; use `Motions.spec` for new animations. Screens change with `Motions.screenEnter`/`screenExit` (`NavMove`: the screen on top fades over an opaque one, the same length on both sides so a back gesture can seek it). Tab screens carry their own bar (`TabFrame` in `AppNav`), so the NavHost never resizes. Dev Today switches days with `Motions.daySwitch`.
