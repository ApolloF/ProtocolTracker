# ProtocolTracker

Android app for dose plans: open it, tap what you took. Offline, no account.

- **Today** – due and overdue doses as check rows. Tap to log at the scheduled time, expand for another time or skip, *Check all* for everything due. Undo after every action.
- **Plan** – phases with dates (e.g. Cruise → Blast → PCT) that switch automatically, plus an *Always* group. Schedules: daily, weekdays, every N days, fixed hour intervals (e.g. every 84 h), as needed. Doses in mg, mcg, IU, mL (via concentration) or tablets.
- **Levels** – estimated level curves per compound group from logged and planned doses, with steady-state range, time to steady state and washout date.
- **History** – logged doses by day, edit or delete, adherence over 7 and 30 days.
- **Reminders** – exact-time notifications with *Taken*, *Snooze*, *Skip*; optional daily summary. Home-screen widget with one-tap check.
- **Data** – JSON backup/restore through the system file picker; import from a CycleTracker (`cycletracker-1`) export.

Level curves are model estimates (see [docs/MODELS.md](docs/MODELS.md)), not measurements or medical advice.

## Install
Download the APK from [Releases](https://github.com/ApolloF/ProtocolTracker/releases) (or the `protocoltracker-debug-apk` artifact of the latest CI run) and open it on the phone. Android asks to allow installs from that source once.

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME`).
```
./gradlew :core:domain:test testDebugUnitTest assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/`. See [AGENTS.md](AGENTS.md) for layout and conventions.

Release builds are signed in CI when a `v*` tag is pushed and the `PT_KEYSTORE_BASE64`, `PT_KEYSTORE_PASSWORD`, `PT_KEY_ALIAS` and `PT_KEY_PASSWORD` secrets are set. Locally, put the same keys in an untracked `keystore.properties`.
