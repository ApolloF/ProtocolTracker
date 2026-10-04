# Bloodwork import with help from AI

Design, 2026-09-27: how a lab report gets into the dev build, and why. Item ids (BW-n, BWI-n, INF-n, HIST-n, TR-n)
refer to [BACKLOG.md](BACKLOG.md), which holds the build order. The detailed build specification (full grammar, domain
model, file-by-file changes, every test fixture) is kept with the design notes outside the repo.
Written while the app had a `dev` and a `stable` flavor; since 0.5.0 there is one app with the dev behaviour, so the
gating and stable-compatibility notes below are history.

## Scope of v1

The first version is **paste, review, save** (BWI-1 to BWI-5, plan change 2026-09-27 in the backlog):

- **One way in:** Log › Bloodwork › **Import results** in the dev Bloodwork sheet. The answer comes back only by
  **Paste answer**.
- **No questions.** Uncertain values are left out with a reason instead of asking. Where this document describes a
  question, v1 leaves that result out and shows the question's text as the reason (Q1-Q4); a draw whose date is
  uncertain is left out whole, with its reason (D1-D4). Answer buttons, alternative readings, "Choose date", the draw
  dialog and "Change answer" are **Later**. A row can still be left out or kept with one tap, and a same-day result
  with another value still starts left out (C8).
- **Kept:** the format and parser (§3-4), the Dutch aliases (§5.4), lab ranges and the flag rule (§6), several draw
  dates (§3.5), unlisted results (§7), duplicates (§9), the generated prompt and its test (§12), a modest fixture set
  (§13.1).
- **Later:** reading the copied answer when the app regains focus (§10.2), the dev share target "Import bloodwork"
  (§11), opening a file (M9, M10, M13), and importing from a Journal Bloodwork view (BW-19).
- A draw without a printed time is saved at 12:00; the time can be corrected afterwards in the Bloodwork sheet.

The sections below keep the full design; each part that v1 leaves out says "Later".

---

## 1. Summary of the flow

The owner imports a lab report every few months, on his phone, usually a Dutch PDF. The app has no network access, so a
chatbot of his choice reads the report and the app reads the chatbot's answer.

1. **Journal › + › Bloodwork › Import results** (or from Today › Log). The import screen opens at **Start**.
2. **Copy AI prompt.**
3. In any chatbot: paste the prompt, attach the PDF or photo, send, then tap **Copy** on the answer.
4. Switch back and tap **Paste answer**; **Check** opens. Later: Start reads the copied answer by itself, and **Open a
   file** and sharing text to the app are other ways in.
5. **Check** shows each blood draw with its date, lab and results. A clean Dutch report leaves nothing out. What only
   the report can settle (a unit, a number, two values for one marker, or the date) is left out with its reason in v1;
   asking about it is Later.
6. **Save 28 results.** One journal entry per draw; Journal › Bloodwork opens with "Bloodwork saved" and Undo.

Decisions in brief:

| # | Question | Decision |
|---|---|---|
| 1 | Flow | One screen, two steps (Start, Check), reachable only in dev. |
| 2 | Answer back | v1: "Paste answer". Later: clipboard read when Start regains focus; "Open a file"; share. No paste field. |
| 3 | Format | `protocoltracker-bloodwork-1`: header, `lab:` and `date:` lines, one pipe line per result, `end`. |
| 4 | Forgiving vs strict | Forgiving about wrapping; a value is saved only when its meaning is proven or chosen. |
| 5 | Questions | v1: none; an uncertain unit, lost decimal, number reading or two values leaves the result out with its reason, an uncertain date leaves the draw out. Later: asking, with an open date blocking Save. |
| 6 | Settled by rule | Name vs key, a missing unit only one unit fits, the men's range, a chatbot correction, a thousands separator the block's own style proves, plain "Glucose" (kept unlisted): shown as a caption. |
| 7 | Meaning checks | Range fit, lost decimal, repairs for impossible values, wide plausibility limits. |
| 8 | Lab ranges | Stored per result; when present, the lab range alone sets the flag. |
| 9 | Censored values | `<` or `>` kept; no flag when the true value can lie on either side of a limit. |
| 10 | Unlisted results | Saved as `other:<printed name>` with the printed name and unit. |
| 11 | Storage | Five optional fields on `MarkerResult`; no migration, no backup change, stable's JSON unchanged. |
| 12 | No printed time | 12:00 local, never asked. |
| 13 | Duplicates | Matched by content (§9.3). Already saved results are skipped; a same-day result with another value starts left out; existing entries are never changed. |
| 14 | Prompt | Generated from `BloodMarkers`; a test keeps §12 equal to it. |
| 15 | On-device OCR | Not built (§2.4). |

---

## 2. Design and trade-offs

### 2.1 Principles

- **Forgiving about wrapping.** Every line is used, skipped as known decoration, or listed as not imported.
- **Strict about meaning.** Each result is proven (and the row says how) or left out with a reason (asking about it is
  Later).
- **Ask only what the report can answer.** What a table or the report's own numbers settle becomes a caption.
- **One button.** Left-out results are named above Save, so a fast tap never saves a guess.

### 2.2 How the starting idea changed

Kept: the prompt, paste, a review before saving, a normal bloodwork entry (share and file are Later). Added: the
clipboard read on return (Later). Changed: v1 asks nothing; what only the report can settle is left out with its reason.

### 2.3 Rejected alternatives

| Alternative | Why not |
|---|---|
| A paste text field | Three extra steps; invites hand edits of machine text. |
| No automatic clipboard read | One more tap on every import, and the owner has to know to press it. (v1 starts here; the read on return is Later.) |
| Reading the clipboard when the Bloodwork sheet or Journal opens | Android would show its paste notice to someone who came to type values. Only the import screen means "import". |
| An "Open with" filter, a text-selection menu item, or intent handling in `MainActivity` | Lists the app for every text file or selection; changes stable's activity. Share and "Open a file" cover files. |
| Save blocked until every question is answered | Forces answers about markers he may not care about; leaving them out is as strict. |
| Asking about name conflicts, sex ranges, time, corrections | Tables or the report's numbers settle them; asking adds questions to most imports. |
| Editing values in the review | A second number editor; the Bloodwork sheet covers it. |
| JSON or YAML | Break on decimal commas, smart quotes and truncation; pipe lines fail one line at a time. |
| New real markers (FT4, HbA1c, ferritin, vitamin D, CRP) | Visible in stable; unlisted results already store and flag them. |
| An "unknown" flag value, or `require` checks on the new fields | A new case in every `when` on a shared enum; one bad stored value would break the journal and backup restore. A missing flag means "no flag". |
| Merging into a same-date entry, or upsert by id | Changes the owner's entries; re-imports would overwrite hand edits. |
| 09:00, or asking for the draw time | 09:00 can cross a day after a zone change; most Dutch reports print no time. |
| "Share prompt" into the chatbot app | Unverified per app; the clipboard works everywhere (Owner check 9). |
| A "near the typical range" check | On a protocol E2, Hct, Hb and T are often far above typical; it would ask every time. |
| A question for a printed range that fits no unit | One real option adds a tap, not certainty; the range is dropped with a caption (§4.3). |
| Keeping choices across process death; clearing the clipboard after import | A few taps are cheap to redo; clearing is surprising, and keyboard histories keep the clip anyway. |

### 2.4 On-device text recognition: not built

We looked at a text recognition model bundled in the APK (ML Kit, Tesseract, PaddleOCR) and decided against it:

- Reading the text is the easy part. A lab report is a table, laid out differently by every lab, sometimes with several
  dates side by side. Turning it into results needs layout rules per lab.
- Recognition mistakes look like real data: "mmol/l" read as "nmol/l", a lost decimal comma, a dropped "<".
- The APK would grow from about 3.6 MB to 10-47 MB. ML Kit brings usage-metrics code that wants network access;
  Tesseract handles tables poorly.
- Without real Dutch reports there was no way to show it works.

A chatbot reads PDFs and photos better than any model the app could ship, and the app checks its answer strictly before
saving. The trade-off: the report passes through the chatbot you choose; remove name, date of birth and BSN first if you
prefer.

**Revisit** when any one of these holds, and build only after a test on real reports with known correct values:
1. The owner provides anonymized real reports (PDF and photo) with the correct values.
2. His labs send text-layer PDFs with a stable layout. Then first try Android's own PDF text plus a per-lab parser: no
   OCR, no APK growth.
3. An on-device model provided by Android works without network access or usage metrics.
4. Google documents that bundled ML Kit sends nothing without network permission, and the owner accepts 6-13 MB for an
   arm64-only dev build.
5. The chatbot path proves unreliable (the owner keeps correcting values).
6. Privacy becomes more important than size and effort.

`ManifestPermissionsTest` (INF-2) fails if `INTERNET` ever enters either flavor.

### 2.5 Known limits

- A unit label off by a small factor (Hb 1.611×, E2 3.67×, free T 3.47×) is caught only when a range is printed.
  Dutch reports print ranges.
- A plausible wrong digit (9,7 read as 9,1) cannot be caught. The review shows every value as printed.
- Two errors on one line (a lost decimal and a wrong unit) may give a wrong repair option. It is still uncertain, so
  v1 leaves it out (Later: a question answered against the report).
- An unlisted test spelled two ways by two labs gets two keys. Exact names only, never fuzzy matching.

### 2.6 Departures from the placement notes

The earlier placement notes had a paste field, an "Open with" filter and Save blocked until every choice was made. All
three are dropped (§2.3).

---

## 3. Import format v1

### 3.1 Shape

```text
protocoltracker-bloodwork-1
lab: <lab name>                                              optional; applies to the draws below it until changed
date: <YYYY-MM-DD>[ <HH:MM>] | <label and date as printed>   one per draw, newest first
<key> | <name as printed> | <value as printed> | <unit as printed> | <range as printed> | <note>
end
```

### 3.2 Lines

- **Key:** a `BloodMarkers` key, `other`, or an unknown word (read as `other`); case, spaces and `-` are forgiven.
- **Name** and **value** are required; unit, range and note may be empty or `-`. `\|` is a literal pipe.
- Blank lines, fences, table borders and headings such as "Hematologie" are skipped. `...` or "(remaining results)"
  raises notice N2. Any other line is listed as "Line N not understood".

### 3.3 Values and numbers

- A value is an optional `<`, `>`, `≤` or `≥` (stored as `<` or `>`) and a number. Copied flags (H, L, *, arrows)
  are stripped; a unit glued to the number moves to the unit cell.
- **No value:** empty, `?`, `null`, or a word such as volgt, onleesbaar or negatief. Not imported; the word goes into
  the entry note. Negative numbers are not imported.
- **Separators** are copied as printed and read only by the parser: `1.209,5` and `8,5` are clear; `1,050` or `1.209`
  follows the block's own decimal mark, else the only reading possible for that marker, else question Q3 (v1: left
  out).

### 3.4 Ranges

- `8,5 - 11,0` or `8,5 tot 11,0` → both limits; `< 45` or `max 45` → high only; `> 1,0` or `vanaf 1,0` → low only.
- One range with a sex prefix (`M <200`, `man: …`): the prefix is dropped, no caption.
- Ranges for men and women: **the men's range**, caption C5. The app has no sex setting, its defaults are typical adult
  male ranges, and the report labels the men's range itself (Owner check 6). A female user would make this a question.
- Age ranges, a bare number, low above high, a negative limit, a women's range alone, or a limit whose separators the
  block cannot settle: no lab range, caption C6. A unit printed after the range that differs from the result's unit is
  left to the meaning checks (§4.3).

### 3.5 Dates

- Part 1 is the draw date `YYYY-MM-DD` with an optional time; `00:00` means no time. Day-first dates are read too. A
  date or time that cannot be read is D1.
- Part 1 must match the date printed in part 2 (read day-first unless it has AM/PM); otherwise question D3.
- A received date is accepted with a caption; a birth, request, result, validation, report or print date is question
  D2. No date is D1; a date more than a day ahead or before 1990 is D4. **In v1 a date question leaves the whole draw
  out** with its reason. Later: it leaves the date empty until the owner picks one, so it blocks Save.
- Two `date:` lines with the same date merge into one draw (a report split into sections), unless both print a time
  and the times differ.

### 3.6 Notes and lab

Notes stay text. The entry note gets one line per row with a note or a no-value word ("HBsAg: negatief"). The lab is
the latest `lab:` line above the draw.

### 3.7 Versioning

Version 1 is read forever, because answers get pasted from old chats; a higher version is refused (M6). A changed field
or line kind bumps the version; new aliases, units or keys never do.

### 3.8 Block detection

Each header starts a block that ends at `end` or the next header; the prompt's own template is ignored, so a whole
copied conversation works. **In a block without `end`, the last result line is never imported** (`0,4` cut from `0,49`
looks complete) and notice N1 shows. A text with no usable block gets the most specific message (M1-M13).

### 3.9 Examples

**Clean** (Dutch, one draw; the full test fixture has 28 rows):

````text
```text
protocoltracker-bloodwork-1
lab: Saltro
date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
free_testosterone | Vrij testosteron (berekend) | 412 | pmol/l | 225 - 725 |
estradiol | Oestradiol | 96 | pmol/l | < 150 |
fsh | FSH | <0,3 | E/l | 1,5 - 12,4 |
hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
hdl | HDL-cholesterol | 1,02 | mmol/l | > 1,0 |
egfr | eGFR (CKD-EPI) | >90 | ml/min/1,73m2 | > 60 |
other | Vrij T4 | 15,2 | pmol/l | 10 - 23 |
end
```
````

Result: 1 draw, 12 Mar 2025 08:15, Saltro, 9 results, no question, no caption. FSH `<0.3` is Low; HDL 1.02 mmol/L is in
range by the lab's `> 1,0`, where the default range would say Low; Vrij T4 is kept as `other:vrij_t4`.

**Messy** (chatter, a bold header, a Markdown table, a copied flag, a grouping dot, two sex ranges, an unreadable
value):

````text
Hier is het blok. Eén waarde was slecht leesbaar.

text
**protocoltracker-bloodwork-1**
date: 2025‑03‑12 | Datum van afname: 12.03.25 00:00
| key | name | value | unit | range | note |
|---|---|---|---|---|---|
| ck | CK (creatine-kinase) | ↑↑↑ 1.209 | U/l | < 190 | |
| hemoglobin | Hb | 9,9 | mmol/l | M: 8,5-11,0 V: 7,5-10,0 | |
| hematocrit | Hematocriet | 0,49 | | 0,41 - 0,51 | |
| psa | PSA totaal | ? | µg/l | < 4,0 | onleesbaar |
end

Let op: bespreek deze uitslagen met uw arts.
````

Result: 1 draw on 12 Mar 2025, saved at 12:00, no question. CK 1209 High ("Read as 1209.": the block uses `,` as its
decimal mark, so `.` groups). Hb with "Men's range used.". Hct read as L/L ("No unit on the report; only L/L fits.").
PSA not imported ("No result: onleesbaar."), and the entry note says "PSA totaal: onleesbaar".

---

## 4. Parser rules and every message

### 4.1 Forgiven (wrapping)

Code fences, chatter around the block, Markdown tables and bullets, bold, citation marks, HTML entities, invisible and
non-breaking characters, every kind of dash, `µ`/`u`, decimal commas, `<0.1`, unit spellings, SI or conventional units,
a missing header (N3) and a cut-off answer (N1).

### 4.2 Strict (meaning)

- No value, a negative value or text instead of a number: not imported.
- The printed name picks the marker (§5.3); an unknown name is kept as an unlisted result (§7).
- A missing or unknown unit that exactly one unit fits is used (caption C1); several → Q1; none → not imported.
- Impossible values, units that contradict the printed range, and lost decimals are questions (§4.3).
- Two values for one marker in one block → Q4; a later block's value wins (C7, the chatbot's correction).
- Every row ends as Ready, Question, Not imported, Left out or Already saved. Nothing uncertain is Ready. In v1 there
  is no Question: such a row is Left out, with the question's text as its reason.

### 4.3 Meaning checks

- **Impossible value** (outside §4.4): repairs that fit (another unit, a decimal shift) are offered as Q1; none → not
  imported. Hct `0,47 %` → "Read as L/L".
- **Range fits another unit:** the printed range is far off the typical range in the printed unit but fits another unit
  → Q1. Catches Hb mmol/L vs g/dL, E2 and free T pmol/L vs pg/mL. For free T only molar vs mass units are compared, so
  a direct-assay range (about a fifth of a calculated one) asks nothing.
- **Lost decimal:** a value over 5× the high limit whose ÷10, ÷100 or ÷1000 lies inside a two-sided range → Q2.
  Glucose `52` with `4,0 - 5,6` asks "Does the report say 5.2?". A training CK of 1209 against `< 190` is never asked.
- **Range consistency:** a range that fits no unit (copied from the wrong row) is dropped (C6); the value is kept. Free
  T is exempt: its range depends on the method and is always kept.

### 4.4 Plausibility limits

Wide limits per marker catch unit, decimal and grouping slips; they never judge health. Each lies beyond extreme real
values, high-dose protocols included (Hb 2-26 g/dL, Hct 5-80 %, total T up to 20,000 ng/dL, CK up to 500,000 U/L).
The manual sheet has no limits.

### 4.5 Every message, with its exact text

These texts are product copy; tests pin them. `<name>` is the printed name, `<Marker>` the app's marker name. Numbers
use a decimal point and the printed digits; units show as printed, or in the app's spelling when the app chose the unit.

**Start step** (one warning line under the buttons). "Auto": also shown by the automatic clipboard read, which stays
silent for unrelated text (Later, like the file messages M9, M10 and M13; v1 shows the others after Paste answer).

| Id | When | Text | Auto |
|---|---|---|---|
| M1 | no text | There is no text to import. In the chatbot, tap Copy on its answer first. | no |
| M2 | no block | No results found in this text. In the chatbot, tap Copy on its answer, then try again. | no |
| M3 | chat share link | This is a link to a chat. The app can't open it. Copy the answer itself instead; a shared chat link is public. | yes |
| M4 | the prompt | This is the prompt. Paste it into a chatbot with your lab report, then copy the chatbot's answer. | yes |
| M5 | JSON, other layout | The chatbot used another layout. Ask it: "Use the protocoltracker-bloodwork-1 layout from my first message." | yes |
| M6 | newer version | This answer uses a newer layout (version <n>). Update the app, or copy the prompt again. | yes |
| M7 | lines joined | The lines of the answer were joined into one. Copy it with the Copy button of the block itself. | yes |
| M8 | over 200,000 characters | This text is too long for a lab report. Copy only the chatbot's answer. | no |
| M9 | a PDF | This is a PDF. The app can't read lab reports itself. Give the PDF to a chatbot together with the AI prompt, then bring back its answer. | – |
| M10 | not text | This file isn't text. Save or share the chatbot's answer as text. | – |
| M11 | the report itself | This looks like the lab report itself. Give it to a chatbot together with the AI prompt, then bring back the chatbot's answer. | yes |
| M12 | no results | The answer has no results. Ask the chatbot to list every result from the report. | yes |
| M13 | file unreadable | The file could not be read. Choose a text file. | – |
| – | prompt copied (Android 12 and lower) | AI prompt copied | – |

**Check step notices:**

| Id | When | Text |
|---|---|---|
| N1 | no `end` | The answer stops early, so results may be missing. Ask the chatbot to write the whole answer again, then copy it. |
| N2 | omission lines | The chatbot left out some results. Ask it to list every result. |
| N3 | no header | The answer has no first line "protocoltracker-bloodwork-1". If you copied only part of it, results may be missing. |

**Draw questions** (v1: the draw is left out whole, with the text as its reason; the buttons and blocking Save until a
date is chosen are Later):

| Id | When | Text | Buttons |
|---|---|---|---|
| D1 | no date | No draw date on the report. | Choose date |
| D2 | not a draw date | This date is from "<label>", not the blood draw. | Choose date |
| D3 | dates disagree | The report says "<printed date>". Which date is it? | <date A>, <date B>, Choose date |
| D4 | future, or before 1990 | <date> is in the future. / <date> is before 1990. | Choose date |

Draw caption: "Received date; the draw can be a day earlier." Draw dialog: "Time not on the report; saved as 12:00."

**Row questions** (unanswered → the result is left out; with several draws each card starts with
"<date> · <Marker>"). In v1 the result is always left out with the text as its reason; the buttons are Later:

| Id | When | Text | Buttons |
|---|---|---|---|
| Q1 | no unit, 2+ fit | <name> <value>: no unit on the report. | Read as <unit> (each), Leave out |
| Q1 | unit not accepted | <name> <value> <unit>: <unit> is not a unit for <Marker>. | Read as <unit> (each), Leave out |
| Q1 | range fits another unit | <name> <value> <unit>: the range <range> fits <B>, not <A>. | Read as <B>, Keep as <A> / Keep <A>, without the range, Leave out |
| Q1 | impossible | <name> <value> <unit> is not possible for <Marker>. | Read as <unit> (each), <value'> <unit>, Leave out |
| Q2 | lost decimal | <name> <value> <unit> is far outside the range <range>. Does the report say <value'>? | <value'> <unit>, <value> <unit>, Leave out |
| Q3 | ambiguous thousands | <name> <printed value>: this can mean <a> or <b>. | <a>, <b>, Leave out |
| Q4 | two values | Two values for <Marker> on <date>. | <v1> <unit>, <v2> <unit>, Leave out |

**Row captions** (one muted line; the first that applies in the order C12, C2, C11, C3, C4, C1, C6, C5, C9, C7, C8,
C10; a chatbot note adds "Note: <note>"):

| Id | Text |
|---|---|
| C1 | No unit on the report; only <unit> fits. (A single-unit marker: "No unit on the report.") |
| C2 | Kept as <name>, not <Marker>. |
| C3 | Read as <Marker>. |
| C4 | Matched by the chatbot. |
| C5 | Men's range used. |
| C6 | Range not used: several ranges. / … it doesn't fit <Marker>. / … not a range. / … low is above high. / … negative limits are not supported. / … women's range. / … unclear numbers. |
| C7 | Changed later in the answer (was <value>). |
| C8 | Saved earlier this day: <value> <unit>. (starts left out; tap: Keep) |
| C9 | Read as <number>. |
| C10 | Your choice. |
| C11 | Kept as <name>: fasting not stated. |
| C12 | Kept as <name>: the numbers don't fit <Marker>. |

**Not imported** (a folded group; name, then the reason):

| When | Text |
|---|---|
| no value | No result: <text>. / No result. |
| text instead of a number | "<text>" is not a number. |
| negative | Negative results are not supported. |
| impossible, no repair | <value> <unit> is not possible for <Marker>. |
| unit not accepted, nothing fits | <unit> is not a unit for <Marker>. |
| no unit, nothing fits | No unit on the report. |
| line not understood | Line <n> not understood: "<original text, at most 80 characters>" |
| cut-off rule | Line <n> may be cut off: "<original text>" |

**Save area** (one line above the button):

| Id | When | Text | Save |
|---|---|---|---|
| S1 | a draw has no date (Later; v1 leaves the draw out) | Choose the draw date to save. / Choose the draw dates to save. | off |
| S2 | open questions (v1: results left out with a reason, without the "N questions open." part) | 1 question open. Hemoglobin is left out. / 2 questions open. Hemoglobin and Creatinine are left out. / 4 questions open. 4 results are left out. | on |
| S3 | all already saved | Everything here is already saved. | off |
| S4 | no numbers | Nothing to save. No result has a number. | off |
| S5 | nothing else to save | Nothing to save. | off |

Button: "Save 1 result", "Save 26 results", "Save 2 draws". Then the snackbar "Bloodwork saved" or
"2 blood draws saved" with Undo (Later: a toast after a share). On a database error: "Couldn't save. Try again." Back after a
choice: "Discard this import?" with **Discard** and **Keep checking**.

---

## 5. Aliases and units

### 5.1 Where the tables live

`MarkerVocabulary` in the import code, not in `BloodMarkers` (the stored model stable renders). A test fails when a
marker has no names, units or limits.

### 5.2 Name normalization

Names match after dropping case, accents, punctuation, footnote marks and method or specimen words (`LC-MS/MS`, serum,
berekend, CKD-EPI, a leading `S-`). Words that change the meaning (vrij, free, nuchter, niet, urine, index, ratio, %,
totaal) are never dropped. **Exact matches only**, never fuzzy. Names with `/`, ratio, quotient, index or verhouding
are never a known marker (except exact variants such as `ASAT/GOT`). The display keeps the printed name.

### 5.3 Name and key precedence

The printed name picks the marker through the tables. The chatbot's key counts only when the tables do not know the
name and the report's numbers confirm it (C4); a key that disagrees with the table loses (C3), and a name with a
`never` word of the key stays unlisted (C2). So the review never asks "which marker is this?". Plain "Glucose" stays
unlisted with a one-tap "Save as Glucose (fasting)" (Later; v1 keeps it unlisted): fasting is a fact only the owner
knows.

### 5.4 The alias table

Matching ignores case, accents, dashes and parentheses (§5.2). **Aliases** are listed in the prompt (§12);
**variants** are matched only, which keeps the prompt short. A name with a `never` word of the key is another test.
The brief's Dutch names (Testosteron totaal, Hematocriet, Hemoglobine, Vrij testosteron, Oestradiol, Kreatinine, ALAT,
ASAT, Gamma-GT, PSA totaal) are aliases, and a test pins each.

| Key | Aliases (prompt) | Variants (matched only) | `never` words |
|---|---|---|---|
| total_testosterone | Testosteron, Testosteron totaal, Totaal testosteron, Total testosterone | Testosterone, Testosterone total, TT | vrij, free, bio, biobeschikbaar, bioavailable, fai, %, dht, dihydrotestosteron, dihydrotestosterone, speeksel, saliva, salivary |
| free_testosterone | Vrij testosteron, Vrij testosteron (berekend), Free testosterone | Testosteron vrij, Berekend vrij testosteron, Calculated free testosterone, Free T | fai, bio, biobeschikbaar, bioavailable, %, procent, percent, t3, t4, ft3, ft4, psa |
| estradiol | Oestradiol, Estradiol, 17-beta-oestradiol, E2 | 17-beta-estradiol, 17β-oestradiol, 17β-estradiol, Estradiol sensitive, Estradiol ultrasensitive | estron, oestron, estrone, estriol, oestriol |
| shbg | SHBG, Sex hormone binding globulin | Sex-hormoonbindend globuline | – |
| lh | LH, Luteïniserend hormoon | Luteinizing hormone, Luteinising hormone | lhrh |
| fsh | FSH, Follikelstimulerend hormoon | Follikel stimulerend hormoon, Follicle stimulating hormone | – |
| prolactin | Prolactine, Prolactin, PRL | – | macro, macroprolactine, macroprolactin, peg, monomeer, monomeric |
| tsh | TSH, Thyreotropine | Thyrotropine, Thyrotropin, Thyreoidstimulerend hormoon, Thyroid stimulating hormone | t3, t4, ft3, ft4, trab, antistoffen, antibodies, receptor |
| hemoglobin | Hemoglobine, Hb, Hemoglobin | Haemoglobine, Haemoglobin, Hgb | a1c, hba1c, mch, mchc, urine, geglyceerd, glycated, glycohemoglobine |
| hematocrit | Hematocriet, Ht, Hematocrit | Haematocrit, Hct, PCV | – |
| cholesterol | Cholesterol, Cholesterol totaal, Totaal cholesterol, Total cholesterol | Chol | hdl, ldl, vldl, non, niet |
| hdl | HDL-cholesterol, Cholesterol HDL, HDL | HDL cholesterol, HDL-C | non, niet |
| ldl | LDL-cholesterol, Cholesterol LDL, LDL | LDL cholesterol, LDL-C | – |
| non_hdl | Non-HDL-cholesterol, Niet-HDL-cholesterol | Non-HDL cholesterol, Non HDL, Non-HDL-C | – |
| triglycerides | Triglyceriden, Triglycerides, TG | Triglyceride | – |
| glucose | Glucose nuchter, Nuchtere glucose, Fasting glucose | Nuchter glucose, Glucose fasting; plain Glucose only by the owner's tap (§5.3) | niet, non, random, uur, 2h, ogtt, belasting, urine, a1c, hba1c |
| creatinine | Kreatinine, Creatinine | Kreat, Crea | urine, klaring, clearance, kreatinineklaring, creatinineklaring, kinase, albumine, albumin |
| egfr | eGFR, eGFR (CKD-EPI), Geschatte GFR | eGFR MDRD, Estimated GFR, Glomerulaire filtratiesnelheid | klaring, clearance, kreatinineklaring, creatinineklaring, cockcroft |
| albumin | Albumine, Albumin | Alb | urine, micro, microalbumine, globuline, prealbumine, kreatinine, creatinine |
| ast | ASAT, ASAT (GOT), AST | GOT, SGOT, AST (GOT), ASAT/GOT, Aspartaataminotransferase, Aspartaat aminotransferase, Aspartate aminotransferase | – |
| alt | ALAT, ALAT (GPT), ALT | GPT, SGPT, ALT (GPT), ALAT/GPT, Alanineaminotransferase, Alanine aminotransferase | – |
| ggt | Gamma-GT, GGT, γ-GT | Gamma GT, G-GT, Gamma-glutamyltransferase, Gamma-glutamyltranspeptidase | – |
| ck | CK, Creatinekinase, CK totaal | Creatine kinase, CPK | mb, ckmb |
| psa | PSA, PSA totaal | Totaal PSA, Total PSA, PSA total, Prostaatspecifiek antigeen, Prostate specific antigen | vrij, free, % |

The traps, settled (printed name, the chatbot's key → result):

| Printed | Key | Result |
|---|---|---|
| Vrij testosteron | total_testosterone | free testosterone, C3 (the table wins, if the numbers are clean) |
| Vrij testosteron index | free_testosterone | unlisted, C2 (`index`) |
| Vrij T4 | free_testosterone | `other:vrij_t4`, C2 (`t4`) |
| FT4 | tsh | unlisted, C2 (`ft4`) |
| HbA1c | hemoglobin | unlisted, C2 |
| Hemoglobine 9,9 mmol/l | hemoglobin | hemoglobin ×1.611 (the name picks the marker, the unit the factor) |
| Glucose (niet nuchter) | glucose | unlisted, C2 (`niet`) |
| Glucose | glucose | `other:glucose`, C11, option "Save as Glucose (fasting)" |
| Kreatinine urine | creatinine | unlisted, C2 (`urine`) |
| Kreatinine `12 \| mmol/l \| 3 - 20` | other | `other:kreatinine`, C12 (the numbers don't fit creatinine) |
| Cholesterol/HDL | hdl | unlisted, C2 (the `/`) |
| CK-MB | ck | unlisted, C2 (`mb`) |
| Vrije testosteronfractie `0,45 \| nmol/l` | free_testosterone | free testosterone, C4 (an unknown name the numbers confirm) |
| Testosteron (ochtend), Testosteron (LC-MS/MS) | total_testosterone | total testosterone, no caption |

### 5.5 Unit normalization

Units compare after case and spaces, `µ`/`mc`/`micro` → `u`, `²` or `^2` → `2`, `1,73` → `1.73`, liter → `l`,
UCUM brackets, cell counts written as `10^9/l` or `10^12/l`, Dutch *eenheden* (`E/l` → U/L, `mE/l` → mU/L, `IE/l` →
IU/L, `mIE/l`, `mIE/ml` and `µIE/ml` → the IU forms), `IU` = `U` for these markers, the eGFR spellings, and a trailing `/I` read as `/l`. Other slips (`1/1` for
`l/l`, `pmol` for `µmol`) are not repaired: the unit is not accepted, and Q1 asks when another unit fits (v1: left out). The display
keeps the printed unit.

### 5.6 Accepted units and factors

`stored = printed × factor`; the lab range uses the same factor. The prompt lists these spellings.

| Key | Stored | Accepted × factor |
|---|---|---|
| total_testosterone | ng/dL | nmol/L ×28.84 · ng/dL ×1 · ng/mL, µg/L ×100 |
| free_testosterone | pg/mL | pmol/L ×0.2884 · nmol/L ×288.4 · pg/mL, ng/L ×1 · ng/dL ×10 (never `%`: percent free is another quantity) |
| estradiol | pg/mL | pmol/L ×0.2724 · nmol/L ×272.4 · pg/mL, ng/L ×1 |
| shbg | nmol/L | nmol/L ×1 |
| lh, fsh | IU/L | U/L, IU/L, E/L, IE/L, mIU/mL, mU/mL ×1 |
| prolactin | ng/mL | mU/L, mIU/L, mE/L, µIU/mL ×0.0472 · U/L, IU/L, E/L ×47.2 · ng/mL, µg/L ×1 |
| tsh | mIU/L | mU/L, mIU/L, mE/L, µIU/mL, µU/mL ×1 |
| hemoglobin | g/dL | mmol/L ×1.611 · g/dL ×1 · g/L ×0.1 |
| hematocrit | % | L/L ×100 · % ×1 |
| cholesterol, hdl, ldl, non_hdl | mg/dL | mmol/L ×38.67 · mg/dL ×1 (never `ratio`) |
| triglycerides | mg/dL | mmol/L ×88.57 · mg/dL ×1 |
| glucose | mg/dL | mmol/L ×18.0 · mg/dL ×1 |
| creatinine | mg/dL | µmol/L ×0.011312 · mg/dL ×1 |
| egfr | mL/min/1.73m² | mL/min/1.73m² ×1 · mL/min ×1 (only under an eGFR name) |
| albumin | g/dL | g/L ×0.1 · g/dL ×1 |
| ast, alt, ggt, ck | U/L | U/L, IU/L, E/L ×1 · µkat/L ×60 |
| psa | µg/L | µg/L, ng/mL ×1 |

Tests check that every stored unit has factor 1 and every SI unit uses the app's own conversion, so an imported value
stores the same number as a typed one.

### 5.7 The Dutch units found in the research

All covered: E2 and free T in nmol/L, prolactin in U/L or mE/l, TSH in mE/l, Hb in mmol/L, Hct in l/l, LH, FSH and
enzymes in E/l or IE/l, eGFR in ml/min/1,73m².

---

## 6. Lab reference ranges and the flag rule

### 6.1 Model

`MarkerResult` gains five optional fields: `qualifier` (`<` or `>`), `refLow` and `refHigh` (the lab's limits in the
stored unit), and `name` and `unit` (as printed, for unlisted results). They get no new `require` check: a bad stored
value must never break the journal or a backup restore, so readers validate instead.

### 6.2 Storage, backup and stable compatibility

Results live in the journal's JSON column: no Room migration, and the backup stays `protocoltracker-backup-2`. Empty
fields are not written, so every result stable can create is stored byte for byte as before. A dev backup restores in
current stable with the fields kept; the released 0.4.0 restores it without them.

### 6.3 What a null side means

When a result has a lab range, it is used alone and a null side means no limit (`< 45` has no low limit). Otherwise the
result has no range and no flag: the app never supplies a range of its own (review 2026-10, F1). An importer that knows only one side the lab applied writes the other side too: the
web history import fills it with the web default. One rule, no per-side mixing with defaults.

### 6.4 The flag rule

Limits count as in range. An exact value is Low below the low limit, High above the high limit, else In lab range. A
censored value is flagged only when its true value cannot lie on both sides of a limit; otherwise it has no flag:

| Value | Low | In lab range | High |
|---|---|---|---|
| `<x` | x ≤ low | low limit missing or 0, and x ≤ high (if any) | never |
| `>x` | never | no high limit, and x ≥ low (if any) | x ≥ high |

A result with no range at all, or with an unknown qualifier, has no flag. Old results flag exactly as today.
Examples: FSH `<0.3` vs 1.5–12.4 → Low; E2 `<40` vs 20–150 → no flag; eGFR `>90` vs `> 60` → In lab range.

### 6.5 Where flags and ranges show

The Journal line says "N outside lab range", or "all in lab range" only when every result is in its lab range.
Reports mark lab ranges "(lab)" and show censored and unlisted results as printed; lines stay identical for data
without the new fields. Dev screens use the lab range (marked "(lab)" on the Journal card, like the reports) and show
censored values with their sign, with no flag text when the flag is unclear; censored results are not plotted on Levels.
One-result draws read "1 result" in dev; stable keeps "1 results".

---

## 7. Unlisted results

A Dutch panel has 5-10 tests the app does not list (FT4, HbA1c, ferritin, vitamin D, CRP). They are saved: key `other:`
plus a slug of the printed name (`Vrij T4` → `other:vrij_t4`; `%` is written `pct`, so `Lymfocyten %` and
`Lymfocyten` stay apart), with the value, qualifier, name, unit and range as
printed, never converted. The `:` never occurs in a marker key, so they can never collide. Within one draw, a later
block's value in the same unit replaces an earlier one (C7) and two values in the same unit in the latest block are both
left out (Q4), as for listed markers; other results with the same slug (from one block, or in another unit) become
`…_2`, `…_3`. They are flagged only by a lab range, shown by their printed name in the
review, reports and the dev sheet, and listed over time in the dev marker sheet (TR-4). The web history import keeps
its other keys the same way, without a unit, and never maps them to a known marker. A future real marker never
reinterprets them, because their unit is not proven.

---

## 8. The import draft domain model

Pure Kotlin in `core/domain`, JVM tested: `read` turns text into a draft or a refusal, `review` applies the owner's
choices and the saved entries, and `entries` builds the journal entries. The ViewModel only holds state.

---

## 9. Saving, default draw time and duplicates

### 9.1 One entry per draw, one write

One `JournalEntry.Bloodwork` per draw, all saved in one transaction; Undo deletes exactly those. An import never
changes, merges into or deletes an existing entry.

### 9.2 Draw time when none is printed

12:00 local, shared with the web history import. It keeps the calendar day across time zones, matches the web app, and
asks nothing. v1: the time can be corrected afterwards in the Bloodwork sheet (the draw dialog is Later).

### 9.3 Already saved

A result is already saved when an entry on the same local date holds a result with the same marker and qualifier (for
an unlisted result also the same unit) and a value within 0.5 % of the larger, at least 0.01, in the stored unit. This
covers SI and conventional round trips and the web export's two decimals. Matching by date, not time or id, also finds
a draw from the web import; in the other direction, the web import skips a web draw on any date that already has a
draw made in the app.

### 9.4 Same day, same marker, another value

The row starts left out with caption C8, which shows the earlier value; one tap on **Keep** includes it. A cumulative
report or a small hand-typed difference never adds a second value unnoticed. C8 replaces the row's other caption, because
it says why the row starts left out.

### 9.5 Re-imports and existing entries

The same answer twice: all already saved (S3). A cumulative report: only the new draw saves. Same date with some
markers new: the new results save as a new entry on that date. A result edited by hand later shows as a same-day row,
left out.

---

## 10. Review screen and the in-app help text

### 10.1 Start step

Top bar "Import results". The help text, **Copy AI prompt** (outlined), a message line when there is one, **Paste
answer** (primary), **Open a file** (text button; Later). No help page.

**The in-app help text** (final wording):

> 1. Copy the AI prompt.
> 2. In any chatbot, paste it and add your lab report (PDF, photo or text).
> 3. Tap Copy on the chatbot's answer, not Share.
> 4. Come back here and tap Paste answer.
>
> Your report goes to the chatbot you use. This app stays offline.

"Not Share", because a chatbot's Share button publishes a public link. Step 4 is the v1 wording; with the clipboard
read on return (Later) it becomes "Come back here. The copied answer opens for checking."

### 10.2 How the answer comes back

**v1: only Paste answer, which reads the clipboard on the tap.** The rest of this section is Later.

Only the Start step reads the clipboard, each time the window regains focus, and only a new text clip that is not
marked sensitive and not the prompt itself. Android shows its own paste notice. **Paste answer** reads on the tap;
**Open a file** reads a text file; in dev, text or a file can be shared to "Import bloodwork".

### 10.3 Check step

Open questions of every draw sit on top under "To check" (Later; in v1 each left-out row shows its reason in place).
Below, one card per draw, newest first: marker name, printed name when it differs, value and unit as printed, the flag
as a word, one caption. Left-out rows stay in place, struck through. "N already saved" and "N not imported" fold open in place. One line above Save says what is left out.

### 10.4 Row detail

Tap a row: the line as printed, the range used, and **Leave out** or **Keep**, an alternative reading, or **Change
answer**. v1: **Leave out** or **Keep** only (a row left out as uncertain cannot be kept); alternative readings and
**Change answer** are Later.

### 10.5 Draw dialog

Later. Tap a draw header or "Choose date": date, time ("Not on the report" when empty), lab, "Leave out this draw".
In v1 a draw is not edited in the review; its date and time can be changed afterwards in the Bloodwork sheet.

### 10.6 After saving, back and process death

Save opens Journal with the Bloodwork chip selected and Undo; after a share (Later), a toast returns to the sender.
Nothing is saved before Save. After process death v1 starts again at Start (paste again); later the automatic read
reopens the answer.

### 10.7 The Bloodwork sheet keeps the new fields

Editing a draw by hand keeps untouched results exactly, in both flavors (a data-safety fix). Only fields the user typed a
different value into are rebuilt (`BloodworkRules.editResults`): a typed number keeps the lab range and drops the
qualifier, a cleared field removes the result. The unit toggle never counts as typing; untouched fields show the saved
value in the other units, and typing the saved value back makes a field untouched again. In dev the sheet shows lab
ranges, "Reported as <0.3 IU/L. A typed number replaces it." and an "Other tests" section.

In dev each caption describes the result as it will be saved: "Lab range 248–836 ng/dL" (gone once the field is
cleared; a field without a lab range has no range caption), and under a censored value "Reported as <0.3 IU/L. A
typed number replaces it.", whose number is the field's own text so the two always match; typing a number removes that
line. "Other tests" comes last, one field per unlisted result: label and unit as printed, the number as printed (never
converted by the unit switch), its lab range and "Reported as" line the same way.

---

## 11. Dev gating and stable compatibility

The import row, the route and the Today and Journal hooks exist only in dev; the share activity (Later) lives in the
`dev` source set. Shared changes (the result fields, the flag rule, report lines, the sheet keeping untouched results) change
nothing visible for data stable can create; pinned tests and the stable screenshot guard hold that.
`DevEntryPointsTest` checks every entry point in both flavors, and `ManifestPermissionsTest` checks that neither has
`INTERNET`.

---

## 12. The AI prompt

The app builds the prompt (`LabPrompt.text` in `core/domain`, `io/labimport/LabPrompt.kt`) from `BloodMarkers` and
the import tables: fixed rules, one key line per marker (key, name, accepted units, aliases, notes) and the `other:` line
last. The block below is the generated text; `LabPromptTest` keeps it equal to the app's prompt. English only; chatbots
read Dutch reports fine with it.

````text
Bloodwork import prompt (format protocoltracker-bloodwork-1). Give the app the chatbot's answer, not this prompt.

Turn the attached lab report (PDF, photo or text) into one data block for a tracking app. Read every page. Use only the report.

Reply with one code block and nothing before or after it, in exactly this layout. Parts in <angle brackets> are placeholders.

```text
protocoltracker-bloodwork-1
lab: <lab name; leave this line out if none is printed>
date: <draw date YYYY-MM-DD> <draw time HH:MM, only if printed> | <date label and date exactly as printed>
<key> | <test name as printed> | <result as printed> | <unit as printed> | <reference range as printed> | <note>
end
```

Rules
1. One line per result, for every result on the report, also tests that are not in the key list. Never shorten the list or write "..." or "etc.".
2. Copy the result, unit and reference range character for character, with every decimal comma or point, thousands separator and < or >. Never round, convert, calculate, estimate or invent a value (such as free testosterone, LDL, eGFR or a ratio that is not printed). Leave out flags such as H, L, * and arrows.
3. If a result is unreadable, missing or a word (such as "volgt" or "negatief"), write ? as the result, which means no value, and put the reason or the word in the note.
4. If the report prints several reference ranges (for example for men and women, or by age), copy all of them with their labels. If it prints none, leave the range empty. Never add one yourself.
5. Key: pick it from the list below. Use other when the test is not in the list or you are not sure. A similar name is not enough: follow every note.
6. Date: the day the blood was drawn (afname, afnamedatum, datum afname, datum van afname, collected). Never the date of birth, or the received, report or print date. If no draw date is printed, write date: ? and put the dates you see after the |.
7. Several draw dates (for example earlier results in extra columns): one date line per draw, newest first, each followed by its own results.
8. Leave out names, dates of birth, patient numbers and addresses. No advice, comments or questions: put doubts in the note of that line.
9. End the block with the line end. Before you answer, check every line against the report once more.

Keys (key: name; units; also printed as; notes)
total_testosterone: Total testosterone; nmol/L, ng/dL, ng/mL, µg/L; Testosteron, Testosteron totaal, Totaal testosteron, Total testosterone; not free, bioavailable or salivary testosterone, not DHT
free_testosterone: Free testosterone; pmol/L, nmol/L, pg/mL, ng/L, ng/dL; Vrij testosteron, Vrij testosteron (berekend), Free testosterone; not the free androgen index (FAI, vrij testosteron index), not bioavailable or % free testosterone, not free T4 or T3
estradiol: Estradiol (E2); pmol/L, nmol/L, pg/mL, ng/L; Oestradiol, Estradiol, 17-beta-oestradiol, E2; not estrone or estriol
shbg: SHBG; nmol/L; SHBG, Sex hormone binding globulin
lh: LH; U/L, IU/L, E/L, IE/L, mIU/mL, mU/mL; LH, Luteïniserend hormoon
fsh: FSH; U/L, IU/L, E/L, IE/L, mIU/mL, mU/mL; FSH, Follikelstimulerend hormoon
prolactin: Prolactin; mU/L, mIU/L, mE/L, µIU/mL, U/L, IU/L, E/L, ng/mL, µg/L; Prolactine, Prolactin, PRL; not macroprolactin or prolactin after PEG
tsh: TSH; mU/L, mIU/L, mE/L, µIU/mL, µU/mL; TSH, Thyreotropine; not free T4 (vrij T4, FT4) or T3
hemoglobin: Hemoglobin; mmol/L, g/dL, g/L; Hemoglobine, Hb, Hemoglobin; not HbA1c, MCH or MCHC
hematocrit: Hematocrit; L/L, %; Hematocriet, Ht, Hematocrit
cholesterol: Total cholesterol; mmol/L, mg/dL; Cholesterol, Cholesterol totaal, Totaal cholesterol, Total cholesterol; not HDL, LDL or a ratio
hdl: HDL cholesterol; mmol/L, mg/dL; HDL-cholesterol, Cholesterol HDL, HDL; not a ratio
ldl: LDL cholesterol; mmol/L, mg/dL; LDL-cholesterol, Cholesterol LDL, LDL; not a ratio
non_hdl: Non-HDL cholesterol; mmol/L, mg/dL; Non-HDL-cholesterol, Niet-HDL-cholesterol
triglycerides: Triglycerides; mmol/L, mg/dL; Triglyceriden, Triglycerides, TG
glucose: Glucose (fasting); mmol/L, mg/dL; Glucose nuchter, Nuchtere glucose, Fasting glucose; only when the report says fasting (nuchter); plain Glucose is other
creatinine: Creatinine; µmol/L, mg/dL; Kreatinine, Creatinine; not urine creatinine or creatinine clearance
egfr: eGFR; mL/min/1.73m², mL/min; eGFR, eGFR (CKD-EPI), Geschatte GFR; not creatinine clearance
albumin: Albumin; g/L, g/dL; Albumine, Albumin; not urine albumin or the albumin/creatinine ratio
ast: AST (GOT); U/L, IU/L, E/L, µkat/L; ASAT, ASAT (GOT), AST
alt: ALT (GPT); U/L, IU/L, E/L, µkat/L; ALAT, ALAT (GPT), ALT
ggt: Gamma-GT; U/L, IU/L, E/L, µkat/L; Gamma-GT, GGT, γ-GT
ck: CK (creatine kinase); U/L, IU/L, E/L, µkat/L; CK, Creatinekinase, CK totaal; not CK-MB
psa: PSA; µg/L, ng/mL; PSA, PSA totaal; not free PSA or a PSA ratio
other: any other test; copy its name, result, unit and range as printed
````

The brief's four instructions: only the block (the "Reply with" line); copy numbers exactly, never convert, round or
invent (rules 2 and 4); `other` for unlisted markers (rule 5); unreadable values as `?` with a note (rule 3). The
prompt holds no example numbers, because models copy them when a photo is unreadable. Rule 2 keeps thousands
separators, because dropping them would make the chatbot interpret `1,050`.

Tip: save the prompt in a chatbot project to skip step 2. Old prompts keep working.

`LabPromptTest` fails when a marker lacks its key line, units or aliases, when the text grows past 6,500 characters or
holds an example number, or when the first four-backtick fence after this heading differs from the app's prompt. Keep
no other four-backtick fence between this heading and the prompt.

---

## 13. Tests

### 13.1 Fixtures

v1 uses a modest set, about 15 (BWI-3 lists them); the design had about 50. Synthetic chatbot answers and report texts
as JVM tests, Dutch and English: clean, messy (Markdown, chatter,
invisible characters) and broken (cut off, JSON, share links, unit slips, lost decimals, date and name traps). Values
and messages are compared exactly. No real report data enters the repo.

### 13.2 Unit and property tests (JVM)

Text, values, ranges and dates, the vocabulary (including the brief's Dutch names), meaning checks, the flag rule,
stable JSON compatibility, reports, the review and the prompt. A corpus test saves every fixture, with rows left out
and kept, and checks that the entries survive a backup round trip (answering questions is Later).

### 13.3 Robolectric

The import screen, the path to Journal with Undo, the sheet in both flavors and the dev entry points (the share
activity is Later).

### 13.4 Screenshots

The dev import screens and sheet, light and dark.

---

## 14. Owner checks

Each has a default that works without the owner.

| # | Check | Default |
|---|---|---|
| 1 | Run 2-3 real Dutch reports (a PDF and a phone photo) through the chatbot and the import. How many results are left out, and which captions appear? | Expected none. A recurring C4 or C12 means a missing alias; anonymized answers become fixtures. |
| 2 | Which copy buttons the chatbot apps offer | Block and whole-answer copies both work. |
| 3 | Is Android's paste notice on return acceptable, and does the automatic read fire? (Later) | Yes; "Paste answer" always works. |
| 4 | Does "Import bloodwork" appear in the file manager's share sheet? (Later) | "Open a file" covers it. |
| 5 | 12:00 for draws without a printed time | Accepted; editable in the Bloodwork sheet. |
| 6 | The men's range when a report prints both | Men's range with a caption. |
| 7 | After a share import: back to the chatbot, or into Journal? (Later) | Back to the chatbot. |
| 8 | A same-day result with another value starts left out | One tap keeps it. |
| 9 | Would sharing the prompt into the chatbot app work? | Copy stays. |
| 10 | Saving the prompt in a chatbot project | Documented tip (§12). |
| 11 | Anonymized real reports with correct values, for an OCR test | OCR not built (§2.4). |

---

## 15. Implementation plan

The build order is in [BACKLOG.md](BACKLOG.md): BW-2 to BW-7 (done, after the guard items INF-1 and INF-2), BW-13 (the
bulk save shared with the history import), then BWI-1 to BWI-5, domain first, one small commit per item. Nothing
visible changes in stable; the feature becomes reachable in dev with BWI-5. BW-16, BW-17, BW-19 and opening a file are
Later.
