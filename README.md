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
- **Bloodwork** – enter lab results in conventional or SI units; the Journal shows the latest result per marker with its reference range and how long ago the last draw was (also in the Log menu); tap a marker to see every result, newest first, with a chart of two or more; and total testosterone results appear on the Testosterone level curve. Editing a draw shows the lab's own ranges, values reported as "<0.3", and tests the app does not list (under "Other tests", as printed).
- **Bloodwork import** – Log › Bloodwork › Import results: copy the AI prompt, give it to any chatbot with your lab report, then paste the chatbot's answer. The app shows the results per draw (a tap leaves one out) and saves them as normal bloodwork entries; nothing is saved before you tap Save. Your report goes to the chatbot you use; the app stays offline. Steps: [the v0.5.0-dev.3 notes](docs/releases/v0.5.0-dev.3.md); design: [docs/BLOODWORK_IMPORT.md](docs/BLOODWORK_IMPORT.md).
- **Blood pressure chart** – with the Blood pressure chip selected in Journal, the Blood pressure card shows the 7-day averages of the last 26 weeks (tap or slide to read a week).
- **Injection sites** – the Log dose sheet of an injectable dose has a Site row with the suggested next site already selected (the site that followed your last one the previous time, per compound) and all 12 sites under More; pick another site there to change it, and the rotation follows what you record. Pending injectable rows on Today and dose reminders end with the suggested site, and checking the row or tapping Taken records it. A dose logged from the widget or a past day in the calendar records no site, and the suggestion stops until you pick a site in the Log dose sheet. A past dose's site can be changed by opening it from Journal. Journal dose lines show the site ("125 mg · 0.63 mL · R VG"). The site is stored with the dose log and kept in backups; reports do not print it. Steps: [the v0.5.0-dev.4 notes](docs/releases/v0.5.0-dev.4.md).
- **Logging and editing** – Journal's + opens the same Log menu as Today (extra dose included), and a new entry shows a snackbar with Undo on both tabs. Today lists extra doses and journal entries in one "Logged today" section by time. Any logged dose, planned or extra, opens in the dose sheet to change its amount, date, time, site, note or Taken/Skipped; every edit sheet ends with "Delete entry" (with Undo). The Bloodwork sheet shows the markers you have results for first and folds the rest under "More markers".
- **Web app history** – a one-time import of blood pressure, notes, symptoms and bloodwork from the CycleTracker web app's full export (Settings › Export and data › Import CycleTracker export; see [the v0.5.0-dev.2 notes](docs/releases/v0.5.0-dev.2.md)).

- **Also different in the dev build** – Levels charts always slide to read (no Experimental page, no compare mode), with the reading under the chart ("est. 799 ng/dL", the last dose and nearby entries, T and E2 of a draw) and a "Lab result" legend; Journal › All shows bloodwork as one row; Today rows carry no category tag; "Earlier…" times never land in the future; symptom copy gives no advice. Details per build in [docs/releases](docs/releases).
All of it appears in the Journal, in reports and in backups.

Level curves are model estimates (see [docs/MODELS.md](docs/MODELS.md)), not measurements or medical advice.

## Install
Download `ProtocolTracker-<version>.apk` (or `ProtocolTracker-Dev-<version>.apk` for the dev build) from [Releases](https://github.com/ApolloF/ProtocolTracker/releases), or the debug APK artifacts of the latest CI run, and open it on the phone. Android asks to allow installs from that source once. Dev builds between releases are published as pre-releases (`v<version>-dev.<n>`) with only the dev APK.

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME`).
```
./gradlew :core:domain:test testDebugUnitTest assembleDebug
```
The APKs are written to `app/build/outputs/apk/stable/debug/` and `app/build/outputs/apk/dev/debug/`. See [AGENTS.md](AGENTS.md) for layout and conventions.

Release builds are signed in CI when a `v*` tag is pushed and the `PT_KEYSTORE_BASE64`, `PT_KEYSTORE_PASSWORD`, `PT_KEY_ALIAS` and `PT_KEY_PASSWORD` secrets are set. A tag containing `-dev.` (e.g. `v0.5.0-dev.1`) publishes only the dev APK, as a pre-release; set the dev flavor's `versionName` in `app/build.gradle.kts` to the tag without the `v` first (`X.Y.Z-dev` for a `vX.Y.Z` tag). CI refuses to publish when an APK's version name does not match the tag. Locally, put the same keys in an untracked `keystore.properties`.
