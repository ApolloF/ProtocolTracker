# ProtocolTracker

Android app for dose plans: open it, tap what you took. Offline, no account.

- **Today** – doses grouped by part of the day (Morning, Pre-workout, Evening, Any time, or exact times); done doses stay in place. The check logs the planned dose; tapping a dose opens a sheet to adjust the amount, time or note for that dose only, or skip it. *Log all* per group, undo after every action. Optional week strip (Settings); tapping a day, or the calendar button, opens that day to check off or backfill its doses at their planned time. The Log button records extra doses, blood pressure and notes.
- **Plan** – phases with dates (e.g. Cruise → Blast → PCT) that switch automatically. Items are sorted into injectable steroids, oral steroids, support and peptides. Injectables are planned per week; the app shows the amount and volume per injection. Timing is a part of the day or an exact time. Schedules: days of the week, daily, every N days, every X hours, as needed.
- **Compounds** – presets named "Common (scientific)", e.g. *Anavar (oxandrolone)*; peptides by compound name (*Tirzepatide*).
- **Levels** – estimated level curves per compound group using the Steroid Plotter method, with steady-state range, time to steady state and washout date, in conventional or SI units. Compounds without reliable data are logged but not plotted. Experimental: compare mode, and scrubbing (slide along a chart to read values and see doses, notes and readings logged around that time, with light vibration ticks).
- **Journal** – doses, blood pressure readings and notes by day; filters, edit or delete, adherence over 7 and 30 days.
- **Reminders** – notifications per part of the day with *Taken*, *Snooze*, *Skip*; any-time doses remind in the evening if not logged; optional daily summary. Home-screen widget with one-tap check.
- **Data** – readable report (HTML) and report for AI tools (Markdown); JSON backup/restore through the system file picker; import from a CycleTracker (`cycletracker-1`) export.
- **Settings** – appearance (theme, colour scheme, animation level), units and formats (12/24-hour clock, date order, conventional or SI lab units, mL or U-100 syringe units), Today, times of day, reminders.

### Dev build
*ProtocolTracker Dev* installs next to the regular app (own data, purple icon) and adds features still in development, ported from the CycleTracker web app:
- **Symptoms** – tick low- and high-estrogen signs and general symptoms, with optional mood (1–10), hair shedding and a note.
- **Bloodwork** – enter lab results in conventional or SI units; the Journal shows the latest result per marker with its reference range, and total testosterone results appear on the Testosterone level curve.

Both appear in the Journal, in reports and in backups.

Level curves are model estimates (see [docs/MODELS.md](docs/MODELS.md)), not measurements or medical advice.

## Install
Download `ProtocolTracker-<version>.apk` (or `ProtocolTracker-Dev-<version>.apk` for the dev build) from [Releases](https://github.com/ApolloF/ProtocolTracker/releases), or the debug APK artifacts of the latest CI run, and open it on the phone. Android asks to allow installs from that source once.

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME`).
```
./gradlew :core:domain:test testDebugUnitTest assembleDebug
```
The APKs are written to `app/build/outputs/apk/stable/debug/` and `app/build/outputs/apk/dev/debug/`. See [AGENTS.md](AGENTS.md) for layout and conventions.

Release builds are signed in CI when a `v*` tag is pushed and the `PT_KEYSTORE_BASE64`, `PT_KEYSTORE_PASSWORD`, `PT_KEY_ALIAS` and `PT_KEY_PASSWORD` secrets are set. Locally, put the same keys in an untracked `keystore.properties`.
