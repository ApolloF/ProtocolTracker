# ProtocolTracker

Android app for dose plans: open it, tap what you took. Offline, no account.

- **Today** – doses grouped by part of the day (Morning, Pre-workout, Evening, Any time, or exact times); done doses stay in place. The check logs the planned dose; tapping a dose opens a sheet to adjust the amount, time, site or note for that dose only, or skip it. *Log all* per group, undo after every action. A dose due today says when the previous one was missed or skipped and when you last took the compound. The week strip swipes back to earlier weeks; tapping a day, or the calendar button, shows that day below the strip to check off or backfill its doses at their planned time. Extra doses and journal entries are listed in one "Logged today" section by time. A new day starts at the time set in Settings › Times of day (default 4:00), so a late-night dose counts for the evening before.
- **Plan** – phases with dates (e.g. Cruise → Blast → PCT) that switch automatically. Items are sorted into injectable steroids, oral steroids, support and peptides. Injectables are planned per week; the app shows the amount and volume per injection. Timing is a part of the day or an exact time. Schedules: days of the week, daily, every N days, every X hours, as needed.
- **Compounds** – presets named "Common (scientific)", e.g. *Anavar (oxandrolone)*; peptides by compound name (*Tirzepatide*).
- **Levels** – estimated level curves per compound group using the Steroid Plotter method, in conventional or SI units; the detail screen adds steady-state range, time to steady state and washout date. Slide along a chart to read the estimate and see the doses, notes and readings logged around that time, with light vibration ticks. Total testosterone results appear on the Testosterone curve. Compounds without reliable data are logged but not plotted.
- **Journal** – doses, blood pressure, notes, symptoms and bloodwork by day; filters, edit or delete (with Undo), adherence over 7 and 30 days counted from your first log. The + opens the same Log menu as Today. Blood pressure shows 7-day averages of the last 26 weeks; Symptoms shows a mood chart.
- **Symptoms** – tick symptoms, grouped by body area, with optional mood (1–10), hair shedding and a note.
- **Bloodwork** – enter lab results in conventional or SI units; the Journal shows the latest result per marker, flagged only against the range printed by the lab, and how long ago the last draw was; tap a marker to see every result, with a chart of two or more. Lab ranges, values reported as "<0.3" and tests the app does not list are kept as printed. **Import results**: copy the AI prompt, give it to any chatbot with your lab report, then paste the chatbot's answer; nothing is saved before you tap Save. Your report goes to the chatbot you use; the app stays offline. Design: [docs/BLOODWORK_IMPORT.md](docs/BLOODWORK_IMPORT.md).
- **Injection sites** – the Log dose sheet of an injectable dose suggests the next site per compound (all 12 under More); Today rows and reminders show the suggestion, and checking the row or tapping Taken records it. Doses logged from the widget or a past day record no site. Journal dose lines show the site; reports do not print it.
- **Reminders** – notifications per part of the day with *Taken*, *Snooze*, *Skip* (and *Skip all*); any-time doses remind in the evening if not logged; optional daily summary. Home-screen widget with one-tap check.
- **Data** – readable report (HTML) and report for AI tools (Markdown); JSON backup/restore (settings included) through the system file picker; import from a CycleTracker (`cycletracker-1`) export or the CycleTracker web app's full history export.
- **Settings** – appearance (theme, colour scheme, animation level), units and formats (12/24-hour clock, date order, conventional or SI lab units, mL or U-100 syringe units), Today, times of day and day start, reminders.

Level curves are model estimates (see [docs/MODELS.md](docs/MODELS.md)), not measurements or medical advice.

## What this app is not
ProtocolTracker is a personal log. It does not recommend, prescribe or adjust doses, and it does not diagnose or interpret results. Level curves are model estimates. It is not a medical device. Talk to a doctor about medicines and lab results. The app shows this once on first start and in Settings › About.

## Privacy
Everything stays on the device: no network access, no account, no analytics, and Android cloud backup is off. Backups and reports you export are unencrypted files saved where you choose. Full policy: [PRIVACY.md](PRIVACY.md).

## Install
Download `ProtocolTracker-<version>.apk` from [Releases](https://github.com/ApolloF/ProtocolTracker/releases), or the debug APK artifact of the latest CI run, and open it on the phone. Android asks to allow installs from that source once. Coming from *ProtocolTracker Dev*: see [the v0.5.0 notes](docs/releases/v0.5.0.md).

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME`).
```
./gradlew :core:domain:test testDebugUnitTest assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/`. See [AGENTS.md](AGENTS.md) for layout and conventions.

Release builds are signed in CI when a `v*` tag is pushed and the `PT_KEYSTORE_BASE64`, `PT_KEYSTORE_PASSWORD`, `PT_KEY_ALIAS` and `PT_KEY_PASSWORD` secrets are set. Set `versionName` in `app/build.gradle.kts` to the tag without the `v` first; CI refuses to publish when they differ. A tag with a suffix (e.g. `v0.5.1-beta.1`) is published as a pre-release. Locally, put the same keys in an untracked `keystore.properties`.
