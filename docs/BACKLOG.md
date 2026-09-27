# Backlog

The dev build's living backlog: open work in priority order, decisions (incl. rejected ideas), owner checks, done items, audit findings. Read it first; update it after every item. "Import doc §n" is [BLOODWORK_IMPORT.md](BLOODWORK_IMPORT.md).

Written 2026-09-27 at HEAD 8b24b19.

---

## 1. How to work from this file

Detailed specs for each item were written during design; when an item is unclear, read the code and the import design.

1. Take the first open item in §3 that is not blocked; decide an undecided idea first (§4).
2. Build the smallest good version: domain and JVM tests, then data, then UI with Robolectric tests.
3. Run the gate, look at dev screenshots of changed screens (light and dark), run the stable guard.
4. Commit and push; move the item to §6 with its hash; put findings in §3, §4 or §7.
5. At a **CHECKPOINT** (audit + simplicity pass), audit the diffs since the last one (bugs, edge cases, tests, docs, AGENTS.md) and remove or merge what you can. At a **RELEASE**, publish a dev pre-release.

- **Gate:** `./gradlew :core:domain:test testDebugUnitTest lintDebug assembleDebug`. One class: `:app:testDevDebugUnitTest --tests …`. After manifest, keep-rule or dev source-set changes also `:app:minifyDevReleaseWithR8 :app:minifyStableReleaseWithR8`.
- **Every acceptance below also means:** gate green, stable guard clean, dev screenshots of changed screens looked at.
- **Stable guard:** stable tests, pinned strings and screenshots stay unchanged (pixel-identical to the 8b24b19 baseline; fix a changed PNG, never explain it away; new PNGs only from dev-only tests). Never change `seed()` or an existing `ScreenshotTest` flow; new screens get their own `@Test` and data. `BuildConfig.DEV_FEATURES` is the only gate (§4.4 P5); invisible data-safety fixes go into both flavors. Each new dev entry point gets a `DevEntryPointsTest` row (in dev, not in stable, after an anchor in both).
- **Tests:** every behaviour change. Robolectric: no `runBlocking` inside `waitUntil`; scroll into view or use semantics actions before touch; gesture tests in their own class; `@Before` as `fun x(): Unit = runBlocking { … }`.
- **Data:** a Room change bumps the version with a real migration, `MigrationTest` and the exported schema, and never drops data. Old backups restore; a dev backup restores in stable. New stored fields: `@EncodeDefault(NEVER)`, no `require`.
- **Commits:** small, on `main`, no attribution lines, pushed after a green gate. Never force-push, rewrite history or touch signing secrets. Update AGENTS.md and the README dev section in the same commit.
- **Releases:** bump `versionCode`; set only the dev flavor's `versionName` (`0.5.0-dev.1`), never `defaultConfig.versionName` (stable Settings shows it; CI's "Check version names" step fails on a mismatch with the tag); write `docs/releases/<tag>.md`; tag `vX.Y.Z-dev.N`, push, `gh run watch`. On red CI: fix, delete the unpublished tag, re-tag. Never publish on red.
- **Where things go:** decisions in §4 before building; owner-only checks in §5 with a default; audit findings in §7. If the gate cannot go green without the owner, write what and why in §8 and stop.

---

## 2. Principles

- **Fewer, better features:** used weekly, fits an existing screen or flow, adds no setting. Prefer improving, merging or removing.
- **Quiet by default:** one primary action per screen, details on tap, no dashboards, tours or badges. Today gains no cards.
- **Plain and honest:** plain labels, short instructions, no slogans, no medical advice. Estimates are labelled as estimates.
- **Offline, private, no accounts:** no network permission, ever. Stable stays exactly as released.

---

## 3. Open

Take the first item that is not blocked. Sizes XS to L (split an L if you can). "Dev" = behind `BuildConfig.DEV_FEATURES`.

### (a) Guard and infrastructure

Nothing open (INF-1 to DOC-1 done, checkpoint 1 done).

### (b) Bloodwork import (brief section 1, top priority)

BW-2 done (§6).

### BW-3 · Shared bloodwork rules · S
Why: one rule set for both importers and the sheet.
- Limits cover every marker, hold at the edges and contain the defaults; `other:` keys incl. the web form; same result at 0.4 %, not at 0.6 %, with a 0.01 floor; result edits.
Spec: import doc §4.4, §5.2, §7, §9.2–9.3, §10.7.

### BW-4 · Show lab ranges, censored and unlisted results · S
Why: reports, Journal lines and the dev card read the new fields; stable output stays identical.
- Reports print lab ranges; "all in range" only with nothing out of range or unclear (E2 `<40 pmol/L` vs 20–150 is not); Levels skips censored values; "1 result" in dev, "1 results" kept in stable.
Spec: import doc §6.5.

**CHECKPOINT: audit + simplicity pass**

### BW-5 · Keep untouched results when a draw is edited (both flavors) · S
Why: the sheet rebuilds every result on save and loses ranges, qualifiers and `other:` results.
- An untouched save equals the original; toggling units twice changes nothing; an edited Hb keeps its range.
Spec: import doc §10.7.

### BW-6 · Lab ranges and other tests in the dev Bloodwork sheet · S
Why: imported ranges and unlisted results must be visible and editable.
- Captions "Lab range …" and "Reported as <0.3 …"; an "Other tests" section with a field per unlisted result.
Spec: import doc §7, §10.7.

### BW-7 · Lab text and value grammar · M
Why: forgiving about wrapping, strict about numbers, ranges and dates.
- Text and value tests green; an ambiguous date (`2025-04-03 | 04-03-2025`) and mixed thousands styles are asked, not guessed.
Spec: import doc §3.3–3.5, §4.1.

**CHECKPOINT: audit + simplicity pass**

### BW-8 · Marker vocabulary · M
Why: the aliases (incl. the brief's Dutch names) and units decide what maps without a question.
- Complete for every marker; aliases unique; the Dutch names pinned; the settled cases pass; factors match the existing conversions.
Spec: import doc §5.

### BW-9 · Read `protocoltracker-bloodwork-1` blocks · M
Why: find the block in any answer; refuse the prompt, a share link, JSON or a PDF with a clear message.
- Block fixtures pass: cut-off or gapped answers, a missing header, JSON, share links, the prompt (alone, and before an answer), a newer version, joined lines, empty or huge input, raw reports, PDFs, binary text, and headings, entities and escaped pipes inside a block; a cut-off segment's last result line is never imported.
Spec: import doc §3.1–3.2, §3.7–3.8, §4.5.

### BW-10 · Row pipeline and draft · L
Why: each value is proven by the tables or the report, asked, or left out with a reason.
- Every §13.1 fixture right at draft level; 0 questions for the clean Dutch one- and two-draw reports, an English LabCorp report (direct free T), a block with sex ranges and a unitless Hct with its range; meaning checks green.
Spec: import doc §4.2–4.3, §5.3, §7–9.

**CHECKPOINT: audit + simplicity pass**

### BW-11 · Review and entries · M
Why: review rules live in the domain, so the screen stays thin.
- Save stays disabled until a date is chosen (D2–D4); every fixture with every option saves valid entries with distinct keys that survive a backup round trip.
Spec: import doc §8, §9, §10.3.

### BW-12 · Generate the AI prompt · S
Why: generated from `BloodMarkers`, so a missing marker fails a test.
- Every marker with key, name, units and aliases; signature; header once; no example numbers; ≤ 6,500 characters; pasted back it is refused; prompt plus an answer reads the answer; the doc's text equals the generated text.
Spec: import doc §12.

### BW-13 · Save and undo several journal entries at once · S
Why: one transaction and one Undo for a multi-draw import; HIST-3 reuses it.
- Save and delete each run in one transaction; the journal emits once; saving a list twice adds no duplicates.

**CHECKPOINT: audit + simplicity pass**

### BW-14 · Bloodwork import screen · L
Why: the Start and Check steps.
- Start: help, Copy AI prompt, Paste answer, Open a file (≤ 200 KB). Check: to-check list, draw cards, row detail, draw dialog. The route exists only in dev.
- Tested: paste, date and unit questions, keep and leave out, Back, two draws saved, a second import, files.
Spec: import doc §10.1–10.5, §11, §13.3.

### BW-15 · Open the import from the sheet and land in Journal · M
Why: Log › Bloodwork › Import results makes it reachable.
- Only the new dev sheet shows "Import results" (not edit mode, not stable); saving lands on Journal › Bloodwork with "Bloodwork saved" or "N blood draws saved" and Undo.
Spec: import doc §10.6.

### BW-16 · Read the copied answer on return · M
Why: coming back from the chatbot needs no tap.
- On focus, new non-sensitive clipboard text not handled before is read, never the copied prompt; tested through the real focus trigger.
Spec: import doc §10.2.

**CHECKPOINT: audit + simplicity pass**

### BW-17 · Share lab results to ProtocolTracker Dev · M
Why: the brief's dev-only share target, without touching `MainActivity`.
- "Import bloodwork" receives shared text in dev and does not exist in stable; still no network permission; both R8 tasks pass.
Spec: import doc §11.

### BW-18 · Record the import in the project notes · XS
Why: the next agent learns the import from AGENTS.md.
- AGENTS.md layout and conventions (`other:` keys, lab ranges and the flag rule, new stored fields); README dev section; §6; `docs/releases/v0.5.0-dev.1.md`.

**CHECKPOINT: audit + simplicity pass**

**RELEASE: v0.5.0-dev.1** (bloodwork import). First run of the INF-3 path: confirm with `gh release view v0.5.0-dev.1` that it is a pre-release with exactly one asset, `ProtocolTracker-Dev-v0.5.0-dev.1.apk`.

### (c) Candidate ideas, alternating with polish

### POL-1 · Narrow, large-font and dark dev screenshot pass · M
Why: clipping claims come from arithmetic and dev was never rendered dark; the clipping items wait for this.
- Dev screenshots at 360 dp (font 1.0, 1.3) and 411 dp dark; a §7 line per claim, confirmed or not (week strip, item editor, chips, settings groups, Levels mode row, symptom chip, unit labels, nav labels, dose dialog date); no stable extras.

### HIST-1 · Web export parser: blood pressure, notes, symptoms · M
Why: nearly all BP, symptom and note history is in the web app, with no other way in.
- Takes the full export and "Export AI Review" only (not cycletracker-1, a PT backup, `{}`, `[]`); non-JSON fails cleanly.
- Naive times are UTC (Z, offsets ok; microseconds truncated); pulse 0 or missing = none; float and string numbers import; impossible BP skipped and counted.
- Dose-only symptom rows give nothing, mixed rows keep symptoms; mood 0 = none; unknown keys kept; old mood logs import; dose logs, goals, checklist ignored.
- Long notes shortened with a warning; a warning at the 5,000-log cap; native duplicates (same values) within ±15 min skipped, at 16 min imported; a rerun adds 0; same output twice; output backup-safe; all 40 web symptom keys known.

### HIST-2 · Web export parser: bloodwork · S
Why: months of draws for the marker trends.
- Exact conversions (estradiol 103.98 pg/mL, creatinine 0.9615); the 22 web markers match ours; a lab range (both sides) only when one side differs from the web default; implausible values skipped with a warning naming marker and date.
- Other keys, incl. prolactin, become `other:`; PDF-only rows skipped and counted; a native draw on the same day wins, with a warning; the day holds in any time zone.
Spec: import doc §6.3, §7.

### HIST-3 · Dev auto-detect, confirm dialog and save · S
Why: a one-time import through the existing "Import CycleTracker export" row; no new row, screen or setting.
- Dev: "Import web app history?" with span, counts, what is left out, skipped count, "There is no undo. Save a backup first if you want a way back." and warnings; one write, "Imported N entries"; again: "Nothing new to import from this file."
- Unknown symptom keys and `other:` results survive the database; stable still says "Unsupported export format; expected cycletracker-1"; cycletracker-1 unchanged; fits at 411 dp.

**CHECKPOINT: audit + simplicity pass**

### JV-1 · Journal views: chips become focused views (dev fork) · M
Why: dev Journal opens on a card, Symptoms is cut and Bloodwork off-screen (§4.4 P1).
- Chips All · Bloodwork · Blood pressure · Symptoms · Doses · Notes; Bloodwork visible at 411 dp. All: by day, no cards, no ISO dates. Other views: their card, then entries; compound chips move into Doses. The import lands on Bloodwork.

### JV-2 · Journal view cards and empty states (dev) · S
Why: every view needs an empty state with its action, and the Bloodwork card must stay short.
- Bloodwork card: out-of-range first, the rest behind "N in range ▾", which expands.
- Empty states: "No bloodwork yet" [Enter by hand], "No readings yet" [Add reading], "No symptom logs yet" [Log symptoms], "No doses logged yet", "No notes yet" [Add note], "Nothing logged yet"; each with a one-line body.

### BW-19 · Import from the Journal Bloodwork view · S
Why: the Bloodwork view's one action.
- "Import results" is the card's header action and the empty state's primary button; "Enter by hand" becomes secondary.
Spec: import doc §10.1.

### BLOO-1 · Domain: marker history and draw age · S
Why: the domain half of the trend and the draw hint.
- History oldest first with lab and entry, stable ties; draws without the marker, unknown keys and other kinds give nothing; `other:` keys work; `markerTrends` unchanged.
- Age of the latest past draw: days to 13, weeks to 181 days, then months and years; future draws ignored; a draw at 23:30 is "yesterday" at 00:10 in any zone.

**CHECKPOINT: audit + simplicity pass**

### BLOO-2 · Draw hint in the Log menu and the card label (dev) · S
Why: the brief's "draw due" hint as a plain fact.
- "Last draw 3 days ago" in the Log menu and the card label; no past draw: "Lab results of a blood draw"; no schema change; Today tests and stable unchanged; the longest text fits at 360 dp.

### SIM-1 · Scrubbing is how Levels charts work in dev (absorbs POL-11) · S
Why: by default a chart eats vertical drags, so Levels cannot scroll from it.
- Dev always scrubs (the stored switch ignored, not deleted); "Slide along a chart to read it. Two fingers pan and zoom."; a vertical swipe from a chart scrolls, a sideways drag reads.

### SIM-2 · Scrub ticks follow the phone; no vibration switch · S
Why: a second switch for one feature, and ticks ignore touch feedback on Android 8–12.
- Dev always ticks, stays silent when touch feedback is off, and ticks firmly only for logged items; stable unchanged.

### BPTR-1 · Domain: 7-day averages of blood pressure · S
Why: "is my BP creeping up?" is a weekly question Journal cannot answer.
- 26 weekly buckets back from now, oldest first, empty weeks dropped, older readings ignored; the newest equals the BP card's 7-day average; edges exact; systolic stays above diastolic; 1,000 readings count right.
- Captions "Last 7 days · 127/81 mmHg · 9 readings" and "7 days to 19 Sep · …", singular "1 reading", in either date order and zone.

**CHECKPOINT: audit + simplicity pass**

### BLOO-3 · TrendChart: drawing and static mode · S
Why: bloodwork, BP and mood need one small time chart; `LevelChart` is PK-bound stable code (§4.4 P6).
- x by time; y padded and widened to the band; one-sided bands reach the edge; a minimum span holds; a static chart lets the page scroll and ignores taps.

### BPTR-2 · TrendChart gestures · M
Why: BP and mood read older points by tap or slide (§4.7).
- A tap selects the nearest point within 24 dp; a sideways drag moves it with a light tick; a vertical drag scrolls; no pan or zoom; back gesture excluded.

### BPTR-3 · BP chart in the Journal Blood pressure view (dev) · S
Why: the trend where the owner already reads his readings.
- With 2+ weeks: the chart (systolic solid, diastolic dashed) and "Each point is a 7-day average"; otherwise nothing; none on All.
- The newest point matches the header's 7-day average; stable unchanged; no clipping at 360 dp; lines distinct in dark.

### SIM-4 · Levels reading under the chart, not in a bubble (absorbs part of POL-12) · M
Why: the bubble hides the curve and the lab diamond, and repeats the time.
- Dev charts draw only the cursor, so the diamond stays visible; the panel starts "<time> · est. 799 ng/dL"; its overline loses the time; "Last dose" uses the short name.

**CHECKPOINT: audit + simplicity pass**

### SIM-5 · Lab results on the Testosterone curve: legend and values (absorbs POL-12, EEST-2) · S
Why: nothing explains the diamonds, and the panel hides the numbers that matter.
- A "Lab result" legend; on the Testosterone chart "Bloodwork · T 1100 ng/dL · E2 45 pg/mL" in Settings units, only the markers present, qualifiers kept; otherwise the generic line with "1 result"; stable's string pinned; fits two lines at 411 dp.

### BLOO-4 · Marker sheet from the dev Bloodwork card rows · M
Why: read 3–10 draws per marker without opening each; costs nothing until tapped.
- Card rows open a sheet: a static chart with 2+ plottable results and a caption naming whose range is shaded, then every result newest first, flagged by its own range.
- Rows lose "before X"; one result: no chart; "<5" listed, not plotted; a ref only where it differs from the band; Settings units; unlisted results behind "Other tests (N)", list only; band visible in dark.

### SIM-11 · Symptoms sheet: recent first, attributed headings, no advice (absorbs POL-21, SYMP-4) · M
Why: each log scrolls past 45 chips; the caption is advice; headings and the E2 summary classify.
- "Your recent symptoms" (90 days, up to 12) first, all 45 folded; headings "Often listed with low estrogen", "…high estrogen", "Other"; no caption; lines drop the E2 summary.
- Recent libido chips say "(low E2)" or "(high E2)"; a new log needs a symptom, mood or hair shedding; a chip that clips at 1.3 may wrap; stable unchanged.

### OTHE-1 · Earlier-pick resolver (domain) · S
Why: at 08:00, picking 23:30 stores a future time.
- A new entry gets the latest matching moment, never after now; an existing entry keeps its date; right around midnight and DST.

**CHECKPOINT: audit + simplicity pass**

### OTHE-2 · Earlier… reaches yesterday in the dose, BP, note and symptoms sheets (dev) · M
Why: last night's reading or dose, logged in the morning, lands on the right day.
- At 00:15, 23:30 means yesterday in dev, today in stable; the label reads "Time · Yesterday" in dev, "Time" in stable.

### SIM-14 · One Log menu on Today and Journal (dev) · M
Why: two add menus with different rows, no snackbar in Journal, overlapping subtitles.
- Journal's "+" opens Today's menu (incl. Extra dose) with the same snackbars and Undo; subtitles Note "Free text for anything else", Symptoms "Symptoms, mood and hair shedding".

### DISC-1 · Settings › About: "In this dev build" (dev) · XS
Why: nothing says what the dev build adds, and it needs no tour or badge (§4.4 P4).
- One paragraph in About naming only what exists, how to read a chart, and that it installs beside the regular app and its backups restore there.

**CHECKPOINT: audit + simplicity pass**

**RELEASE: v0.5.0-dev.2** (history import, Journal views, BP and bloodwork trends, Levels reading, symptoms sheet, one Log menu)

### SIM-7 · Compare mode removed from dev · S
Why: not weekly use; it costs a switch, a row and three explanations, and its baseline looks like data.
- No "Compare" in dev even with the stored switch on; stable unchanged.

### SIM-3 · Settings › Experimental hidden in dev · S
Why: after SIM-1, SIM-2 and SIM-7 its switches do nothing in dev.
- 7 settings rows in dev, 8 in stable; route and keys kept.

### SYMP-1 · Domain: mood trend · S
Why: mood is the one symptom value dense enough for a picture.
- Mood points over the 90 days up to now, or up to the latest mood when that is older; oldest first; none with mood on fewer than 2 days.

### SYMP-2 · Mood chart in Journal › Symptoms (dev) · S
Why: the compact mood-over-time view; nothing else is added.
- A "Mood" card: dots on 1–10 and one detail line for the selected log (latest first); old moods show their own dates; none with mood on one day, none on All, none in stable; dots visible in dark; DISC-1 mentions it.

**CHECKPOINT: audit + simplicity pass**

### SIM-8 · One control row on the Levels overview (absorbs POL-13) · S
Why: up to three control rows push the first chart down; the mode is a rare what-if.
- The dev overview has only the range row; the detail keeps the mode row with "Solid: logged doses · dashed: plan".

### SIM-9 · Figures on the detail screen only, plain labels (absorbs POL-14) · S
Why: figure chips on every card are a dashboard; "90% OF STEADY" is jargon; 308.9 vs 309.
- Dev cards read "est. now 309 ng/dL"; detail labels NOW · STEADY RANGE · STEADY AVERAGE · NEAR STEADY AFTER · MOSTLY CLEARED BY, rounded like the jump bar.

### POL-2 · Today dose detail keeps numbers with their units (dev) · S
Why: details break as "0.18 / mL".
- A number never wraps away from its unit (Today, Journal, scrub lines); day prefix "Thu evening".

### POL-3 · Today rows without the category tag (dev) · S
Why: the tag takes about 90 dp and pushes names to two lines.
- No tag on dev Today and Day sheet rows; Plan and the dose sheet keep it.

**CHECKPOINT: audit + simplicity pass**

### SITE-1 · Injection sites: catalog, rotation rule, `DoseLog.site` (domain) · M
Why: 3–7 pins a week; the one-tap check records the suggestion for free.
- 12 sites, short ("L VG") and long labels. Suggestion: the site that followed the last one before, else its mirror, from the last 24 taken logs by time taken (skipped ignored); an abandoned site drops after one override; none when the newest taken log has no site.
- Backups without sites encode byte-identically; old backups decode; reports unchanged.

### SITE-2 · Room 3→4 site column and a site-aware write path · M
Why: the only planned migration.
- A real v3 database migrates unchanged (v2 goes 2→3→4); re-logging keeps a site unless cleared; batch logs store shown sites and never overwrite; backups keep sites.

### SITE-3 · Site row in the Log dose sheet (dev) · M
Why: where a site is chosen or changed.
- Injectables only, extras too: the suggestion preselected with "Last: X · <day>", or "Choose site" when untracked; a taken dose reopens with its site; tap to clear; Skip stores none; 48 dp chips announced by full name; stable shows none and keeps sites.

**CHECKPOINT: audit + simplicity pass**

### SITE-4 · Today: suggested site on rows, recorded by one tap (dev) · M
Why: the suggestion shows before injecting, and the check records it.
- Pending and missed injectable rows end " · R delt"; a check stores it ("Test C taken · R delt") and the next row moves on; a site-less log removes the suffix; Log all never repeats a site per compound; Day sheet checks store none.

### SITE-5 · Dose reminder shows and records the suggested site (dev) · S
Why: logging from the reminder must not break the chain.
- Reminder lines end " · R VG" and Taken stores the shown sites, never one twice per compound; a stale notification never overwrites a log; stable notifications pinned unchanged.

### SITE-6 · Journal dose line shows the site (dev) · S
Why: the site shows where past doses are read.
- "125 mg · 0.63 mL · R VG" in dev; editing keeps the site; stable unchanged.

### SITE-7 · Site guards, screenshots and docs · S
Why: sites are guarded and documented before release.
- Entry-point rows and dev screenshots for every site surface; AGENTS.md convention; DISC-1 and the release note mention sites.

**CHECKPOINT: audit + simplicity pass**

**RELEASE: v0.5.0-dev.3** (injection sites, mood chart, calmer Levels overview, Today detail)

### POL-9 · Journal rows share one anatomy (dev) · S
Why: dose rows look different and start 32 dp left of the other rows.
- One row layout (icon, title, muted detail, time, ≥ 48 dp); doses show the short name; skipped reads "Skipped".

### SIM-15 · One editor per logged dose (dev) · M
Why: one record has two editors, and extras on Today cannot be opened.
- The dose sheet's edit mode opens from Journal and from the extras on Today and in the Day sheet; saves keep the snapshot, amount and key; a new date moves the log; the site can change; Delete offers Undo; stable keeps its dialog.

### POL-4 · One "Logged today" section on Today (dev) · S
Why: "Also logged today" is unexplained and squeezes names at 360 dp.
- One section by time; every row ≥ 48 dp and opens its editor.

### POL-10 · Edit sheets get "Delete entry" (dev) · S
Why: entries can only be deleted by an invisible long-press.
- BP, note, symptom and bloodwork edit sheets show "Delete entry" with Undo; stable has none.

**CHECKPOINT: audit + simplicity pass**

### OTHE-4 · Latest taken dose per compound (domain) · S
Why: before an as-needed dose: "when did I last take it?"
- Latest taken per compound, planned or not; skipped and future ignored; no start amount in a unit the form does not offer; no data-layer change.

### OTHE-5 · Extra-dose form shows "Last taken" and starts at that amount (dev) · M
Why: the answer where the dose is logged.
- "Last taken: Yesterday 21:30 · 0.5 mg" and "Log 0.5 mg", which saves 0.5 mg; no earlier log: no line, empty field; stable unchanged.

### POL-7 · Extra-dose picker: plan compounds first (dev) · S
Why: extras are nearly always plan compounds.
- "In your plan" first when the search is empty, not repeated below.

### RECO-1 · Domain: strength from vial and water · XS
Why: peptides and hCG have no strength, so no volume shows.
- Strength = vial ÷ water, 2 decimals; none for zero, negative or absurd input; powders are injectable peptides, hCG and HGH.

**CHECKPOINT: audit + simplicity pass**

### RECO-2 · "From vial and water…" on Strength (dev) · S
Why: turns "5 mg vial, 2 mL water" into the Strength used everywhere.
- The dialog shows "Strength X unit/mL" or "Check the amounts." (Use disabled); Use fills Strength, stored only on Save; Cancel changes nothing; hCG 5000 IU + 5 mL gives "500 IU · 0.5 mL" on Today; 1.67 survives reopening; Test C has no link.

### SIM-12 · Bloodwork sheet: import first, tracked markers up front (dev) · M
Why: the sheet opens as 24 fields with 24 reference lines for one or two values.
- Import row first; markers with history up front (else hormones and hematology), the rest folded; one "High · ref …" line under an out-of-range value; stable unchanged.

### SIM-6 · The Levels reading clears when it no longer applies (dev) · S
Why: after a range change the panel describes a moment off the chart.
- It clears on a range change and Back to now, and stays after the finger lifts.

### POL-26 · 48 dp touch targets (dev) · XS
Why: Journal lines, extra-dose rows and the Week toggle are 44 dp.
- 48 dp in dev, 44 dp in stable.

**CHECKPOINT: audit + simplicity pass**

### POL-19 · Item and compound editor details (dev) · S
Why: small editor faults found in review.
- The any-time caption shows only for any-time; the peak suffix follows the level unit. If POL-1 confirms: title "Edit item", Starts/Ends stacked.

### POL-23 · Settings summaries follow the time format (dev) · S
Why: summaries ignore the 12-hour format and touch the chevron at 411 dp.
- Times follow the format; Today reads "Week bar collapsible · scheduled time"; summaries ellipsize.

### POL-27 · Warn text contrast in light Plum and Clay (dev; absorbs SIM-13's contrast check) · S
Why: "2 miss" is below WCAG AA in light Plum and Clay, and no test covers warn.
- A darker dev warn; a dev-only test checks warn on every background in every scheme.

**CHECKPOINT: audit + simplicity pass**

### Clipping fixes, only where POL-1 confirmed them
Close each as "not reproduced" in §7 when POL-1 does not show it. Accept: no clipping at 360 dp and font 1.3.
- **POL-6** · S · week strip "2 miss": a warning icon plus the count.
- **POL-20** · S · quick-amount chips: less padding, ellipsis instead of clip.
- **POL-25** · M · clipping Settings option groups become radio rows; the Levels range row stays.

### MISS-1 · Plan days before the first dose log are not "missed" (dev) · S
Why: pre-PT plan days show as weeks of "missed" doses in the Phase-range report (§4.1).
- Dev reports count missed doses and adherence from the first dose log and still list older entries; existing report tests pass unchanged; stable output byte-identical.

**CHECKPOINT: full audit of the dev build**

**RELEASE: v0.5.0-dev.4**

Then audit every dev screen again and write the next backlog.

### Later (dev; low value; take when Open is empty, or when a nearby change touches the same code)
- **POL-5** "Nothing due today" plus the next due dose on a blank Today.
- **POL-8** Day sheet empty text "Nothing scheduled on this day."; notes in Journal lines ellipsize.
- **POL-15 / SIM-10** one short estimate note on the Levels overview; the full note stays on the detail.
- **POL-16** "Back to now" gets the MyLocation glyph.
- **POL-17** the Levels empty state gets "Open plan".
- **POL-18** Plan card: band without DAYS; timing row "Daily · morning".
- **POL-22** Bloodwork sheet "Conventional" / "SI" labels, only if the unit labels clip.
- **POL-24** Settings copy: shorter units note, clearer time-recording and Reports text, restore date via `Formats`.
- **SIM-16** Levels reading panel: the last dose plus journal entries, not every nearby dose.

---

## 4. Decisions

### 4.1 Candidate ideas (brief section 2)

Each idea was proposed minimal, then cut by a skeptic unless the reasoning was wrong (one resolution on top: §4.7).

| Idea | Decision | Minimal form | Reason |
|---|---|---|---|
| Web app history import | **Accept, smaller** (HIST-1…3) | The existing "Import CycleTracker export" row takes the web file in dev and confirms with counts. BP, notes, symptoms and draws; insert only; duplicates skipped. No doses, ticks, checklist, weekly notes, settings or PDFs. | Nearly all his BP, symptom and lab history is there, with no other way in. Cut: upsert (would overwrite edits), weekly and dose notes, a per-result matcher; the report fix became MISS-1. No Room or backup change. |
| Bloodwork over time and a "draw due" hint | **Accept, smaller** (BLOO-1…4) | "Last draw N weeks ago" in the Log menu and card; card rows open a read-only sheet per marker. | A due date needs an interval: a fixed one is medical advice, a personal one is a setting. Rejected: due dates, badges, notifications, Today lines, sparklines, deltas, a compare picker, targets, critical tiers, range pickers, selection in the sheet, charts for unlisted markers. |
| Injection site rotation | **Accept, smaller** (SITE-1…7) | Per compound, quiet until a site is picked: suggested on Today and the reminder and recorded by the one tap; a Site row in the dose sheet; shown in Journal. | 3–7 pins a week; the web app had rotation, but its one-tap paths never recorded a site. Cut: a report line, and waiting for the merged dose editor (SIM-15); the widget records none. |
| Symptom trends and mood | **Accept, smaller** (SYMP-1…2) | A mood chart in Journal › Symptoms, with mood on 2+ days. | Mood is the one dense value. Rejected: counts, per-symptom or E2-group trends, averages, a joining line, a hair chart, overlays, a range control. |
| Personal estimated E2 curve | **Reject** | Measured E2 shows as lab results (SIM-5, BLOO-4); MODELS.md says why (DOC-1). | The two-sentence version repeats the last draw and misses the as-needed AI, Dbol and hCG changes he steers E2 with, so it is most wrong when read. Reopen when as-needed use stops, or on request after 2+ native draws; calibrate then only on plain past E2 values above 0, an absolute ng/dL testosterone curve, taken logs (never the plan), first T dose ≥ Tmax + 4·t½ and latest ≤ Tmax + 2·t½ before the draw. |
| Supply (days left, heads-up) | **Reject** | Nothing built. | Counts need re-entry at nearly every container; dead space exceeds the warning window; lead times would be a setting; he holds the vial at every dose; a doses-per-vial line in the vial dialog was dropped too (it opens only at setup, and an estimate would break its plain arithmetic). Revisit if running out actually happens: a separate table with a real migration (not on the compound or plan item), one "On hand" field, a fixed 14-day Today hint, no notification. |
| Peptide reconstitution | **Accept, smaller** (RECO-1…2) | "From vial and water…" fills Strength for powders; nothing else stored. | The missing step from "5 mg vial, 2 mL water" to mg/mL; volumes then show everywhere. Cut: a per-dose line and a U-100 caption (the band shows it), a 4-decimal strength (it drifts to 2 on the next save). |
| Blood pressure trend | **Accept, smaller** (BPTR-1…3) | 7-day averages over 26 weeks in Journal › Blood pressure. | A weekly question for a daily telmisartan user; weekly points avoid a zigzag and match the card. Rejected: a "healthy" band (advice), raw points, pulse, legend, range picker, a morning/evening split, overlays, a report change, a "chart appears later" hint. |
| Anything else from the web app | **Accept, smaller** (OTHE-1, 2, 4, 5) | "Earlier…" reaches yesterday and names the day; extra doses show "Last taken" and start at that amount. | Both improve daily sheets; the first fixes future timestamps. Cut: a snackbar day suffix (OTHE-3). The rest is rejected (§4.2). |
| Plan days before the first log | **Accept, small** (MISS-1) | Dev reports count from the first dose log. | Found while cutting the history import: pre-PT days read as missed. |

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
- One bulk save and undo serves both importers.
- An invalid stored lab range (a side negative or not finite, or low above high) is ignored: the marker's default range applies, and an unlisted result gets no flag (BW-2).

On-device text recognition is **not built** (import doc §2.4); INF-2 guards the no-network rule; owner check 47.

Brief section 1 is covered in full: flow and ways in (§1, §10–11: BW-14…17, BW-19); review and saving (§8–10: BW-11, BW-13); format, parser, several dates (§3–4: BW-7, BW-9, BW-10); Dutch aliases (§5.4: BW-8); lab ranges (§6: BW-2, BW-4); prompt and help (§12: BW-12, BW-14); OCR (§2.4); tests (§13).

### 4.4 Placement (where things live in dev)
- **P1 Journal views.** Chips become focused views: at most one card, then entries; All is a plain timeline. Today gains no cards. Rejected: a Health tab or screen, cards on All, BP-card sheets, dropdowns, wrapped or abbreviated chips, range selectors, trends on Levels.
- **P2 Import.** One screen, three ways in: the Bloodwork sheet, the Journal Bloodwork view, a dev-only share target. `MainActivity` untouched. After a save: Journal › Bloodwork with Undo; after a share: a toast, back to the chatbot. Rejected: intent handling in `MainActivity`, an "Open with" filter, a text-selection menu item, a sheet-based review, a Settings entry, a sixth Log row, a paste field.
- **P3 Merge.** One Log menu and one dose editor; stable keeps its old paths.
- **P4 Discoverability.** Facts and empty states that name the action, plus one paragraph in About. Rejected: DEV tags, intro cards, hint settings.
- **P5 Stable guard.** Four shapes behind `BuildConfig.DEV_FEATURES`: (a) a `devOr` value switch; (b) an optional hook with a no-op default; (c) a branch with the old code untouched; (d) an `XxxDev` fork for a section with more than about three differences. New features in new files; `app/src/dev` holds only resources, the manifest and the share activity. No new settings; old toggles ignored in dev, never deleted. Dev-only ViewModel fields default to empty and are computed only in dev.
- **P6 TrendChart.** One chart for bloodwork, BP and mood; `LevelChart` untouched. A band only for bloodwork (named in a caption); none for BP or mood, which would be advice. The selected point is described under the chart, never in a bubble. It replaced a raw BP chart and marker-sheet selection.

### 4.5 Simplify and polish (brief section 3)
- **Levels:** one slide-to-read gesture, vertical scroll free, no switch (SIM-1, SIM-2); Experimental and compare gone from dev (SIM-3, SIM-7; stable loses them at graduation, said in that release note); one control row, one "now" per card (SIM-8, SIM-9); reading under the chart, lab legend (SIM-4, SIM-5).
- **Sheets and paths:** recent symptoms first (SIM-11); import first in Bloodwork (SIM-12); "Delete entry" (POL-10); one Log menu, one dose editor, one "Logged today" (SIM-14, SIM-15, POL-4).
- **Merged:** POL-11 → SIM-1; POL-12 and EEST-2 → SIM-4, SIM-5; POL-13 → SIM-8; POL-14 → SIM-9; POL-15 → SIM-10; POL-21 and SYMP-4 → SIM-11; POL-22 → SIM-12; SIM-13 → JV-2, BLOO-4, POL-27.
- **Kept:** the range row, two-finger pan and zoom, the jump bar, the lingering cursor, tick density, diamonds for total T only, mood 1–10 and five hair levels, the units control, five Log rows, Journal's "+" in dev, the mode row on the detail screen, "Back to now".
- **Rejected:** scrub opt-in, hiding "Back to now", removing the mode control, a smaller compare (fallback on record: a push screen with the plan baseline only), regrouping symptoms, symptom search, an "Add result" picker, dropping the Bloodwork sheet's reference lines completely, vibration off by default, no firm ticks, merging Note and Symptoms, back-gesture exclusion on Levels charts.

### 4.6 Visible fixes and stable
Stable is frozen: visible shared-UI bugs are fixed in dev only and move over in one batch when dev graduates; invisible data-safety fixes go into both. Clipping is fixed only once a screenshot shows it. Low-level shared components get verified value switches, never forks. No muscle-memory differences for small gains.
- **Left in stable, fixed in dev:** "1 results", ISO day headers, the Reports text, the "Earlier…" future-time hole, warn contrast, the clipped dose dialog, the E2 summary, 44 dp targets.
- **Left in both:** Plan's filled "Add", the shared calendar glyph, eight Settings pages, Save placement in the two editors, sheet paddings, the dark band colour, planned tick alpha, week cell widths, y labels at 1.3, odd y ticks, the Today eyebrow comma, nav labels at large font, warn on wallpaper colours.

### 4.7 Resolutions made in this backlog
- BW-1 comes first: docs go into git before build commits cite them.
- TrendChart: BLOO-3 draws and has a static mode; BPTR-2 adds the gestures for BP and mood.
- The history import stays insert-only without Undo; the dialog shows the counts first.
- Dev releases change only the dev flavor's `versionName`.
- The import doc's order holds for BW-2…BW-18; BW-19 follows the Journal views.
- Sites (3→4) is the only planned migration; clipping items wait for POL-1.

---

## 5. Owner checks

Only the owner can verify these. Each default holds until he answers.

| # | Check | Default meanwhile | Item |
|---|---|---|---|
| 1 | Run 2–3 real Dutch reports (PDF and photo) through the chatbot and the import: how many questions? | Expect 0; a recurring caption means a missing alias; his answers become fixtures. | BW-8, BW-10 |
| 2 | Which copy paths his chatbot apps offer | Any whole answer works; the help says "Tap Copy …, not Share". | BW-14 |
| 3 | Is the clipboard toast on return fine, and does the automatic check fire? | Yes; "Paste answer" always works. | BW-16 |
| 4 | Does "Import bloodwork" appear when sharing a file? | "Open a file" covers it. | BW-17 |
| 5 | 12:00 for draws without a printed time | Accepted; editable. | BW-3 |
| 6 | The men's range when a report prints both | Men's range, with a caption. | BW-10 |
| 7 | After a share import: back to the chatbot, or into Journal? | Toast and return. | BW-17 |
| 8 | A same-day result with another value starts left out | Left out; one tap keeps it. | BW-11 |
| 9 | Share the prompt into the chatbot instead of Copy? | Copy stays. | BW-14 |
| 10 | Save the prompt in a chatbot project | A tip in the import doc. | BW-12 |
| 11 | Which web file: full export, or "Export AI Review" (logged in as himself; guest exports blank notes) | Both accepted. | HIST-3 |
| 12 | Dose history from the web app | Not imported. | HIST-1 |
| 13 | After the history import, spot-check BP, a draw and a symptom log against the web app | Tests cover the documented shapes only. | HIST-3 |
| 14 | Is "Last draw 9 weeks ago" enough? | Fact only; never a setting. | BLOO-2 |
| 15 | Do the hematocrit, T and E2 sheets read right on his data? | As specified. | BLOO-4 |
| 16 | Charts for unlisted markers? | List only. | BLOO-4 |
| 17 | Does he log pins from the widget? | The widget has no sites. | SITE-5 |
| 18 | The 12 sites, labelled "L VG" | These 12. | SITE-1 |
| 19 | Does the site suffix read well at his font size? | Keep it. | SITE-4 |
| 20 | Does he rotate hCG or peptide pins? | Available, quiet until used. | SITE-3 |
| 21 | Does he still log mood weekly? | Build it; sparse mood shows nothing. | SYMP-2 |
| 22 | Mood as dots or a line? | Dots. | SYMP-2 |
| 23 | A hair-shedding trend? | None; it would replace mood, not add a toggle. | SYMP-2 |
| 24 | Is his AI or Dbol use still as needed? | Yes, so no E2 estimate. | §4.1 |
| 25 | Did he use the web app's E2 estimate weekly? | No evidence; rejection stands. | §4.1 |
| 26 | "T" and "E2" as labels in the reading line | As specified. | SIM-5 |
| 27 | Has running out actually happened lately? | No; supply stays rejected. | §4.1 |
| 28 | Oils in mL and powders in units at once? | One global setting. | RECO-2 |
| 29 | Blend vials | Enter each compound with the same water. | RECO-2 |
| 30 | A custom powder without the vial link | Move it to Peptides. | RECO-1 |
| 31 | Does the BP chart show the expected cycle changes? | As specified. | BPTR-3 |
| 32 | 26 weekly points or 13? | 26. | BPTR-1 |
| 33 | Morning and evening as separate lines? | No split. | BPTR-1 |
| 34 | Is "Each point is a 7-day average" clear without a legend? | Yes. | BPTR-3 |
| 35 | A date picker for older entries? | No: one day back; doses can move in the editor. | OTHE-2 |
| 36 | Extra dose starts at the last amount or empty? | The last amount. | OTHE-5 |
| 37 | Does he log as-needed doses in PT? | Build it anyway. | OTHE-5 |
| 38 | Do the 360 dp / 1.3 screenshots match his phone? | The screenshots decide. | POL-1, JV-1 |
| 39 | Drop the category tag on Today? | Dropped in dev. | POL-3 |
| 40 | Symptom heading wording | "Often listed with low/high estrogen", "Other". | SIM-11 |
| 41 | Recent symptoms: 90 days, 12 chips | 90 and 12. | SIM-11 |
| 42 | How the scrub ticks feel | Light per day, firm per log. | SIM-2 |
| 43 | Does he use compare? | Removed in dev. | SIM-7 |
| 44 | Bloodwork fields up front with no history | Hormones and hematology. | SIM-12 |
| 45 | Does sliding near the edge trigger Back? | Excluded on TrendChart. | BPTR-2 |
| 46 | The "Back to now" glyph | MyLocation. | POL-16 |
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

---

## 7. Audit findings

| Date | Finding | Status |
|---|---|---|
| 2026-09-27 | Today's Log button (`ExtendedFloatingActionButton`) merges no text: its semantics node is a Button without "Log", so TalkBack may announce only "Button". Tests find it through the unmerged tree. Shared UI; a fix changes stable's accessibility tree, not its pixels. | Open: decide with a polish item (a `contentDescription`/`semantics` on the button, both flavors, pixel-identical) |
| 2026-09-27 | `JournalState()` starts with `empty = false`, so the filter chips render while the Journal loads and an empty Journal flashes chips before its empty state. Tests anchor on loaded content, not on "All". | Open: low; stable behaviour, leave unless a Journal item touches the loading state |
| 2026-09-27 | The other Robolectric Compose tests (`AppNavTest`, `TodayScreenTest`, `Levels*Test`, `UnitsSettingsTest`) still use the v1 `createComposeRule`, whose unconfined effect dispatcher can resume a frame on a Room background thread (`CalledFromWrongThreadException`, seen once in CI in `DevEntryPointsTest`). `ScreenshotTest` stays on v1 (guard baseline). | Open: move a class to `junit4.v2.createComposeRule` when it next changes or if it flakes in CI |
| 2026-09-27 | `ManifestPermissionsTest` ends before the app's startup coroutine opens the database, so CI logs an uncaught `SQLiteCantOpenDatabaseException` from a deleted Robolectric data dir. The test passes; the log line is noise. | Fixed (9652d1f): the test runs with a plain `Application`, so no startup work; still fails with `INTERNET` added |
| 2026-09-27 | Checkpoint 1 (INF-1 to DOC-1): INF-3 replaced the dev `versionNameSuffix` with a hand-set `versionName`, so the dev version no longer follows stable, and nothing compared a tag with the APKs: a forgotten edit would publish a release whose Settings shows the wrong version. | Fixed (d324c56): CI step "Check version names" reads each release APK with `aapt2 dump badging` and fails unless a `-dev.` tag equals dev's `versionName` and a `vX.Y.Z` tag equals stable `X.Y.Z` and dev `X.Y.Z-dev`; dry-run against the debug APKs and a stub for every path |
| 2026-09-27 | Checkpoint 1 screenshots: the empty Journal (plan, nothing logged) shows the collapsed "Adherence · 7 days / 30 days" card under "Nothing logged yet", so the empty state is not the only thing on the screen. Stable shows the same. | Open: low; a Journal polish item decides whether dev hides adherence until the first dose log |
| 2026-09-27 | Checkpoint 1 review, no change needed: `DevEntryPointsTest` covers all six gates (`grep DEV_FEATURES\|devOr(`), and each check follows an anchor from the same state emission (the Bloodwork card and `GroupView.measured` come with the rows and views they wait for); no Room, backup, R8 or manifest change in the batch; stable screenshots unchanged. | Closed |
| 2026-09-27 | Build machine: `AccessDeniedException` / "Failed to clean up output files" kept coming back (also with `PT_REDIRECT=1`, and in the `stable-base` clone) because folders under `build/` carried the Windows read-only attribute, which Java cannot delete through. Clearing it (PowerShell: every item under each `build` folder, `Attributes -band -bnot ReadOnly`) fixed every run. | Closed (machine quirk, no code change) |

---

## 8. Blocked

Nothing blocked.
