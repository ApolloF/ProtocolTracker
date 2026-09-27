# Backlog

The dev build's living backlog: open work in priority order, decisions (incl. rejected ideas), owner checks, done items, audit findings. Read it first; update it after every item. "Import doc §n" is [BLOODWORK_IMPORT.md](BLOODWORK_IMPORT.md).

Written 2026-09-27 at HEAD 8b24b19; trimmed the same day (plan change below).

---

## Plan change 2026-09-27

The owner cut the plan: he wants releases with documentation soon, and only what he would use weekly.
- **Open is the weekly set:** bloodwork import v1, web history import, bloodwork trend with the draw hint, BP trend, injection sites. Everything else moved to Later (end of §3) with its id, so §4, §5 and §7 still point at it.
- **Bloodwork import v1 = paste, review, save.** BW-8…BW-12, BW-14, BW-15 and BW-18 became BWI-1…BWI-5; BW-13 is done. Uncertain values are left out with a reason instead of asking. The clipboard read on return (BW-16), the share target (BW-17), opening a file and the import from a Journal Bloodwork view (BW-19) are Later. Kept: Dutch aliases, lab ranges, several draw dates, the generated prompt and its test, a modest fixture set. Import doc "Scope of v1".
- **No Journal-views fork:** JV-1 and JV-2 are Later. The trends use what dev already has: the marker sheet opens from the dev Bloodwork card rows, the BP chart sits on the Blood pressure card under the Blood pressure chip, and the draw hint is the plain "Last draw N weeks ago" line.
- **Merged:** HIST-2 into HIST-1; BLOO-1…4 and BPTR-1…3 into TR-1…5; SITE-7 into SITE-6.
- **Process:** targeted tests while working and the full gate once per item before pushing; dev screenshots and the stable guard only when UI or shared app code changed; a dev release after every 3–4 items as features land, each after one audit; no per-item checkpoints.

---

## 1. How to work from this file

Detailed specs for each item were written during design (by id; merged items name their old ids); when an item is unclear, read the code and the import doc.

1. Take the first open item of your track in §3 that is not blocked; decide an undecided idea first (§4).
2. Build the smallest good version: domain and JVM tests, then data, then UI with Robolectric tests.
3. While working, run targeted tests (one module or class). Before pushing, run the full gate once.
4. Only when UI or shared app code changed: look at dev screenshots of the changed screens (light and dark) and run the stable guard.
5. Commit; add a §6 row (hashes, one line) and mark the item done in §3; `git pull --rebase origin main` (docs conflicts: keep both sides; code conflicts: resolve and rerun the targeted tests; a rebase that brought only other commits and no conflicts needs no second gate); push. Findings go to §3, §4 or §7.
6. **Release** after every 3–4 items, when a feature has landed: one audit of the diffs since the last release (bugs, edge cases, tests, docs, AGENTS.md, anything to remove or merge), fix what it finds, then publish a dev pre-release (§3 › Releases).

- **Gate:** `./gradlew :core:domain:test testDebugUnitTest lintDebug assembleDebug`. One class: `:app:testDevDebugUnitTest --tests …`. After manifest, keep-rule or dev source-set changes also `:app:minifyDevReleaseWithR8 :app:minifyStableReleaseWithR8`.
- **Every acceptance below also means:** the gate green once before pushing; when UI or shared app code changed, also the stable guard clean and dev screenshots of changed screens looked at.
- **Stable guard:** stable tests, pinned strings and screenshots stay unchanged (pixel-identical to the 8b24b19 baseline; fix a changed PNG, never explain it away; new PNGs only from dev-only tests). Never change `seed()` or an existing `ScreenshotTest` flow; new screens get their own `@Test` and data. `BuildConfig.DEV_FEATURES` is the only gate (§4.4 P5); invisible data-safety fixes go into both flavors. Each new dev entry point gets a `DevEntryPointsTest` row (in dev, not in stable, after an anchor in both).
- **Tests:** every behaviour change. Robolectric: no `runBlocking` inside `waitUntil`; scroll into view or use semantics actions before touch; gesture tests in their own class; `@Before` as `fun x(): Unit = runBlocking { … }`.
- **Data:** a Room change bumps the version with a real migration, `MigrationTest` and the exported schema, and never drops data. Old backups restore; a dev backup restores in stable. New stored fields: `@EncodeDefault(NEVER)`, no `require`.
- **Commits:** small, on `main`, no attribution lines, pushed after a green gate. Never force-push, rewrite history or touch signing secrets. Update AGENTS.md and the README dev section in the same commit.
- **Releases:** bump `versionCode`; set only the dev flavor's `versionName` (e.g. `0.5.0-dev.2`), never `defaultConfig.versionName` (stable Settings shows it; CI's "Check version names" step fails on a mismatch with the tag); write `docs/releases/<tag>.md`; tag `vX.Y.Z-dev.N`, push, `gh run watch`. On red CI: fix, delete the unpublished tag, re-tag. Never publish on red.
- **Where things go:** decisions in §4 before building; owner-only checks in §5 with a default; audit findings in §7. If the gate cannot go green without the owner, write what and why in §8 and stop.

---

## 2. Principles

- **Fewer, better features:** used weekly, fits an existing screen or flow, adds no setting. Prefer improving, merging or removing.
- **Quiet by default:** one primary action per screen, details on tap, no dashboards, tours or badges. Today gains no cards.
- **Plain and honest:** plain labels, short instructions, no slogans, no medical advice. Estimates are labelled as estimates.
- **Offline, private, no accounts:** no network permission, ever. Stable stays exactly as released.

---

## 3. Open

The weekly set, in four tracks that can run side by side. Take the first item of your track that is not blocked. Sizes XS to L. "Dev" = behind `BuildConfig.DEV_FEATURES`. Guard and infrastructure: nothing open (INF-1 to DOC-1 done).

### Releases

- **v0.5.0-dev.1** · done: guards, CI pre-releases, bloodwork groundwork (INF-1…3, DOC-1, BW-2…BW-7); notes in `docs/releases/v0.5.0-dev.1.md`.
- **v0.5.0-dev.2** · done: web history import (HIST-1, HIST-3) plus domain groundwork (BW-13, BWI-1, BWI-2, BWI-4, TR-1, SITE-1); notes in `docs/releases/v0.5.0-dev.2.md`. Audited 2026-09-27 (§7): web draw values fixed, docs corrected.
- Next dev releases (`v0.5.0-dev.3`, `.4`, … in the order they land) are cut as features land: bloodwork import v1, trends, injection sites. Each follows one audit of the diffs since the previous release. The first run of the INF-3 path also confirms with `gh release view <tag>` that it is a pre-release with exactly one asset, `ProtocolTracker-Dev-<tag>.apk`.

### (a) Bloodwork import v1: paste, review, save (brief section 1, top priority)

BW-2 to BW-7, BW-13 and BWI-1 to BWI-4 done (§6); BWI-5 is next. Scope: import doc "Scope of v1". Nothing asks: uncertain values are left out with a reason.

### BWI-1 · Marker vocabulary (was BW-8) · M · done (§6)
Why: the aliases (incl. the brief's Dutch names) and units decide what maps; a missing alias leaves a result unlisted or out.
- Complete for every marker; aliases unique; the brief's Dutch names pinned ("Testosteron totaal", "Hematocriet", "Hemoglobine" in mmol/L, "Vrij testosteron", "Oestradiol", "Kreatinine", "ALAT", "ASAT", "Gamma-GT", "PSA totaal"); the settled cases pass; factors match the existing conversions.
- Name and unit normalization already exist (`BloodworkRules.normalizeName`/`normalizeUnit`, BW-3): look up through them, do not copy them.
Spec: import doc §5.

### BWI-2 · Read `protocoltracker-bloodwork-1` blocks (was BW-9) · M · done (§6)
Why: find the block in any pasted answer; refuse the prompt, a share link, JSON or the report itself with a clear message.
- Block fixtures pass: cut-off or gapped answers, a missing header, JSON, a share link, the prompt (alone, and before an answer), a newer version, joined lines, empty or huge input, a raw report, and headings, entities and escaped pipes inside a block; a cut-off segment's last result line is never imported. The file messages (M9, M10, M13) are Later.
Spec: import doc §3.1–3.2, §3.7–3.8, §4.5.

### BWI-3 · Row pipeline, left-out reasons and entries (was BW-10, BW-11) · L · done (§6)
Why: each value is proven by the tables or the report, or left out with its reason; the review rules live in the domain, so the screen stays thin.
- No questions: what the design asks (Q1–Q4) leaves the result out with that text as its reason; an uncertain draw date (D1–D4) leaves the whole draw out with its reason. Captions as designed; a same-day result with another value starts left out and one tap keeps it; already saved results are skipped.
- A modest fixture set (about 15), each right at draft level with exact reasons: clean Dutch one- and two-draw reports, an English LabCorp report (direct free T), a block with sex ranges, a unitless Hct with its range, a unit slip, a lost decimal, an ambiguous thousands number, two values for one marker, a date trap, a cut-off answer, a continued answer (§7, audit of v0.5.0-dev.2), a re-import. The clean ones leave nothing out; meaning checks green.
- Entries: one per draw with distinct keys; every fixture, with rows left out and kept, saves valid entries that survive a backup round trip; the same answer twice saves nothing ("Everything here is already saved.").
Spec: import doc §3–4, §5.3, §7–9.

### BWI-4 · Generate the AI prompt (was BW-12) · S · done (§6)
Why: generated from `BloodMarkers`, so a missing marker fails a test.
- Every marker with key, name, units and aliases; signature; header once; no example numbers; ≤ 6,500 characters; pasted back it is refused; prompt plus an answer reads the answer; the import doc's §12 text equals the generated text.
Spec: import doc §12.

### BWI-5 · Import screen: paste, review, save (was BW-14, BW-15, BW-18) · L
Why: makes the import reachable and lands the result where bloodwork is read.
- Entry: "Import results" in the dev Bloodwork sheet only (not in edit mode, not in stable), so Today's Log menu and Journal's "+" both reach it; the route exists only in dev; a `DevEntryPointsTest` row.
- Start: the help text, Copy AI prompt, Paste answer, one message line for a refusal. Review: one card per draw (date, lab, results as printed, the flag as a word, one caption); left-out rows stay in place with their reason; "N already saved" and "N not imported" fold open; a tap leaves a row out or keeps it; one line above "Save N results"; Back after a change asks "Discard this import?".
- Save: one write (BW-13); Journal opens with the Bloodwork chip selected and "Bloodwork saved" or "N blood draws saved" with Undo.
- Tested: paste, refusals, leave out and keep, Back, two draws saved, a second import; dev screenshots of Start and Review, light and dark. Docs: AGENTS.md layout and conventions (`other:` keys, lab ranges and the flag rule, new stored fields), README dev section, the release note.
Spec: import doc §10, §11, §13.3.

### (b) Web history import

### HIST-1 · Web export parser: blood pressure, notes, symptoms and bloodwork (absorbs HIST-2) · M · done (§6)
Why: nearly all BP, symptom, note and lab history is in the web app, with no other way in; months of draws feed the marker trends.
- Takes the full export and "Export AI Review" only (not cycletracker-1, a PT backup, `{}`, `[]`); non-JSON fails cleanly.
- Naive times are UTC (Z, offsets ok; microseconds truncated); pulse 0 or missing = none; float and string numbers import; impossible BP skipped and counted.
- Dose-only symptom rows give nothing, mixed rows keep symptoms; mood 0 = none; unknown keys kept; old mood logs import; dose logs, goals, checklist ignored.
- Long notes shortened with a warning; a warning at the 5,000-log cap; native duplicates (same values) within ±15 min skipped, at 16 min imported; a rerun adds 0; same output twice; output backup-safe; all 40 web symptom keys known.
- Bloodwork: exact conversions (estradiol 103.98 pg/mL, creatinine 0.9615); the 22 web markers match ours; a lab range (both sides) only when one side differs from the web default; implausible values skipped with a warning naming marker and date. Other keys, incl. prolactin, become `other:`; PDF-only rows skipped and counted; a native draw on the same day wins, with a warning; the day holds in any time zone.
Spec: import doc §6.3, §7.

### HIST-3 · Dev auto-detect, confirm dialog and save · S · done
Why: a one-time import through the existing "Import CycleTracker export" row; no new row, screen or setting.
- Dev: "Import web app history?" with span, counts, what is left out, skipped count, "There is no undo. Save a backup first if you want a way back." and warnings; one write (BW-13), "Imported N entries"; again: "Nothing new to import from this file."
- Unknown symptom keys and `other:` results survive the database; stable still says "Unsupported export format; expected cycletracker-1"; cycletracker-1 unchanged; fits at 411 dp.

### (c) Trends: bloodwork trend, draw hint, BP trend

Placed without the Journal-views fork: the dev Bloodwork card, the Blood pressure card and the existing Journal chips. Today gains nothing.

### TR-1 · Domain: marker history, draw age and 7-day BP averages (was BLOO-1, BPTR-1) · S · done (§6)
Why: the domain half of the bloodwork trend, the draw hint and the BP trend.
- Marker history oldest first with lab and entry, stable ties; draws without the marker, unknown keys and other kinds give nothing; `other:` keys work; `markerTrends` unchanged.
- Age of the latest past draw: days to 13, weeks to 181 days, then months and years; future draws ignored; a draw at 23:30 is "yesterday" at 00:10 in any zone.
- BP: 26 weekly buckets back from now, oldest first, empty weeks dropped, older readings ignored; the newest equals the BP card's 7-day average; edges exact; systolic stays above diastolic; 1,000 readings count right. Captions "Last 7 days · 127/81 mmHg · 9 readings" and "7 days to 19 Sep · …", singular "1 reading", in either date order and zone.

### TR-2 · Draw hint "Last draw N weeks ago" (dev; was BLOO-2) · S · done (§6)
Why: the brief's "draw due" hint as a plain fact.
- The Bloodwork row of Today's Log menu reads "Last draw 3 days ago" (no past draw: "Lab results of a blood draw"), and the dev Bloodwork card's label "Bloodwork · last draw 3 days ago"; no schema change; Today tests and stable unchanged; the longest text fits at 360 dp.

### TR-3 · TrendChart: drawing, static mode, tap and slide to read (was BLOO-3, BPTR-2) · M · done (§6)
Why: bloodwork and BP need one small time chart; `LevelChart` is PK-bound stable code (§4.4 P6).
- x by time; y padded and widened to the band; one-sided bands reach the edge; a minimum span holds; a static chart lets the page scroll and ignores taps.
- Interactive: a tap selects the nearest point within 24 dp; a sideways drag moves it with a light tick; a vertical drag scrolls; no pan or zoom; back gesture excluded.

### TR-4 · Marker sheet from the dev Bloodwork card rows (was BLOO-4) · M · done (§6)
Why: read 3–10 draws per marker without opening each; costs nothing until tapped.
- Card rows open a sheet: a static chart with 2+ plottable results and a caption naming whose range is shaded, then every result newest first, flagged by its own range.
- Rows lose "before X"; one result: no chart; "<5" listed, not plotted; a ref only where it differs from the band; Settings units; unlisted results behind "Other tests (N)", list only; band visible in dark.

### TR-5 · BP chart on the Blood pressure card (dev; was BPTR-3) · S
Why: the trend where the owner already reads his readings.
- With the Blood pressure chip selected and 2+ weeks of readings, the card shows the chart (systolic solid, diastolic dashed; tap or slide to read a week) and "Each point is a 7-day average"; otherwise nothing; none on All; stable's card unchanged.
- The newest point matches the card's 7-day average; no clipping at 360 dp; lines distinct in dark.

### (d) Injection sites

### SITE-1 · Injection sites: catalog, rotation rule, `DoseLog.site` (domain) · M · done (§6)
Why: 3–7 pins a week; the one-tap check records the suggestion for free.
- 12 sites, short ("L VG") and long labels. Suggestion: the site that followed the last one before, else its mirror, from the last 24 taken logs by time taken (skipped ignored); an abandoned site drops after one override; none when the newest taken log has no site.
- Backups without sites encode byte-identically; old backups decode; reports unchanged.

### SITE-2 · Room 3→4 site column and a site-aware write path · M
Why: the only planned migration.
- A real v3 database migrates unchanged (v2 goes 2→3→4); re-logging keeps a site unless cleared; batch logs store shown sites and never overwrite; backups keep sites.

### SITE-3 · Site row in the Log dose sheet (dev) · M
Why: where a site is chosen or changed.
- Injectables only, extras too: the suggestion preselected with "Last: X · <day>", or "Choose site" when untracked; a taken dose reopens with its site; tap to clear; Skip stores none; 48 dp chips announced by full name; stable shows none and keeps sites.

### SITE-4 · Today: suggested site on rows, recorded by one tap (dev) · M
Why: the suggestion shows before injecting, and the check records it.
- Pending and missed injectable rows end " · R delt"; a check stores it ("Test C taken · R delt") and the next row moves on; a site-less log removes the suffix; Log all never repeats a site per compound; Day sheet checks store none.

### SITE-5 · Dose reminder shows and records the suggested site (dev) · S
Why: logging from the reminder must not break the chain.
- Reminder lines end " · R VG" and Taken stores the shown sites, never one twice per compound; a stale notification never overwrites a log; stable notifications pinned unchanged.

### SITE-6 · Journal dose line, guards and docs (absorbs SITE-7) · S
Why: the site shows where past doses are read, and sites are guarded and documented before release.
- "125 mg · 0.63 mL · R VG" in dev; editing keeps the site; stable unchanged.
- Entry-point rows and dev screenshots for every site surface; AGENTS.md convention; the release note mentions sites.

### Later

Dev; not the weekly set. Take one only on the owner's request, or when a nearby change touches the same code. Ids are kept for §4, §5 and §7; each item's full text is in this file's history before the plan change and in the design notes.

**Bloodwork import**
- **BW-16** · M · Read the copied answer when the import screen regains focus: new, non-sensitive text not handled before, never the copied prompt; tested through the real focus trigger. Import doc §10.2.
- **BW-17** · M · Dev-only share target "Import bloodwork" for shared text, without touching `MainActivity`; still no network permission; both R8 tasks pass. Import doc §11.
- **Open a file** (was part of BW-14) · S · "Open a file" on Start (≤ 200 KB), with the messages M9, M10 and M13.
- **BW-19** · S · "Import results" as the Journal Bloodwork view's header action and empty-state button (needs JV-1). Import doc §10.1.
- **Import questions** · M · Answer buttons for Q1–Q4, "Choose date" for D1–D4, the draw dialog and "Change answer". Import doc §4.5, §10.3–10.5.

**Journal and logging**
- **JV-1** · M · Journal chips become focused views (dev fork): All by day without cards; other views show their card, then entries; compound chips move into Doses.
- **JV-2** · S · View cards and empty states; the Bloodwork card lists out-of-range results first, the rest behind "N in range ▾".
- **SIM-14** · M · One Log menu on Today and Journal (dev), with the same snackbars and Undo.
- **SIM-15** · M · One editor per logged dose (dev), opened from Journal and from extras on Today and in the Day sheet.
- **POL-4** · S · One "Logged today" section on Today (dev).
- **POL-9** · S · Journal rows share one anatomy (dev).
- **POL-10** · S · "Delete entry" with Undo in the BP, note, symptom and bloodwork edit sheets (dev).
- **SIM-12** · M · Bloodwork sheet: import first, markers with history up front, the rest folded (dev).
- **DISC-1** · XS · Settings › About: one "In this dev build" paragraph.
- **OTHE-1** · S · Earlier-pick resolver (domain): a new entry gets the latest matching moment, never after now.
- **OTHE-2** · M · "Earlier…" reaches yesterday in the dose, BP, note and symptoms sheets (dev).
- **OTHE-4** · S · Latest taken dose per compound (domain).
- **OTHE-5** · M · The extra-dose form shows "Last taken" and starts at that amount (dev).
- **POL-7** · S · Extra-dose picker: plan compounds first (dev).
- **MISS-1** · S · Dev reports count missed doses and adherence from the first dose log.

**Symptoms and mood**
- **SYMP-1** · S · Domain: mood trend (90 days, or up to the latest mood; none with mood on fewer than 2 days).
- **SYMP-2** · S · Mood chart in Journal › Symptoms (dev); uses TR-3.
- **SIM-11** · M · Symptoms sheet: recent first, attributed headings, no advice (absorbs POL-21, SYMP-4).

**Levels**
- **SIM-1** · S · Dev always scrubs; a vertical swipe from a chart scrolls (absorbs POL-11).
- **SIM-2** · S · Scrub ticks follow the phone's touch feedback; no vibration switch.
- **SIM-4** · M · Levels reading under the chart, not in a bubble (absorbs part of POL-12).
- **SIM-5** · S · Lab results on the Testosterone curve: a legend and "Bloodwork · T 1100 ng/dL · E2 45 pg/mL" (absorbs POL-12, EEST-2).
- **SIM-6** · S · The Levels reading clears on a range change and Back to now.
- **SIM-7** · S · Compare mode removed from dev.
- **SIM-3** · S · Settings › Experimental hidden in dev (after SIM-1, SIM-2 and SIM-7).
- **SIM-8** · S · One control row on the Levels overview (absorbs POL-13).
- **SIM-9** · S · Figures on the detail screen only, plain labels (absorbs POL-14).
- **POL-15 / SIM-10** one short estimate note on the Levels overview; **SIM-16** the reading panel shows the last dose plus journal entries; **POL-16** "Back to now" gets the MyLocation glyph; **POL-17** the Levels empty state gets "Open plan".

**Plan and compounds**
- **RECO-1** · XS · Domain: strength from vial and water.
- **RECO-2** · S · "From vial and water…" on Strength (dev).
- **POL-18** Plan card: band without DAYS; timing row "Daily · morning".
- **POL-19** · S · Item and compound editor details (dev).

**Polish and screenshots**
- **POL-1** · M · Narrow, large-font and dark dev screenshot pass; the clipping fixes **POL-6**, **POL-20** and **POL-25** wait for it.
- **POL-2** · S · Numbers never wrap away from their units (dev). **POL-3** · S · No category tag on dev Today rows.
- **POL-23** · S · Settings summaries follow the time format (dev). **POL-26** · XS · 48 dp touch targets (dev). **POL-27** · S · Warn text contrast in light Plum and Clay (absorbs SIM-13's contrast check).
- **POL-5** "Nothing due today" plus the next due dose; **POL-8** Day sheet empty text, Journal notes ellipsize; **POL-22** Bloodwork sheet "Conventional"/"SI" labels, only if the unit labels clip; **POL-24** Settings copy.

---

## 4. Decisions

### 4.1 Candidate ideas (brief section 2)

Each idea was proposed minimal, then cut by a skeptic unless the reasoning was wrong (one resolution on top: §4.7). Since the plan change 2026-09-27 the accepted ideas outside the weekly set are Later (§3).

| Idea | Decision | Minimal form | Reason |
|---|---|---|---|
| Web app history import | **Accept, smaller** (HIST-1, HIST-3) | The existing "Import CycleTracker export" row takes the web file in dev and confirms with counts. BP, notes, symptoms and draws; insert only; duplicates skipped. No doses, ticks, checklist, weekly notes, settings or PDFs. | Nearly all his BP, symptom and lab history is there, with no other way in. Cut: upsert (would overwrite edits), weekly and dose notes, a per-result matcher; the report fix became MISS-1. No Room or backup change. |
| Bloodwork over time and a "draw due" hint | **Accept, smaller** (TR-1…4) | "Last draw N weeks ago" in the Log menu and card; card rows open a read-only sheet per marker. | A due date needs an interval: a fixed one is medical advice, a personal one is a setting. Rejected: due dates, badges, notifications, Today lines, sparklines, deltas, a compare picker, targets, critical tiers, range pickers, selection in the sheet, charts for unlisted markers. |
| Injection site rotation | **Accept, smaller** (SITE-1…6) | Per compound, quiet until a site is picked: suggested on Today and the reminder and recorded by the one tap; a Site row in the dose sheet; shown in Journal. | 3–7 pins a week; the web app had rotation, but its one-tap paths never recorded a site. Cut: a report line, and waiting for the merged dose editor (SIM-15); the widget records none. |
| Symptom trends and mood | **Accept, smaller; Later** (SYMP-1…2) | A mood chart in Journal › Symptoms, with mood on 2+ days. | Mood is the one dense value. Rejected: counts, per-symptom or E2-group trends, averages, a joining line, a hair chart, overlays, a range control. |
| Personal estimated E2 curve | **Reject** | Measured E2 shows as lab results (SIM-5, TR-4); MODELS.md says why (DOC-1). | The two-sentence version repeats the last draw and misses the as-needed AI, Dbol and hCG changes he steers E2 with, so it is most wrong when read. Reopen when as-needed use stops, or on request after 2+ native draws; calibrate then only on plain past E2 values above 0, an absolute ng/dL testosterone curve, taken logs (never the plan), first T dose ≥ Tmax + 4·t½ and latest ≤ Tmax + 2·t½ before the draw. |
| Supply (days left, heads-up) | **Reject** | Nothing built. | Counts need re-entry at nearly every container; dead space exceeds the warning window; lead times would be a setting; he holds the vial at every dose; a doses-per-vial line in the vial dialog was dropped too (it opens only at setup, and an estimate would break its plain arithmetic). Revisit if running out actually happens: a separate table with a real migration (not on the compound or plan item), one "On hand" field, a fixed 14-day Today hint, no notification. |
| Peptide reconstitution | **Accept, smaller; Later** (RECO-1…2) | "From vial and water…" fills Strength for powders; nothing else stored. | The missing step from "5 mg vial, 2 mL water" to mg/mL; volumes then show everywhere. Cut: a per-dose line and a U-100 caption (the band shows it), a 4-decimal strength (it drifts to 2 on the next save). |
| Blood pressure trend | **Accept, smaller** (TR-1, TR-3, TR-5) | 7-day averages over 26 weeks on the Blood pressure card under the Blood pressure chip. | A weekly question for a daily telmisartan user; weekly points avoid a zigzag and match the card. Rejected: a "healthy" band (advice), raw points, pulse, legend, range picker, a morning/evening split, overlays, a report change, a "chart appears later" hint. |
| Anything else from the web app | **Accept, smaller; Later** (OTHE-1, 2, 4, 5) | "Earlier…" reaches yesterday and names the day; extra doses show "Last taken" and start at that amount. | Both improve daily sheets; the first fixes future timestamps. Cut: a snackbar day suffix (OTHE-3). The rest is rejected (§4.2). |
| Plan days before the first log | **Accept, small; Later** (MISS-1) | Dev reports count from the first dose log. | Found while cutting the history import: pre-PT days read as missed. |

### 4.2 Web app features not taken (one line each)
- **Covered elsewhere:** "Next blood work" and draw schedules (Last draw); rotation (SITE); reconstitution maths (RECO); bloodwork compare, category chips, US/SI toggle (marker sheet, Units setting).
- **Out of scope:** accounts, demo and guest modes, the MCP connector, the PWA prompt, nutrition, training, injection technique and PIP text, peptide catalog cards, bloodwork targets and guidance, critical tiers, the Evidence page.
- **Already in PT:** theme, nav sheet, phase headline and progress, week chart, checklist, Cycle page, dated plans, oil calculator, level modes, forecast and washout, compound cards, hover panel, AI fields and "next due" (plan items, OTHE-5), logs grid, mg ↔ mL, "also log hCG" (Log all), time presets (Earlier…), activity feed (Journal), AI export (AI report), printed week (HTML report).
- **Low value or too heavy:** weekly and phase goals, the Test:EQ ratio, lab PDFs, maintenance dose, saturation and "blood balance", compare overlays, AI effect models and E2 factors, ester, PK and model toggles, a hair legend.
- **Correction:** the web's "yesterday" and "custom" presets were not covered (OTHE-2).

### 4.3 Bloodwork import (brief section 1)
**Accepted as designed in the import doc.** Rules other items rely on:
- Unlisted results are stored as `other:<slug of the printed name>` with the printed name and unit (the web import: the web key, no unit).
- A lab range, when present, alone decides the flag (a missing side means no limit, so an importer that knows only one side writes both); `<` and `>` values get no flag when the true value may fall either side.
- A draw without a printed time is stored at 12:00, never asked.
- Duplicates match by content (local date, marker, qualifier, unit for unlisted results, value within 0.5 % with a 0.01 floor), never by id; the web import skips web draws on a day that has a native draw.
- The chatbot copies numbers as printed; only the parser interprets them.
- One bulk save and undo serves both importers (BW-13).
- v1 asks nothing: uncertain values and draws with an uncertain date are left out with a reason (plan change 2026-09-27).
- An invalid stored lab range (a side negative or not finite, or low above high) is ignored: the marker's default range applies, and an unlisted result gets no flag (BW-2).

On-device text recognition is **not built** (import doc §2.4); INF-2 guards the no-network rule; owner check 47.

Brief section 1: flow and paste (§1, §10: BWI-5; the clipboard read, share, a file and the Journal view are Later: BW-16, BW-17, BW-19); review and saving (§8–10: BWI-3, BW-13; questions Later); format, parser, several dates (§3–4: BW-7, BWI-2, BWI-3); Dutch aliases (§5.4: BWI-1); lab ranges (§6: BW-2, BW-4); prompt and help (§12: BWI-4, BWI-5); OCR (§2.4); tests (§13, a modest fixture set).

### 4.4 Placement (where things live in dev)
- **P1 Journal views** (Later with JV-1 and JV-2; until then the existing chips and cards hold the trends: TR-4 opens from the dev Bloodwork card rows, TR-5 sits on the Blood pressure card under its chip, TR-2 is a line in the Log menu and the card label). Chips become focused views: at most one card, then entries; All is a plain timeline. Today gains no cards. Rejected: a Health tab or screen, cards on All, BP-card sheets, dropdowns, wrapped or abbreviated chips, range selectors, trends on Levels.
- **P2 Import.** v1 has one way in, the Bloodwork sheet; the Journal Bloodwork view and the share target are Later. One screen, three ways in: the Bloodwork sheet, the Journal Bloodwork view, a dev-only share target. `MainActivity` untouched. After a save: Journal › Bloodwork with Undo; after a share: a toast, back to the chatbot. Rejected: intent handling in `MainActivity`, an "Open with" filter, a text-selection menu item, a sheet-based review, a Settings entry, a sixth Log row, a paste field.
- **P3 Merge.** One Log menu and one dose editor; stable keeps its old paths.
- **P4 Discoverability.** Facts and empty states that name the action, plus one paragraph in About. Rejected: DEV tags, intro cards, hint settings.
- **P5 Stable guard.** Four shapes behind `BuildConfig.DEV_FEATURES`: (a) a `devOr` value switch; (b) an optional hook with a no-op default; (c) a branch with the old code untouched; (d) an `XxxDev` fork for a section with more than about three differences. New features in new files; `app/src/dev` holds only resources, the manifest and the share activity. No new settings; old toggles ignored in dev, never deleted. Dev-only ViewModel fields default to empty and are computed only in dev.
- **P6 TrendChart.** One chart for bloodwork, BP and mood; `LevelChart` untouched. A band only for bloodwork (named in a caption); none for BP or mood, which would be advice. The selected point is described under the chart, never in a bubble. It replaced a raw BP chart and marker-sheet selection.

### 4.5 Simplify and polish (brief section 3)
All Later since the plan change 2026-09-27; the decisions stand.
- **Levels:** one slide-to-read gesture, vertical scroll free, no switch (SIM-1, SIM-2); Experimental and compare gone from dev (SIM-3, SIM-7; stable loses them at graduation, said in that release note); one control row, one "now" per card (SIM-8, SIM-9); reading under the chart, lab legend (SIM-4, SIM-5).
- **Sheets and paths:** recent symptoms first (SIM-11); import first in Bloodwork (SIM-12); "Delete entry" (POL-10); one Log menu, one dose editor, one "Logged today" (SIM-14, SIM-15, POL-4).
- **Merged:** POL-11 → SIM-1; POL-12 and EEST-2 → SIM-4, SIM-5; POL-13 → SIM-8; POL-14 → SIM-9; POL-15 → SIM-10; POL-21 and SYMP-4 → SIM-11; POL-22 → SIM-12; SIM-13 → JV-2, TR-4, POL-27.
- **Kept:** the range row, two-finger pan and zoom, the jump bar, the lingering cursor, tick density, diamonds for total T only, mood 1–10 and five hair levels, the units control, five Log rows, Journal's "+" in dev, the mode row on the detail screen, "Back to now".
- **Rejected:** scrub opt-in, hiding "Back to now", removing the mode control, a smaller compare (fallback on record: a push screen with the plan baseline only), regrouping symptoms, symptom search, an "Add result" picker, dropping the Bloodwork sheet's reference lines completely, vibration off by default, no firm ticks, merging Note and Symptoms, back-gesture exclusion on Levels charts.

### 4.6 Visible fixes and stable
Stable is frozen: visible shared-UI bugs are fixed in dev only and move over in one batch when dev graduates; invisible data-safety fixes go into both. Clipping is fixed only once a screenshot shows it. Low-level shared components get verified value switches, never forks. No muscle-memory differences for small gains.
- **Left in stable, fixed in dev:** "1 results", ISO day headers, the Reports text, the "Earlier…" future-time hole, warn contrast, the clipped dose dialog, the E2 summary, 44 dp targets.
- **Left in both:** Plan's filled "Add", the shared calendar glyph, eight Settings pages, Save placement in the two editors, sheet paddings, the dark band colour, planned tick alpha, week cell widths, y labels at 1.3, odd y ticks, the Today eyebrow comma, nav labels at large font, warn on wallpaper colours.

### 4.7 Resolutions made in this backlog
- BW-1 comes first: docs go into git before build commits cite them.
- TrendChart: TR-3 draws, has a static mode and the tap-and-slide gestures for BP (and later mood).
- The history import stays insert-only without Undo; the dialog shows the counts first.
- Dev releases change only the dev flavor's `versionName`.
- The import doc's order holds for BW-2…BW-7, BW-13 and BWI-1…BWI-5; BW-16, BW-17 and BW-19 are Later.
- Sites (3→4) is the only planned migration; clipping items (Later) wait for POL-1.
- BW-3: unit normalization (§5.5) and character normalization (§4.1 steps 4-6) live in `BloodworkRules` (model), not in `io/labimport`, because `sameResult` compares units and the model must not depend on the importer. BW-7's `LabText` and BW-8's vocabulary call `normalizeChars`/`normalizeUnit`.
- BW-7: range limits follow only the block's decimal style (§3.3 rule 5.1), never elimination; a limit the style cannot settle, or a women's range alone, is caption C6 ("… unclear numbers.", "… women's range."). A unit printed after a range that differs from the row's unit is kept on the parsed range for the meaning checks (BW-10), not refused. The decimal reading of an ambiguous number drops trailing zeros (`1,050` → `1.05`) so a caption or question cannot be read as grouping. Unreadable part 1 dates (`morgen`, `2025-02-30`, `25:00`) are D1.
- BW-3: an `other:` slug writes `%` as `pct` (`Lymfocyten %` → `other:lymfocyten_pct`), so a percentage and an absolute count of one test (common in Dutch blood counts) never share a key across draws. The import doc's examples are unchanged.
- BW-3: `editResults` ignores a typed unlisted key without an existing result (it has no name or unit); the sheet only shows fields for existing ones.
- BW-5: a sheet field counts as touched while its text differs from the saved value as shown in the current units, so typing the saved value back keeps the result exactly (qualifier included). The unit toggle re-renders untouched fields from the saved value instead of converting their rounded text (the old conversion showed creatinine 96 µmol/L as 96.004).
- BW-6: sheet captions describe the result as it will be saved (the edited results, not the saved ones), and the "Reported as" number is the field's own text (3 decimals, e.g. `<10.896 pg/mL`), not the rounded card value, so the field and its caption never disagree. A dev field keeps the typical "Reference …" when its result has no lab range. The dev "Other tests" section has no caption of its own.

---

## 5. Owner checks

Only the owner can verify these. Each default holds until he answers.

| # | Check | Default meanwhile | Item |
|---|---|---|---|
| 1 | Run 2–3 real Dutch reports (PDF and photo) through the chatbot and the import: how many results are left out? | Expect 0; a recurring caption means a missing alias; his answers become fixtures. | BWI-1, BWI-3 |
| 2 | Which copy paths his chatbot apps offer | Any whole answer works; the help says "Tap Copy …, not Share". | BWI-5 |
| 3 | Is the clipboard toast on return fine, and does the automatic check fire? | Yes; "Paste answer" always works. | BW-16 (Later) |
| 4 | Does "Import bloodwork" appear when sharing a file? | "Open a file" covers it. | BW-17 (Later) |
| 5 | 12:00 for draws without a printed time | Accepted; editable. | BW-3 |
| 6 | The men's range when a report prints both | Men's range, with a caption. | BWI-3 |
| 7 | After a share import: back to the chatbot, or into Journal? | Toast and return. | BW-17 (Later) |
| 8 | A same-day result with another value starts left out | Left out; one tap keeps it. | BWI-3 |
| 9 | Share the prompt into the chatbot instead of Copy? | Copy stays. | BWI-5 |
| 10 | Save the prompt in a chatbot project | A tip in the import doc. | BWI-4 |
| 11 | Which web file: full export, or "Export AI Review" (logged in as himself; guest exports blank notes) | Both accepted. | HIST-3 |
| 12 | Dose history from the web app | Not imported. | HIST-1 |
| 13 | After the history import, spot-check BP, a draw and a symptom log against the web app | Tests cover the documented shapes only. | HIST-3 |
| 14 | Is "Last draw 9 weeks ago" enough? | Fact only; never a setting. | TR-2 |
| 15 | Do the hematocrit, T and E2 sheets read right on his data? | As specified. | TR-4 |
| 16 | Charts for unlisted markers? | List only. | TR-4 |
| 17 | Does he log pins from the widget? | The widget has no sites. | SITE-5 |
| 18 | The 12 sites, labelled "L VG" | These 12. | SITE-1 |
| 19 | Does the site suffix read well at his font size? | Keep it. | SITE-4 |
| 20 | Does he rotate hCG or peptide pins? | Available, quiet until used. | SITE-3 |
| 21 | Does he still log mood weekly? | Build it; sparse mood shows nothing. | SYMP-2 (Later) |
| 22 | Mood as dots or a line? | Dots. | SYMP-2 (Later) |
| 23 | A hair-shedding trend? | None; it would replace mood, not add a toggle. | SYMP-2 (Later) |
| 24 | Is his AI or Dbol use still as needed? | Yes, so no E2 estimate. | §4.1 |
| 25 | Did he use the web app's E2 estimate weekly? | No evidence; rejection stands. | §4.1 |
| 26 | "T" and "E2" as labels in the reading line | As specified. | SIM-5 (Later) |
| 27 | Has running out actually happened lately? | No; supply stays rejected. | §4.1 |
| 28 | Oils in mL and powders in units at once? | One global setting. | RECO-2 (Later) |
| 29 | Blend vials | Enter each compound with the same water. | RECO-2 (Later) |
| 30 | A custom powder without the vial link | Move it to Peptides. | RECO-1 (Later) |
| 31 | Does the BP chart show the expected cycle changes? | As specified. | TR-5 |
| 32 | 26 weekly points or 13? | 26. | TR-1 |
| 33 | Morning and evening as separate lines? | No split. | TR-1 |
| 34 | Is "Each point is a 7-day average" clear without a legend? | Yes. | TR-5 |
| 35 | A date picker for older entries? | No: one day back; doses can move in the editor. | OTHE-2 (Later) |
| 36 | Extra dose starts at the last amount or empty? | The last amount. | OTHE-5 (Later) |
| 37 | Does he log as-needed doses in PT? | Build it anyway. | OTHE-5 (Later) |
| 38 | Do the 360 dp / 1.3 screenshots match his phone? | The screenshots decide. | POL-1, JV-1 (Later) |
| 39 | Drop the category tag on Today? | Dropped in dev. | POL-3 (Later) |
| 40 | Symptom heading wording | "Often listed with low/high estrogen", "Other". | SIM-11 (Later) |
| 41 | Recent symptoms: 90 days, 12 chips | 90 and 12. | SIM-11 (Later) |
| 42 | How the scrub ticks feel | Light per day, firm per log. | SIM-2 (Later) |
| 43 | Does he use compare? | Removed in dev. | SIM-7 (Later) |
| 44 | Bloodwork fields up front with no history | Hormones and hematology. | SIM-12 (Later) |
| 45 | Does sliding near the edge trigger Back? | Excluded on TrendChart. | TR-3 |
| 46 | The "Back to now" glyph | MyLocation. | POL-16 (Later) |
| 47 | Anonymized real reports for an OCR evaluation | OCR not built; revisit on import doc §2.4. | §4.3 |
| 48 | The published v0.4.0 release text on GitHub still says 25 markers; `docs/releases/v0.4.0.md` now says 24. Update it with `gh release edit v0.4.0 --notes-file docs/releases/v0.4.0.md`? | Published text left as is (editing it is public). | DOC-1 |

---

## 6. Done

| Commit | What |
|---|---|
| 8b24b19 | Pin the clock and wait for settled frames in design-review screenshots |
| b5fbd44 | BW-1 · Add the bloodwork import design and the dev backlog |
| 7322dc7 | INF-1 · `devOr` (Journal empty text uses it) and `DevEntryPointsTest`, one test per gate in both flavors; each failed with its gate forced open (stable) or closed (dev) |
| edd32ac | INF-2 · `ManifestPermissionsTest` (both flavors): the merged manifest has no `INTERNET` (`WAKE_LOCK` present as the positive control, `ACCESS_NETWORK_STATE` allowed); failed with `INTERNET` added to the main manifest |
| 657dcdf | INF-3 · A tag containing `-dev.` builds only `assembleDevRelease` and publishes `ProtocolTracker-Dev-<tag>.apk` as a pre-release (same notes lookup); other `v*` tags unchanged. Dev flavor has its own `versionName = "0.4.0-dev"` (APK badging confirms dev `0.4.0-dev`, stable `0.4.0`); `VersionNameTest` checks the format per flavor (fails if the dev `versionName` is dropped). Release steps pass `bash -n` and were dry-run with stubbed `gh`/`cp` for `v0.5.0-dev.1`, `v0.5.0` and `v0.4.1`. |
| 5649e83, 2429dfe | DOC-1 · v0.4.0 notes say 24 markers; `JournalEntry`, `TrackerRepository.journal` and `JournalLine` KDocs name all four entry kinds; MODELS.md › Limitations has the estradiol line; `Presets.VERSION` unchanged. Also fixed the CI failure of 657dcdf (a one-off `CalledFromWrongThreadException` in stable `DevEntryPointsTest`): that class uses the v2 Compose test rule (standard test dispatcher). |
| a0df7ee | BW-2 · `MarkerResult` gains `qualifier`, `refLow`/`refHigh`, `name`/`unit` (`@EncodeDefault(NEVER)`, no `require`); `RefRange`, `labRange()`, `range()`, `flag()`, `unclear`; `Bloodwork.outOfRange` via `flag()`, `Bloodwork.unclear`, `Bloodwork.result()`; `MarkerTrend.result`. `ResultFlagTest` (§6.4 table, worked cases, every marker's plain results equal `BloodMarker.flag`, counts), `MarkerResultCompatTest` (a literal 0.4.0 backup decodes and re-encodes byte for byte; plain result bytes; every field round-trips; an unknown qualifier, low > high, a negative side and an unknown key decode without throwing), `BloodworkMapperTest` in core/data (an old `dataJson` row decodes, a plain entry stores today's exact string, new fields round-trip). No Room or backup format change; no UI change (display is BW-4); R8 both flavors green; stable guard clean. |
| d2a3773 | BW-3 · `BloodworkRules`: `UNKNOWN_DRAW_TIME` (12:00), `normalizeChars`, `normalizeName` (full and short forms), `otherKey`, `normalizeUnit`, per-marker `limits`/`plausible`, `sameResult`, `editResults`. `BloodworkRulesTest` (27 tests): every marker has limits, edges hold, defaults inside, the §4.4 slips caught; noon keeps the day from UTC-10 to UTC+12; the §5.2 name and §7 key examples incl. the web form; unit spellings; `sameResult` at 0.4 % / 0.6 %, the 0.01 floor, qualifier and unit mismatch, SI round trip vs the web's two decimals; `editResults` untouched-exact, order, typed keeps range and drops qualifier, cleared removes, unlisted never converted. Domain only, unused by the UI; stable guard clean. |
| 68be644, ceed2b7 | BW-4 · Reports: lab range "ref … (lab)", censored values keep `<`/`>` in both units, unlisted results as printed (`printedText`, `printedLabRange`), no flag text when unclear, legend variant only when a draw has a lab range, qualifier or name (`ReportEntry.Bloodwork.labDetails`); `BloodMarker.rangeText`/`formatResult`; `labPoints` skips censored results; `MarkerTrend.previousResult`. Journal line via `bloodworkSummary`: "all in range" only with `outOfRange == 0 && unclear == 0`, "1 result" in dev (`devOr`), "1 results" kept in stable. Dev Bloodwork card flags by `MarkerTrend.result`, marks "(lab)", shows `<0.3 IU/L`, no flag text when unclear, 12 dp gap before the value. `ReportLabRangeTest` (10), `JournalLineTest` (both flavors), `DevEntryPointsTest.journalLineResultCount`, `ScreenshotTest.journalLabRanges` (dev, light and dark, looked at). Pinned `HealthEntriesTest` strings unchanged; stable guard clean. |
| 9b583f0 | BW-5 · `BloodworkSheet` (both flavors) saves through `BloodworkRules.editResults` with a touched-key set (typing only, never the unit toggle): untouched results keep value, qualifier, lab range and `other:` results exactly; untouched fields show the saved value in the other units. `BloodworkSheetEditTest` (6, both flavors): untouched save equals the original entry, toggling units twice (fields show 96 / 9.9 / 1.086), edited SI Hb keeps its range, typed LH drops `<` and typing the saved value back restores it, cleared creatinine removed, typed values convert with the toggle, a new draw saves what is typed; 4 of 6 failed on the old sheet. No visible change; stable guard clean. |
| 7f8d6aa | BW-6 · Dev Bloodwork sheet: a field whose result has a lab range reads "Lab range 248–836 ng/dL" instead of "Reference …"; a censored value adds "Reported as <0.3 IU/L. A typed number replaces it." (the number is the field's own text, so `<10.896 pg/mL`, not the card's `<10.9`); captions follow the result as it will be saved (typing drops the line, clearing brings back "Reference"). "Other tests" at the end: one `NumberField` per unlisted result, label and unit as printed, number as printed (`printedNumber`/`printedValue`, `PrintedResultTest`), untouched by the unit switch. `BloodworkSheetLabTest` (4, dev; one failed with captions taken from the saved instead of the edited results), `DevEntryPointsTest.bloodworkSheetLabDetails` (both flavors), `ScreenshotTest.bloodworkSheetLab` (dev: `bloodwork-sheet-edit-lab`/`-other`, light and dark, looked at). Stable guard clean; no Room, backup, manifest or R8-relevant change. |
| dae20b6, 3e1f2c7 | BW-7 · `io/labimport`: `LabText` (§4.1 steps 1-7: line ends, BOM, citation debris incl. supplementary private-use characters, entities with `&amp;` last, `normalizeChars`; decoration stripped only when the rest is a known line; pipe and tab cells, `|` and `&#124;`; key normalization), `LabValues` (§3.3 separator rules 1-5 with `DecimalStyle` witnesses and `settle` by elimination; values with qualifiers, copied flags, unit tails, no-value words, negatives; §3.4 ranges incl. sex labels and the men's range, age and several ranges, target notes, trailing units), `DrawDates` (§3.5 ISO, day-first, month-name and US dates with times, two-digit years, day-first cross-check against part 2, draw/received/never labels, D1-D4 leaving the date empty). `LabTextTest` (12) and `LabValuesTest` (36) green, incl. `date: 2025-04-03 | 04-03-2025` asking and mixed thousands styles asking. Domain only, unused by the app; no Room, backup, manifest or R8-relevant change; stable guard not needed (no app code). |
| 1d01656 | Release v0.5.0-dev.1 (tag) · dev pre-release: guards, CI pre-releases, bloodwork groundwork (INF-1…3, DOC-1, BW-2…BW-7); notes in `docs/releases/v0.5.0-dev.1.md` |
| 9155785 | BW-13 · `TrackerRepository.saveJournal(list)` (one transaction, upsert by id: saving the same list twice adds no copies) and `deleteJournal(ids)` (one transaction, returns the removed entries oldest first for Undo, unknown and repeated ids ignored, ids chunked at 500 under SQLite's variable limit); `JournalDao.getByIds`/`deleteByIds`. `TrackerRepositoryTest` (5): no duplicates, a failed save or delete (SQLite trigger) leaves nothing changed, delete then save restores, the journal flow emits once per batch, 1,001 entries. Queries only, no Room schema, backup or UI change; stable guard not needed. |
| 2e27549 | Plan change 2026-09-27 · Open trimmed to the weekly set (BWI-1…5, HIST-1 and HIST-3, TR-1…5, SITE-1…6), the rest moved to Later; checkpoints replaced by one audit per release; import doc "Scope of v1" (paste, review, save; uncertain values left out with a reason), §12 prompt unchanged |
| 11904b2 | BWI-1 (was BW-8) · `io/labimport/MarkerVocabulary`: names per marker (prompt aliases incl. the brief's Dutch names, matched-only variants, never words, the plain-Glucose confirm name, prompt notes), global exclusions (`/` unless the matched form holds it, ratio, quotient, index, verhouding), accepted units with factors (`onlyByName` bare mL/min for eGFR), `match`, `identify` (§5.3 name and key precedence before the numbers: `Agreed`, `ByName`, `ByKey`, `Unlisted` with `ruledOut`/`confirm`), `hasNeverWord`, `unit`, `candidates` (one per factor), `neverUnit` (`%` for free T, ratio). Added variants 17β-oestradiol/-estradiol and never words kreatinineklaring/creatinineklaring (doc §5.4 updated). `MarkerVocabularyTest` (20): completeness, cross-key uniqueness, every alias and variant maps to its key, no own never word, the brief's names, the settled cases, traps, every marker's units pinned, stored unit ×1 and SI unit = `siToConventional`, nmol = 1000 × pmol, the web app's factors, §5.7 Dutch examples. Domain only, unused by the app; stable guard not needed. |
| a859581 | HIST-1 (with HIST-2) · `io/WebExportImport`: `matches` (JSON object, no `format`, a `logs`/`symptoms`/`bloodwork` array: full export and AI-review file) and `parse(text, zone, existing = [])` → `WebImport` (entries sorted by time then id; counts per kind, `from`/`to`, `alreadyThere`, `WebLeftOut` doses/ticks/weekly notes/settings/PDFs, warnings grouped by reason with 3 examples and `text()` via `DisplayFormat`). Ids `web:log:<id>`, `web:symptom:<id>`, `web:bloodwork:<date>`; naive times UTC (Z/offsets read), ms; BP from numbers or strings, pulse 0/absent = none, impossible readings left out; notes trimmed; old mood logs and symptom rows (order kept, repeats dropped, unknown keys kept, 0 = none, out-of-scale values left out; AI/Dbol doses counted); draws at 12:00 local, web markers `round4(si × factor)` when within 0.005 of `us` (creatinine 0.9615), plausibility limits, a lab range (both sides) only when a side differs from the default; other keys (prolactin, eGFR too) → `other:` with the name, no unit; empty draws, a native draw on the same date, texts over 5,000 characters and the AI-review caps warned. Skips ids in `existing` and native copies within ±15 min (a rerun adds 0). `WebExportImportTest` (15) on a §5 full export, a messy export and an AI-review file; guards for the 40 symptom keys and 22 markers. Domain only; no Room, backup or UI change; stable guard not needed. |
| cb3a3bf | TR-1 · `model/BloodworkHistory`: `MarkerPoint`, `markerHistory` (oldest first, same instant by entry id, draws without the key skipped, `other:` keys work), `plottable` (known markers, no qualifier), `lastDrawAge` (latest draw at or before now by local days: today / yesterday / N days to 13 / N weeks to 181 days / N months / N years). `model/BloodPressureTrend`: `bloodPressureWeeks` (26 weeks back from now, week 0 = the card's `at >= now − 7 d` with no upper bound, week k `[now − 7(k+1) d, now − 7k d)`, rounded means, oldest first, empty weeks dropped), `BpWeek.describe` ("Last 7 days · …" / "7 days to 19 Sep · …", date of `end − 1 ms` in `DisplayFormat.current.dayMonth`, "1 reading"). `BloodworkHistoryTest` (7) and `BloodPressureTrendTest` (8) incl. exact edges, the card formula, 23:30 → "yesterday" at 00:10 in Amsterdam, UTC−10 and UTC, both date orders, 1,000 readings; `markerTrends` unchanged. Domain only, unused by the app; stable guard not needed. |
| fc7250a | SITE-1 · `model/InjectionSites.kt`: `InjectionSites` (12 keys delt, pec, abdomen, vg, glute, thigh × L/R; short "L VG" and long "Left ventrogluteal" labels, unknown keys shown raw; `mirror`, `order`), `SiteRotation.state` (last 24 taken doses by `takenAt`, skipped ignored; `SiteState(last, personal, suggestion)`: the site that followed the last one the previous time, else its mirror, none when the newest taken dose has no site; personal set in catalog order, unknown keys after) and `byCompound`; `DoseLog.site` last, `@EncodeDefault(NEVER)`. `SiteRotationTest` (13: not tracked, mirror, learned cycle, abandoned site dropped after one override, broken chain keeps `last`, skipped ignored, same site twice → mirror, unknown key raw without mirror, the 24 window, `takenAt` over `createdAt`, personal order, per compound, catalog), `DoseLogSiteCompatTest` (3: a golden backup encoded before the change keeps its bytes, decodes with null sites, a site round-trips), `ReportTest.injectionSitesAreNotReported`. Domain only (the Room column is SITE-2); stable guard not needed. |
| a915b28, eb9a2e2 | BWI-2 (was BW-9) · `io/labimport/BlockReader`: `read(text, today)` → `Found(draws, notices, unread)` or `Refused(InputProblem)`. Segments from each header to `end` or the next header; the prompt's template segments dropped (a whole conversation works); `lab:` lines carry to later draws; `date:` lines via `DrawDates`, draws of one date (same or missing time) merged, results before any date form a draw without date; result lines kept as `PrintedRow`s; headings, fences, table borders and field-name rows skipped; omissions (`...`, `enz.`, "(the remaining …)") → N2; a header-less block with `date:`, a known-key row and `end` → N3; the cut-off rule (a block without `end` never imports its last result line, "Line N may be cut off") → N1; other lines "Line N not understood". Refusals in §3.8 order: M1 empty, M8 over 200,000 characters, M6 newer version, M7 joined lines, M4 the prompt, M3 share links and lone URLs, M5 JSON or a pipe table, M11 the report itself (4+ name-number-unit rows), M12 no results, M2. `ImportMessages` holds the §4.5 texts; `BloodworkImport` the header, version, limit and prompt signature. `BlockReaderTest` (19) on fixtures F02, F03, F09, R01, R03 and a prompt stand-in. Domain only, unused by the app; stable guard not needed. |
| deff029 | BWI-4 (was BW-12) · `io/labimport/LabPrompt.text`: signature, layout block, nine rules, one key line per `BloodMarkers.all` marker (`key: name; units; aliases[; notes]` from `MarkerVocabulary`, distinct unit spellings), `other:` last; 4,909 characters. `LabPromptTest` (5): every marker line in table order with all units, aliases and its note; signature first, header once as a line; pasted back → `Prompt`, prompt + F02 reads F02 with nothing unread; ≤ 6,500 characters, no line over 400, no example number; import doc §12 fence equals the text (doc prose updated). Domain only, unused by the app until BWI-5; stable guard not needed. |
| 59b5007 | HIST-3 · Dev: `SettingsViewModel.readLegacy` sends a file `WebExportImport.matches` to `readWebExport` (stable path unchanged: "Unsupported export format; expected cycletracker-1"); `PendingData.WebImport` shows `WebImportDialog` "Import web app history?" with `WebImport.text(zone)` (span, counts per kind, what is never imported, skipped count, no-undo line, one `•` line per warning); Import saves with `saveJournal(list)` and says "Imported N entries"; a file with nothing new says "Nothing new to import from this file." and shows no dialog. Tests: `WebExportImportTest` dialog text, `TrackerRepositoryTest` web entries round trip, `WebHistoryImportTest` (dev import, rerun, cancel; stable message), `DevEntryPointsTest.settingsImportReadsWebExport`; dev screenshot `web-import-dialog-light` (411 dp) checked; stable guard unchanged. |
| (this commit) | Release v0.5.0-dev.2 (tag) · dev pre-release: web history import (HIST-1, HIST-3) plus groundwork (BW-13, BWI-1, BWI-2, BWI-4, TR-1, SITE-1); notes in `docs/releases/v0.5.0-dev.2.md`. Audit of the diffs since v0.5.0-dev.1 still due, before the next release. |
| f1884f5 | TR-2 · `JournalDao.observeBloodworkTimes` / `TrackerRepository.bloodworkTimes` (read-only `atMs` query, no schema change); `TodayViewModel.lastDraw` (dev: bloodwork times × minute ticker → `lastDrawAge`, stable a constant null; `TodayState` untouched); the Log menu's Bloodwork row reads "Last draw 3 days ago", else "Lab results of a blood draw"; `JournalState.lastDraw` labels the dev card "Bloodwork · last draw 3 days ago" (falls back to "latest results"). `LastDrawHintTest` (4, dev, clock 26 Sep 2026 10:00: 23 Sep → menu and card, no draw, only a 30 Sep draw), `DevEntryPointsTest.logMenuLastDraw` and `journalBloodworkCard` (both flavors); Today tests unchanged. `ScreenshotTest.lastDraw` (dev, 360 dp: `log-menu` light and dark "Last draw 25 weeks ago" on one line; card label at 25 weeks fits, at 10–11 months "AGO" wraps to a second line, fits at 411 dp). Stable guard clean. |
| 0445fb6 | TR-3 · `ui/components/TrendChart` (`TrendSeries` with SOLID/DASHED/NONE lines and DIAMOND/DOT/RING marks, `TrendBand`, fixed `yRange`, 160 dp): x by time with an 8 dp inset so edge marks are whole, y labels in a 40 dp gutter, start and end dates (with the year when they differ), band in `accentSoft`, lines and marks in `accent`. `onSelect = null` is static (no pointer input, no cursor, no exclusion); otherwise a tap selects the nearest point in time within 24 dp, a sideways slide past slop consumes and moves point by point with `ScrubHaptics.tick()` per new point, a vertical drag is released to the page, `systemGestureExclusion`; `selectedAt` draws a cursor line and a larger ink ring; the caller shows the reading. Pure `TrendAxis`: `xWindow` (every point, widened back from the end to a minimum span, optional end), `yRange` (data and band limits padded 10 %, not below 0 for non-negative data, a single value gets a span, fixed wins), `bandSpan` (open side to the plot edge), `yTicks` (1/2/2.5/5 × 10ⁿ, 2–3 labels, one decimal count), `nearest`. `TrendAxisTest` (10, plain JVM), `TrendChartTest` (4, Robolectric, own class: static chart scrolls and ignores taps, tap within/beyond 24 dp, slide selects day 0 → 20 → 40 without scrolling, vertical drag scrolls and selects nothing). Dev-only `ScreenshotTest.trendChart` at 360 dp (light, dark, pure black: hematocrit band, one-sided HDL band, 26 weeks of BP with a selection) looked at; band visible in dark and black, lines distinct. Unused by screens yet; stable guard clean. |
| 4d04958, d5419a1 | Audit of v0.5.0-dev.2 (`v0.5.0-dev.1..v0.5.0-dev.2`: HIST-1/3, BW-13, BWI-1/2/4, TR-1, SITE-1) · web draws keep values entered in conventional units (`WebExportImport.exactValue`, test failed before with 200.0012); README and notes point to Settings › Export and data; `ScreenshotTest.webImportDialog` also renders dark (both looked at). Two reader gaps left for BWI-3/BWI-5 (§7). No app code changed; stable guard not needed. |
| e4ae8ef | TR-4 · Domain: `MarkerTrend` keeps only the latest result (`previous*` removed, tests trimmed); `unlistedTrends` (latest per unlisted key, by printed name, flagged by lab range only); `markerSheetData` (results newest first, chart points only with 2+ plottable, band = latest plotted result's range with `bandFromLab`, `leftOut`, `showsRange`). Dev: `JournalViewModel.markerSheet` computes only the tapped key; card rows (`ResultRow`, 56 dp, chevron, "Show results over time") drop "before X"; unlisted rows after the known markers, a flagged Low/High shown, the rest behind "Other tests (N)"; `ui/health/MarkerSheet` (static `TrendChart` with diamonds, 60-day minimum window, "Shaded: lab range of the latest result, …" / "typical adult male range, …", "Results with < or > are listed, not plotted.", ALL RESULTS with date · lab, Settings units, own flag, ref only where it differs from the band; unlisted as printed, no chart). `BloodworkHistoryTest` (+5), `MarkerSheetTest` (4, dev), `DevEntryPointsTest.journalMarkerSheetRows`; `ScreenshotTest.markerSheet`/`markerSheetNarrow` (dev, 411 and 360 dp, light, dark, black; band visible, no clipping). Stable guard clean. |

| 2d95866 | BWI-3 (was BW-10, BW-11) · `io/labimport`: `BloodworkImport.read(text, today)` → `ImportRead.Refused` or `Found(ImportDraft)`. `RowReader` (§4.2-4.3, §5.3, §7): value (no value, not a number, negative → not imported, the no-value word or note into the entry note), identity (the name wins; `ByName`/`ByKey` need a printed accepted unit and clean numbers, else unlisted with C12; `neverUnit` → C2; plain Glucose keyed glucose → C11), ambiguous thousands (block style, else the only possible reading, else left out), missing or wrong units (the only fitting unit with C1, else left out or not imported), C4 impossible (repairs → left out, none → not imported), C1 range fits another unit (free T molar ↔ mass only), C2 lost decimal, consistency (C6, free T exempt), a range printed in another unit is not used, ranges by sex (C5). `ImportDrafts`: per-block decimal style, duplicates per draw (same value once, a later block wins with C7, two values in one block → both left out with Q4's text, unlisted `…_2`), draws with an uncertain date left out whole with D1-D4's text, received caption. `Review.of(draft, choices, existing, zone)`: `RowState` READY/LEFT_OUT/SAME_DAY/UNCERTAIN/NOT_IMPORTED/ALREADY_SAVED (by local date via `sameResult`; another value that day starts left out with C8, which replaces the caption; Keep includes it), counts, the S2-S5 line (S2 without the question count; a repeated name counts), `saveLabel`, `entries` (one per dated draw, 12:00 default, table order then unlisted, lab ≤ 80, note capped). Texts in `ImportMessages`. Fixtures F01, F08, F18, F20, F21, N02, N08, N15, N16, N18, N22 (+ F02, F03, F09): `ImportDraftTest` (14), `ImportReviewTest` (9, incl. the corpus test: every fixture with rows left out and kept saves entries that survive a backup round trip; the same answer twice saves nothing). Domain only, unused by the app until BWI-5; stable guard not needed. |
---

## 7. Audit findings

| Date | Finding | Status |
|---|---|---|
| 2026-09-27 | Today's Log button (`ExtendedFloatingActionButton`) merges no text: its semantics node is a Button without "Log", so TalkBack may announce only "Button". Tests find it through the unmerged tree. Shared UI; a fix changes stable's accessibility tree, not its pixels. | Open: decide with a polish item (a `contentDescription`/`semantics` on the button, both flavors, pixel-identical) |
| 2026-09-27 | `JournalState()` starts with `empty = false`, so the filter chips render while the Journal loads and an empty Journal flashes chips before its empty state. Tests anchor on loaded content, not on "All". | Open: low; stable behaviour, leave unless a Journal item touches the loading state |
| 2026-09-27 | The other Robolectric Compose tests (`AppNavTest`, `TodayScreenTest`, `Levels*Test`, `UnitsSettingsTest`) still use the v1 `createComposeRule`, whose unconfined effect dispatcher can resume a frame on a Room background thread (`CalledFromWrongThreadException`, seen once in CI in `DevEntryPointsTest`). `ScreenshotTest` stays on v1 (guard baseline). | Open: move a class to `junit4.v2.createComposeRule` when it next changes or if it flakes in CI (flaked once locally, stable `TodayScreenTest.missedDoseLoggedFromTheSheetStaysVisibleAsChecked`, TR-1 gate; green on rerun) |
| 2026-09-27 | `ManifestPermissionsTest` ends before the app's startup coroutine opens the database, so CI logs an uncaught `SQLiteCantOpenDatabaseException` from a deleted Robolectric data dir. The test passes; the log line is noise. | Fixed (9652d1f): the test runs with a plain `Application`, so no startup work; still fails with `INTERNET` added |
| 2026-09-27 | Checkpoint 1 (INF-1 to DOC-1): INF-3 replaced the dev `versionNameSuffix` with a hand-set `versionName`, so the dev version no longer follows stable, and nothing compared a tag with the APKs: a forgotten edit would publish a release whose Settings shows the wrong version. | Fixed (d324c56): CI step "Check version names" reads each release APK with `aapt2 dump badging` and fails unless a `-dev.` tag equals dev's `versionName` and a `vX.Y.Z` tag equals stable `X.Y.Z` and dev `X.Y.Z-dev`; dry-run against the debug APKs and a stub for every path |
| 2026-09-27 | Checkpoint 1 screenshots: the empty Journal (plan, nothing logged) shows the collapsed "Adherence · 7 days / 30 days" card under "Nothing logged yet", so the empty state is not the only thing on the screen. Stable shows the same. | Open: low; a Journal polish item decides whether dev hides adherence until the first dose log |
| 2026-09-27 | Checkpoint 1 review, no change needed: `DevEntryPointsTest` covers all six gates (`grep DEV_FEATURES\|devOr(`), and each check follows an anchor from the same state emission (the Bloodwork card and `GroupView.measured` come with the rows and views they wait for); no Room, backup, R8 or manifest change in the batch; stable screenshots unchanged. | Closed |
| 2026-09-27 | BW-4: the HTML report has never had the Markdown report's bloodwork legend, so "(lab)" and the SI brackets go unexplained there (the lines themselves are the same). | Fixed (91f6309, checkpoint 2): the HTML Journal section prints the Markdown legend sentence when a draw has lab details; plain reports unchanged |
| 2026-09-27 | Build machine: `AccessDeniedException` / "Failed to clean up output files" kept coming back (also with `PT_REDIRECT=1`, and in the `stable-base` clone) because folders under `build/` carried the Windows read-only attribute, which Java cannot delete through. Clearing it (PowerShell: every item under each `build` folder, `Attributes -band -bnot ReadOnly`) fixed every run. | Closed (machine quirk, no code change) |
| 2026-09-27 | BW-6 screenshots: in the dev sheet the "OTHER" category (CK, PSA) sits right above "OTHER TESTS"; two labels that read alike. | Open: decide in SIM-12, which folds most markers behind "More markers (14)" and may remove the adjacency (else rename the dev category label) |
| 2026-09-27 | Checkpoint 2 (BW-2 to BW-6): an unlisted result with an empty or blank `unit` printed a trailing space (`"Index 1.2 "`) in reports and a double space before "(lab)" in its range (`printedText`/`printedLabRange`; `printedValue` already dropped it). | Fixed (bffbd38): one `printedUnit()` rule for all three; `PrintedResultTest` covers null, empty, blank and padded units |
| 2026-09-27 | Checkpoint 2 simplicity: `MarkerTrend` stored each value twice (`value` and `result.value`, `previous` and `previousResult.value`) after BW-4 added the results. | Fixed (a736423): `value`/`previous` are read from the results |
| 2026-09-27 | Checkpoint 2: typing the saved number back into a censored field (`<40` shows `40`) keeps the `<` (BW-5 rule: the field counts as untouched), while the caption says "A typed number replaces it." The caption stays, so nothing changes silently; `40.0` replaces it. | Open: low; reword the caption or treat a censored field as touched on any edit when an import item next touches the sheet |
| 2026-09-27 | Checkpoint 2 review, no change needed: `flag()` matches the §6.4 table on every row; `editResults` cannot create duplicate keys (`Bloodwork` requires distinct markers; unlisted keys are filtered from known ones); stable's sheet, Journal line and reports change only for results carrying the new fields, which stable cannot create (a dev backup can bring them); no Room, manifest or R8-relevant change; both JSON configs have `ignoreUnknownKeys`, so even 0.4.0 restores a dev backup (new fields dropped); new stored fields are `@EncodeDefault(NEVER)`. Dev screenshots (Journal lab ranges, sheet lab and other tests, light and dark) read correctly; stable screenshots unchanged. | Closed |
| 2026-09-27 | BW-6: `NumberField` accepts at most 4 decimals and 7 integer digits, so an unlisted result printed with 5–6 decimals can only be replaced whole, not trimmed a digit at a time. Values as printed on real reports have not needed it. | Open: low; revisit if an import fixture shows such values |
| 2026-09-27 | Audit v0.5.0-dev.2: the web import took `si × factor` whenever it came within 0.005 of `us`, so values entered in conventional units lost their exact value: cholesterol 200 mg/dL became 200.0012 and was flagged high against the 200 limit (the web showed it in range); testosterone 650 ng/dL became 649.9959. | Fixed (4d04958): `WebExportImport.exactValue` keeps `us` when it gives back `si` (within 0.0005), else `si × factor` when within 0.005 of `us` (SI entries keep their decimals, creatinine 85 µmol/L = 0.9615), else `us`; `WebExportImportTest.conventionalEntriesKeepTheirValueAndSiEntriesTheirDecimals` |
| 2026-09-27 | Audit v0.5.0-dev.2 docs: README and the release notes sent the owner to "Settings › Backup › Import CycleTracker export" (the page is "Export and data"; Backup is a group on it), and the notes said the dialog counts doses, ticks and PDFs (one fixed sentence names them, PDFs not at all). The web import dialog had been looked at in light only. | Fixed (d5419a1): paths and the notes line corrected; `ScreenshotTest.webImportDialog` also saves `web-import-dialog-dark`; light and dark read correctly |
| 2026-09-27 | Audit v0.5.0-dev.2, BWI-2: N1 tells the owner to ask the chatbot to continue and copy the whole answer, but `BlockReader` does not read such a copy well. A continuation that repeats the header without a `date:` line becomes a draw without a date (v1 leaves it out) while N1 still shows for the first block; a continuation without a header ends the first block, so its cut last line becomes an ordinary row next to the repeated full line (two values for one marker). Safe (nothing is saved wrongly), but the advised path loses results. | Open: BWI-3 adds a continued-answer fixture (both shapes) and decides, e.g. carry the open draw into a dateless next block after a block without `end`, and let a repeated key replace a line cut by the chatbot's stop |
| 2026-09-27 | Audit v0.5.0-dev.2, BWI-2: a v1 block whose result lines use another separator (`;` or commas) has no result lines, so the reader answers M12 "The answer has no results…" instead of M5 "The chatbot used another layout…". | Open: low; BWI-5 decides with a fixture when the Start step shows the messages |
| 2026-09-27 | Audit v0.5.0-dev.2 review, no change needed: stable's `readLegacy` path is unchanged (same message; `WebHistoryImportTest.stableRejectsTheWebExport`, `DevEntryPointsTest.settingsImportReadsWebExport`); no Room schema, manifest, backup format or R8-relevant change; `DoseLog.site` is `@EncodeDefault(NEVER)` and not yet a Room column, so nothing sets it and old and dev backups restore both ways; the web import never overwrites (existing ids and native copies skipped, one transaction) and every entry it builds passes the model's `require`s (plausibility, note length, scales checked first); `MarkerVocabulary` factors match `BloodMarkers`; `bloodPressureWeeks`, `lastDrawAge` and `SiteRotation` match their KDocs; the prompt lists every marker. | Closed |

---

## 8. Blocked

Nothing blocked.
