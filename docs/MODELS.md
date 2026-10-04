# Level model

Level curves use a public Google Sheet of compiled pharmacokinetic parameters (the "PK sheet") and the per-dose model
below. The sheet supplies every parameter it has; the numbers trace back to the studies cited in the sheet and in each
preset's source note. Every curve is an estimate and is labelled as such in the app.

## Sources
- **PK sheet:** a public Google Sheet (a single tab),
  https://docs.google.com/spreadsheets/d/1Hrbw5eXb8bw1YfZZmBHHS3IY_j95V5DJsc6-piO__Ns, CSV export of 2026-10-04.
  Copied into `pk/PkSheet.kt` unchanged; the export's numeric columns are kept as the test resource
  `core/domain/src/test/resources/pk-sheet-2026-10-04.csv`, and `PkSheetTest` checks every row
  against it and that every sheet row is either imported or listed below as not imported.
- **Model:** the formula, the Basic model and the per-compound multipliers, which the sheet does not state, are
  described below and checked against reference values (`PkSheetGoldenTest`).

## Per-dose curve (`pk/PkSheetModel.kt`, `pk/CurveEngine.kt`)
There are two models, chosen by the sheet's "Model" column. For a dose `D` (in the sheet's dose unit) at time
0, with half-life `t½`, multiplier `M` and the sheet's `Cmax`, `Tmax` and bioavailability `F`:

**Advanced** (the sheet has a study Cmax and Tmax). F is not used.

```
P = M · Cmax · D
c(t) = P · t / Tmax                     0 ≤ t ≤ Tmax
c(t) = P · 2^(−(t − Tmax) / t½)          t > Tmax
```

**Basic** (half-life and F only). First-order absorption with a half-time of `t½ / 8`, so `Tmax = 3 · t½/8` and the
curve reaches `1 − 2^−3 = 87.5 %` of its asymptote at Tmax:

```
c(t) = 14 · M · F · D · (1 − 2^(−t / (t½/8)))     0 ≤ t ≤ 3 t½/8
c(t) = 14 · M · F · D · 0.875 · 2^(−(t − Tmax) / t½)   t > Tmax
```

In the app the Basic curve is stored like the Advanced one: `P = 14 · M · F · 0.875` per unit dosed, Tmax = 3/8 t½, and
`PkParams.rise = FIRST_ORDER` (`c(t) = P · (1 − 2^(−3t/Tmax)) / 0.875` before the peak). `rise` is absent in data stored
before presets-2026-10b and defaults to LINEAR. The curve editor has no field for it; an edited preset keeps its own.

Doses add up (linear superposition). The engine groups doses by (Tmax, t½, rise); within a group
peaks occur in dose order, so the decaying part is one running sum advanced by `e^(−k·Δt)` and only doses still rising
are summed individually. Doses older than `Tmax + 10·t½` are dropped (< 0.1 % contribution).

**Multipliers.** The sheet only says whether a multiplier is used ("Multiplier Used"); the values are the model's:

| Compound | M | Sheet says |
|---|---|---|
| Test E | 0.33 | No |
| Test C, Test Iso | 0.6 | No |
| Boldenone Cyp (Test C clone) | 0.6 | (empty row) |
| Test Susp | 6 | Yes |
| Andriol (oral TU) | 0.7583841 | Yes |
| DHB | 0.7 | Yes |
| Primobolan Depot | 0.5 | Yes |
| Dianabol | 10 | Yes |
| Turinabol | 7 | Yes |
| Winstrol (oral) | 5 | Yes |
| Winstrol Depot | 1.3 | Yes |
| Anavar | 0.3 | Yes |
| Superdrol | 20 | Yes |
| Semaglutide (oral) | 1.5 | Yes |
| everything else | 1 | No |

Multipliers for compounds without a preset: Nebido 53-day form 0.95, E2 valerate 0.45, oral progesterone 0.06,
Ostarine 15, Cardarine 11, S-23 55.

**Sampling.** The reference values sample whole hours and round each dose and each sum to 0.001, so a sampled peak
can sit up to one hour off the true peak (Test E 250 mg: sampled 930.4 at 34 h, true 933.0 at 33.3 h). The app
evaluates the same function continuously; at whole hours the two agree to rounding (`PkSheetGoldenTest`).

**Linear regression** ("Advanced + Linear Regression" for Test E and Test C in the sheet) is not used: peaks scale
linearly with dose.

## Notes on sheet values
- **Anavar:** Cmax 772 ng/dL per mg, as in the sheet, so 772 × 0.3 = 231.6 ng/dL per mg. The cited study (PMC7134583,
  6 adult men, LC-MS/MS) reports a mean Cmax of 67.6 ng/mL (CV 101 %) after 0.1 mg/kg, given as 7.5–10 mg (2.5 mg
  tablets, weights 70–101 kg, median 84.5 kg). 6,760 ng/dL ÷ 8.75 mg (the middle of the range) = 772; dividing by 10 mg
  gives 676, which assumes every man took the top dose. At the median weight the dose was about 8.5 mg, so 772 is the
  closer value. An older radioimmunoassay figure (417 ng/mL after 10 mg, PMID 4729902) is not used: immunoassays of
  that era overread steroid levels.
- **Sheet row "Testosterone | Trestolone"** (t½ 53 d, Cmax 0.995049, Tmax 10.5 d) is a 53-day Nebido form (M 0.95),
  mislabelled. Not imported: the castor-oil row (t½ 33.9 d) is the Nebido preset.
- **Boldenone cypionate:** the sheet row is empty ("Cloned settings from Testosterone Cypionate"); the preset uses
  Test C's settings, M 0.6 included.
- **Test E, Test C and Test Iso** have a multiplier although the sheet says "No".

## Peak `P` and F in the app
- `PkParams.peakPerUnit` is P per base unit of the preset. The sheet's Cmax is ng/dL per mg dosed (ester mass as
  injected) except: T3 and clenbuterol per mcg (× 1000 for per mg), hCG per mg with 100 IU ≈ 0.01 mg (× 1e-4 for per
  IU), HGH per mg with 1 mg ≈ 3 IU (÷ 3 for per IU).
- `activeFraction` (F) is used only for relative curves (`P = dose × F`, "mg active (relative)"). It is the sheet's F,
  except the ester-fraction corrections of review 2026-10 (footnote ¹). The Basic peak always uses the sheet's F.
- Every sheet compound now has an absolute curve. Compounds outside the sheet without a study peak stay relative
  (enclomiphene, cagrilintide, ipamorelin, CJC-1295 DAC). If a level group mixes compounds with and without a peak (or
  different display units), the whole group is shown relative.
- **Missing Tmax** outside the sheet (ipamorelin, CJC-1295 DAC): `Tmax = clamp(0.2 · t½, 1 h, 2 d)` for injections.

Display units: steroids ng/dL; other drugs and peptides ng/mL; cabergoline, clenbuterol and tesamorelin pg/mL.

## Metrics (`Levels.kt`)
- **Steady range/average:** current plan items simulated until transients decay (≥ 28 days and ≥ Tmax + 10 t½), then measured
  over one full schedule period (LCM of item periods).
- **90 % of steady:** `Tmax + t½ · log2(10)` for the slowest compound in use.
- **Below 10 %:** first time after the final dose that the level drops under 10 % of its post-dose peak; only when the plan ends.

## Which groups are shown (`Levels.groups`)
A group is *in use* when a plan item active today takes it, or a taken dose is still above ~1 % of its peak
(`takenAt + Tmax + 6.64 t½ ≥ now`, since 2^-6.64 ≈ 1 %). Skipped doses never count. Other groups (other phases, paused
items, old doses) are listed under "Not in use now". Order follows the plan sections; the colour is that of the compound in use.

## Presets (`Presets.kt`, `VERSION = presets-2026-10b`)
Sheet values unchanged: t½ and Tmax in days (stored in hours), Cmax in the sheet's unit, F. "Model" A = Advanced, B =
Basic (Tmax = 3/8 t½, P = 14 · M · F · 0.875). P is per mg unless noted. "—" = log only, no curve.

| Section | Preset | Model | t½ (d) | Tmax (d) | Cmax | M | P | F | Source |
|---|---|---|---|---|---|---|---|---|---|
| Injectable | Test E (testosterone enanthate) | A | 7.19 | 1.3875 | 11.3095 | 0.33 | 3.732 | 0.72 | Sheet; PMC4721027, ajpendo.00502.2001 |
| Injectable | Test C (testosterone cypionate) | A | 6.9 | 4.5 | 5.56 | 0.6 | 3.336 | 0.70 | Sheet; tau.amegroups 11328, psp4.12287 |
| Injectable | Test P (testosterone propionate) | A | 1.0375 | 1.0625 | 26 | 1 | 26 | 0.84 | Sheet; JCEM 63(6):1361 |
| Injectable | Nebido (testosterone undecanoate, castor oil) | A | 33.9 | 10 | 1.187 | 1 | 1.187 | 0.63 ¹ | Sheet; jandrol.109.009597, Nebido PI |
| Injectable | Test U MCT (testosterone undecanoate, MCT oil) **new** | A | 20 | 8.5 | 1.85 | 1 | 1.85 | 0.63 ¹ | Sheet: estimated values, no study |
| Injectable | Test PP (testosterone phenylpropionate) | B | 2.5 | 0.9375 | — | 1 | 8.453 | 0.69 | Sheet; PMC9611952 |
| Injectable | Test Iso (testosterone isocaproate) | B | 3.1 | 1.1625 | — | 0.6 | 5.513 | 0.75 | Sheet; PMC9611952 |
| Injectable | Test Dec (testosterone decanoate) | B | 5.6 | 2.1 | — | 1 | 7.963 | 0.65 | Sheet |
| Injectable | Test Susp (testosterone suspension) | A | 1.375 | 0.25 | 5.067 | 6 | 30.40 | 1.0 | Sheet (horse study, scaled) |
| Injectable | Deca (nandrolone decanoate) | A | 10.2 | 1.833 | 3.99 | 1 | 3.99 | 0.64 ¹ | Sheet; JCEM 90(5):2624 |
| Injectable | NPP (nandrolone phenylpropionate) | A | 2.4 | 1 | 8.91465 | 1 | 8.915 | 0.67 | Sheet; PMID 9103484 |
| Injectable | Tren A (trenbolone acetate) | B | 1.5 | 0.5625 | — | 1 | 10.66 | 0.87 | Sheet; S002228602030452X |
| Injectable | Tren E (trenbolone enanthate) | B | 11 | 4.125 | — | 1 | 8.698 | 0.71 | Sheet; S002228602030452X |
| Injectable | Parabolan (trenbolone hexahydrobenzylcarbonate) | B | 8 | 3 | — | 1 | 8.085 | 0.66 | Sheet; S002228602030452X |
| Injectable | Masteron P (drostanolone propionate) | B | 2 | 0.75 | — | 1 | 10.29 | 0.84 | Sheet |
| Injectable | Masteron E (drostanolone enanthate) | B | 4.5 | 1.6875 | — | 1 | 8.943 | 0.73 | Sheet |
| Injectable | Primobolan Depot (methenolone enanthate) | B | 10.5 | 3.9375 | — | 0.5 | 4.471 | 0.73 | Sheet |
| Injectable | EQ (boldenone undecylenate) | A | 5.125 | 6 | 1.098 | 1 | 1.098 | 0.63 | Sheet; PMID 17348894 |
| Injectable | Boldenone Cyp | A | 6.9 | 4.5 | 5.56 | 0.6 | 3.336 | 0.70 | Sheet: cloned from Test C |
| Injectable | DHB (1-testosterone cypionate) | B | 9 | 3.375 | — | 0.7 | 8.575 | 0.70 ¹ | Sheet (F 1.0 in the peak) |
| Injectable | Winstrol Depot (stanozolol, injectable) | A | 3.42 | 7 | 8.12 | 1.3 | 10.556 | 1.0 | Sheet; PMID 17348894 |
| Injectable | MENT (trestolone acetate) | B | 0.1556 | 0.0583 | — | 1 | 10.66 | 0.87 | Sheet |
| Oral | Anavar (oxandrolone) | A | 0.2792 | 0.0667 | 772 | 0.3 | 231.6 | 0.625 | Sheet; PMC7134583 |
| Oral | Dianabol (methandienone) | B | 0.2188 | 0.0820 | — | 10 | 116.4 | 0.95 | Sheet |
| Oral | Anadrol (oxymetholone) | A | 0.3326 | 0.1458 | 37.6 | 1 | 37.6 | 0.95 | Sheet |
| Oral | Winstrol (stanozolol) | B | 0.375 | 0.1406 | — | 5 | 61.25 | 1.0 | Sheet |
| Oral | Turinabol | B | 0.6667 | 0.25 | — | 7 | 85.75 | 1.0 | Sheet; PMID 1798729 |
| Oral | Halotestin (fluoxymesterone) | A | 0.0833 | 0.075 | 800 | 1 | 800 | 0.57 | Sheet; PMID 4009439 |
| Oral | Superdrol (methasterone) | B | 0.4167 | 0.1563 | — | 20 | 122.5 | 0.5 | Sheet |
| Oral | Proviron (mesterolone) | A | 0.5208 | 0.0667 | 12.4 | 1 | 12.4 | 0.03 | Sheet; Proviron PI |
| Oral | Primobolan (methenolone acetate) | B | 0.2083 | 0.0781 | — | 1 | 10.78 | 0.88 | Sheet |
| Oral | Andriol (oral testosterone undecanoate) | A | 0.7667 | 0.2042 | 2.4876 | 0.7584 | 1.887 | 0.068 | Sheet; PMC4168025 |
| Support | Arimidex (anastrozole) | A | 1.95 | 0.0417 | 3930 | 1 | 3930 | 0.8 | Sheet; PMID 19470631 |
| Support | Aromasin (exemestane) | A | 0.9458 | 0.0594 | 57.6 | 1 | 57.6 | 0.05 | Sheet; PMC1884784 |
| Support | Femara (letrozole) | A | 1.3896 | 0.0775 | 45.684 | 1 | 45.68 | n/a → 1.0 | Sheet; PMID 16229115 |
| Support | Nolvadex (tamoxifen) | A | 1.975 | 0.3442 | 200 | 1 | 200 | 0.15 | Sheet; Nolvadex FDA review |
| Support | Clomid (clomiphene) | A | 5 | 0.2083 | 40 | 1 | 40 | 0.95 | Sheet; PMID 19033451 |
| Support | hCG | A | 1.9625 | 1 | 2072 | 1 | 0.2072 per IU | 0.45 | Sheet; PMC8301557 |
| Support | Caber (cabergoline) | A | 3.5833 | 0.083 | 4.03 | 1 | 4.03 | 0.465 | Sheet; PMID 12844325 |
| Support | Telmisartan | A | 0.9708 | 0.0708 | 770.06 | 1 | 770.06 | 0.43 | Sheet; PMID 17009837 |
| Support | Nebivolol | A | 0.7054 | 0.1296 | 11.6 | 1 | 11.6 | 0.54 | Sheet; PMID 24845234 |
| Support | Cialis (tadalafil) | A | 0.7083 | 0.1042 | 1725 | 1 | 1725 | n/a → 1.0 | Sheet; PMC1885023 |
| Support | T3 (liothyronine) | A | 0.9183 | 0.1042 | 6.92 per mcg | 1 | 6920 | n/a → 1.0 | Sheet; PMC5167556 |
| Support | Clen (clenbuterol) | A | 1.1067 | 0.1083 | 0.333 per mcg | 1 | 333 | n/a → 1.0 | Sheet; PMC4694390 |
| Peptide | Semaglutide | A | 6.5 | 1.25 | 2879.1 | 1 | 2879 | 0.89 | Sheet; PMC7854449 |
| Peptide | Semaglutide (oral) | A | 0.54 | 0.0590 | 232.38 | 1.5 | 348.6 | 0.008 | Sheet; Rybelsus EPAR |
| Peptide | Tirzepatide | A | 4.8625 | 1.5 | 5826.67 | 1 | 5827 | 0.8 | Sheet; PMC9268041 |
| Peptide | Retatrutide | A | 6.1389 | 1.6819 | 11495.37 | 1 | 11495 | 0.8 | Sheet; Cell Metab S1550-4131(22)00312-6 |
| Peptide | Mazdutide | A | 18.1 | 3.0146 | 7348.33 | 1 | 7348 | --- → 1.0 | Sheet; PMC9561728 |
| Peptide | HGH (somatropin) | A | 0.1722 | 0.2208 | 622.9 | 1 | 207.6 per IU | 0.63 | Sheet |

Display units of the non-steroids: ng/mL, except cabergoline and clenbuterol in pg/mL; T3 in ng/dL.

Compounds outside the sheet keep their label-based values (unchanged from presets-2026-10a):

| Section | Preset | t½ | Tmax | P | F | Source |
|---|---|---|---|---|---|---|
| Support | Enclomiphene | 10 h | 2.5 h | relative | 1.0 | Published t½ ≈ 10 h, tmax 2–3 h |
| Peptide | Liraglutide | 13 h | 11 h | 5833 ng/mL per mg | 0.55 | Victoza label: 0.6 mg → Cmax 35 ng/mL, tmax 8–12 h, t½ ≈ 13 h |
| Peptide | Tesamorelin | 0.53 h | 0.15 h | 191.6 pg/mL per mg | 0.04 | Egrifta label: 2 mg → Cmax 3 831 pg/mL, tmax 0.15 h, t½ 26–38 min |
| Peptide | PT-141 (bremelanotide) | 2.7 h | 1 h | 4160 ng/mL per mg | 1.0 | Vyleesi label: 1.75 mg → Cmax 72.8 ng/mL, tmax 1 h, t½ 2.7 h |
| Peptide | Cagrilintide | 177 h | 48 h | relative | 1.0 | Lancet 2021 phase 1b: t½ 159–195 h, tmax 24–72 h |
| Peptide | CJC-1295 DAC | 166.8 h | est. | relative | 1.0 | Teichman et al. 2006 (JCEM): t½ 5.8–8.1 d |
| Peptide | Ipamorelin | 2 h | est. | relative | 1.0 | Gobburu et al. 1999: t½ ≈ 2 h |
| Peptide | CJC-1295 (no DAC), MK-677, BPC-157, TB-500, GHK-Cu, Melanotan II, AOD-9604, MOTS-c | — | — | — | — | No reliable human level data (MK-677: reported half-lives conflict) |

¹ F corrected from the sheet (Nebido and Test U MCT 0.65, Deca 0.73, DHB 1.0) to the ester fraction, parent ÷ ester
molar mass from the formulas with IUPAC standard atomic weights (C 12.011, H 1.008, O 15.999): testosterone undecanoate
C30H48O3 456.71 → 288.43 ÷ 456.71 = 0.632; nandrolone decanoate C28H44O3 428.66 → 274.40 ÷ 428.66 = 0.640;
1-testosterone cypionate C27H40O3 412.61 → 288.43 ÷ 412.61 = 0.699. Every other injectable ester's F is within 1.2 % of
its ester fraction (`EsterFractionTest`). F only affects relative curves, so none of these changes a plotted level.

Sheet values that look off against labels are kept as the sheet has them: tamoxifen t½ 1.98 d (label 5–7 d),
fluoxymesterone t½ 2 h (label ≈ 9 h), oral semaglutide t½ 0.54 d (the sheet scaled it to match injectable levels), and
Tmax longer than t½ for EQ and Winstrol Depot.

**Sheet rows not imported** (also listed in `PkSheetTest`):
- Testosterone "Trestolone": the mislabelled 53-day Nebido form (see the differences above).
- Testosterone gel and sublingual test base: no transdermal or sublingual preset.
- Boldenone cypionate: empty row; the preset clones Test C.
- Estradiol (EstroGel, cypionate, valerate) and progesterone (oral, vaginal): outside the preset library.
- DNP and the SARMs (Ostarine, Ligandrol, Andarine, Testolone, Cardarine, S-23): outside the preset library.

**Stored data (`PresetMigration`).** Log snapshots and edited presets keep their own copy of the kinetics. On seeding
(start-up and after a restore), a copy that still equals the kinetics a preset shipped with in presets-2026-09b/-10a
(half-life, Tmax, peak, unit; F ignored) is replaced by the current preset's, so old doses are plotted with the
current model too. Kinetics the user changed are left alone. No database schema change: kinetics are stored as JSON.

**Before and after** (steady state, ng/dL, average and trough–peak; "relative only" = no absolute curve before):

| Dose | Before (presets-2026-10a) | After (presets-2026-10b) |
|---|---|---|
| Test E 125 mg/wk (62.5 mg every 3.5 d) | 2,235 (2,013–2,468) | 737 (664–814) |
| Test E 250 mg/wk (125 mg every 3.5 d) | 4,470 (4,026–4,936) | 1,475 (1,329–1,629) |
| Test E 500 mg/wk (250 mg every 3.5 d) | 8,939 (8,053–9,872) | 2,950 (2,657–3,258) |
| Test C 100 mg/wk (weekly) | 969 (856–1,101) | 582 (514–661) |
| Test C 200 mg/wk (weekly) | 1,939 (1,713–2,202) | 1,163 (1,028–1,321) |
| Test P 50 mg every 2 d | 1,318 (943–1,763) | unchanged |
| Nebido 1000 mg every 10 wk | 914 (457–1,559) | unchanged |
| Deca 200 mg/wk (weekly) | 1,782 (1,484–2,108) | unchanged |
| NPP 100 mg every 2 d | 1,766 (1,522–2,032) | unchanged |
| EQ 400 mg/wk (weekly) | 652 (621–718) | unchanged |
| Masteron P 100 mg every 2 d | relative only | 1,740 (1,334–2,058) |
| Tren A 50 mg every 2 d | relative only | 676 (455–883) |
| Tren E 200 mg/wk (100 mg every 3.5 d) | relative only | 4,622 (4,489–4,689) |
| Anavar 20 mg/day | 6,733 (1,660–16,833) | 2,020 (498–5,050) |
| Dianabol 30 mg/day | relative only | 1,291 (199–3,644) |

Test E 300 mg/wk now has a steady trough of 1,328 ng/dL against the 1,345 ng/dL nadir of Bhasin et al. 2001, so the
Test E multiplier does what the earlier calibration proposal ([proposals/pk-calibration.md](proposals/pk-calibration.md),
superseded) asked for.

## Units (`LabUnits.kt`)
Curves are computed in the unit of their data (ng/dL, ng/mL or pg/mL). With *SI* chosen in Settings > Units and formats,
absolute curves are shown per litre by molar amount: ng/dL × 10 ÷ M and ng/mL × 1000 ÷ M give nmol/L, pg/mL × 1000 ÷ M
gives pmol/L, where M is the molar mass (g/mol, PubChem) of the plotted parent molecule (`MolarMass.byGroup`; testosterone
288.42, so 1 nmol/L = 28.84 ng/dL). Relative curves and peptides or hormones measured by mass (hCG, somatropin, GLP-1
agonists) keep their unit. The conversion changes only the display; preset parameters are unchanged.

Lab results (bloodwork) are stored in conventional units with the factors of the CycleTracker web app
(`BloodMarkers`). A total testosterone result is drawn on the Testosterone curve when that curve is absolute in ng/dL
(converted like the curve), so a measurement and the estimate can be compared at the same time.

A result is flagged Low, In lab range or High only against the lab's own printed range (a missing side has no limit;
limits count as in range). Without a lab range it has no flag and no range is shown: the typical adult male limits in
`BloodMarkers` serve only the import's unit checks, so the app never judges a result by ranges of its own. A result reported only as
a limit ("<0.3") is flagged only when its true value cannot lie on both sides of a limit, otherwise it has no flag
(`MarkerResult.flag()`, [BLOODWORK_IMPORT.md](BLOODWORK_IMPORT.md) §6.4).

## Limitations
- Linear superposition and linear dose scaling: no linear regression, no per-user level adjustment.
- No endogenous production, suppression, active metabolites or individual factors (weight, injection site, volume).
- Estimates are for planning and comparison, not blood test results.
- No estradiol estimate. A single factor on the testosterone curve cannot account for aromatase inhibitor, Dbol or hCG
  changes, so measured estradiol is shown only as lab results.
