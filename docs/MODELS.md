# Level model

Level curves follow the method of **Steroid Plotter** (steroidplotter.com) and its public data sheet
(Google Sheet `1Hrbw5eXb8bw1YfZZmBHHS3IY_j95V5DJsc6-piO__Ns`, tab "SteroidPlotter Data"). Steroid Plotter's own code is
obfuscated, so the method is implemented as its FAQ and changelog describe it: levels rise to the peak (Cmax) at the time
to peak (Tmax); elimination with the half-life starts only once the peak is reached; doses add up. Every curve is an
estimate and is labelled as such in the app.

## Per-dose curve (`core/domain/.../pk/CurveEngine.kt`)
For a dose given at time 0 with single-dose peak `P`:

```
c(t) = P · t / Tmax                     0 ≤ t < Tmax
c(t) = P · 2^(−(t − Tmax) / t½)          t ≥ Tmax
```

The level at any time is the sum over all doses. The engine groups doses by (Tmax, t½); within a group peaks occur in dose
order, so the decaying part is one running sum advanced by `e^(−k·Δt)` and only doses still rising are summed individually.
Doses older than `Tmax + 10·t½` are dropped (< 0.1 % contribution). Tests compare the engine with naive superposition and
check the long-run average against `P · (Tmax/2 + t½/ln2) / τ`.

## Peak `P`
- **Study peak ("Advanced" in the sheet):** `P = dose × Cmax`, where the sheet's Cmax is **ng/dL per mg dosed** (ester mass as
  injected). This matches known cases, e.g. tadalafil 20 mg → 345 ng/mL (label 378 ng/mL). Exceptions in the sheet:
  clenbuterol and T3 are per mcg (converted to per mg); hCG and HGH are per mg with the sheet's own IU factors
  (100 IU ≈ 0.01 mg; 1 mg ≈ 3 IU), stored per IU.
- **No study peak ("Basic"), same parent molecule known:** the peak is derived so that the area under the curve per *active*
  mg equals the reference compound's (same molecule → same clearance):
  `P_b = F_b · (Cmax_ref · A_ref / F_ref) / A_b`, with `A = Tmax/2 + t½/ln2` and F the sheet's fraction
  (ester fraction × bioavailability). Used for Test PP, Test Iso and Test Dec (from Test E), oral Winstrol (from Winstrol
  Depot) and boldenone cypionate (from EQ). Computed once in `Presets.derivedPeak`.
- **No study peak, no reference:** the curve is **relative**: `P = dose × F` (active mg), plotted as "mg active (relative)".
- If a level group mixes compounds with and without a peak (or different display units), the whole group is shown relative.
- **Missing Tmax** (our assumption; Steroid Plotter estimates it from the half-life but does not publish how):
  `Tmax = clamp(0.2 · t½, 1 h, 2 d)` for injections, 1.5 h for oral compounds.

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

## Compare mode (experimental, `Compare.kt`)
Several groups on one axis as a percentage of a reference. Each group is divided by a reference on its own scale, so
absolute (ng/dL, ng/mL) and relative curves can share the axis. References depend only on plan and logs, not on the window.
- **Plan baseline:** 100 % = the group's steady-state peak at its planned dose (see Metrics). The curve is below 100 % while
  building up and above it when more is taken than planned.
- **Shared dose baseline:** 100 % = the anchor's steady-state peak at its weekly dose (anchor: chosen, else testosterone,
  else the highest-dosed mg group). Another mg group `c` is divided by `ssPeak_c · weekly_anchor / weekly_c`, i.e. its own
  steady state at the anchor's weekly dose; peaks are linear in dose, so at steady state it sits at
  `weekly_c / weekly_anchor · 100 %` and equal weekly doses overlap. Potency differences are not modelled.
- **Fallbacks:** groups dosed in other units (IU, mcg-based peptides) or without an mg plan keep the plan baseline; groups
  without an active plan use their peak in the visible window. The legend states which reference each line uses.
- Percentages compare trends only; they are not blood levels and not comparable between compounds' effects.

## Presets (`Presets.kt`, `VERSION = presets-2026-09b`)
Values copied from the sheet unless noted: t½ and Tmax in days (stored in hours), Cmax in ng/dL per mg, F.
"derived" = peak from a reference as above; "relative" = no peak; "—" = log only, no curve.

| Section | Preset | t½ (d) | Tmax (d) | Cmax | F | Source |
|---|---|---|---|---|---|---|
| Injectable | Test E (testosterone enanthate) | 7.19 | 1.39 | 11.31 | 0.72 | Sheet; PMC4721027, ajpendo.00502.2001 |
| Injectable | Test C (testosterone cypionate) | 6.9 | 4.5 | 5.56 | 0.70 | Sheet; tau.amegroups 11328, psp4.12287 |
| Injectable | Test P (testosterone propionate) | 1.04 | 1.06 | 26.0 | 0.84 | Sheet; JCEM 63(6):1361 |
| Injectable | Nebido (testosterone undecanoate) | 33.9 | 10.0 | 1.187 | 0.65 | Sheet; jandrol.109.009597, Nebido PI |
| Injectable | Test PP / Test Iso / Test Dec | 2.5 / 3.1 / 5.6 | est. | derived | 0.69 / 0.75 / 0.65 | Sheet; PMC9611952 |
| Injectable | Test Susp (testosterone suspension) | 1.375 | 0.25 | 5.067 | 1.0 | Sheet (horse study, scaled by the sheet) |
| Injectable | Deca (nandrolone decanoate) | 10.2 | 1.83 | 3.99 | 0.73 | Sheet; JCEM 90(5):2624 |
| Injectable | NPP (nandrolone phenylpropionate) | 2.4 | 1.0 | 8.91 | 0.67 | Sheet; PMID 9103484 |
| Injectable | Tren A / Tren E / Parabolan | 1.5 / 11.0 / 8.0 | est. | relative | 0.87 / 0.71 / 0.66 | Sheet; S002228602030452X |
| Injectable | Masteron P / Masteron E | 2.0 / 4.5 | est. | relative | 0.84 / 0.73 | Sheet |
| Injectable | Primobolan Depot (methenolone enanthate) | 10.5 | est. | relative | 0.73 | Sheet |
| Injectable | EQ (boldenone undecylenate) | 5.125 | 6.0 | 1.098 | 0.63 | Sheet; PMID 17348894 |
| Injectable | Boldenone Cyp | 6.9 | 4.5 | derived (EQ) | 0.70 | Sheet (cloned from Test C) |
| Injectable | DHB (1-testosterone cypionate) | 9.0 | est. | relative | 1.0 | Sheet |
| Injectable | Winstrol Depot (stanozolol, injectable) | 3.42 | 7.0 | 8.12 | 1.0 | Sheet; PMID 17348894 |
| Injectable | MENT (trestolone acetate) | 0.156 | est. | relative | 0.87 | Sheet |
| Oral | Anavar (oxandrolone) | 0.279 | 0.067 | 772 | 0.625 | Sheet; PMC7134583 |
| Oral | Dianabol (methandienone) | 0.219 | est. | relative | 0.95 | Sheet |
| Oral | Anadrol (oxymetholone) | 0.333 | 0.146 | 37.6 | 0.95 | Sheet |
| Oral | Winstrol (stanozolol) | 0.375 | est. | derived (Depot) | 1.0 | Sheet |
| Oral | Turinabol | 0.667 | est. | relative | 1.0 | Sheet; PMID 1798729 |
| Oral | Halotestin (fluoxymesterone) | 0.083 | 0.075 | 800 | 0.57 | Sheet; PMID 4009439 |
| Oral | Superdrol (methasterone) | 0.417 | est. | relative | 0.5 | Sheet |
| Oral | Proviron (mesterolone) | 0.521 | 0.067 | 12.4 | 0.03 | Sheet; Proviron PI |
| Oral | Primobolan (methenolone acetate) | 0.208 | est. | relative | 0.88 | Sheet |
| Oral | Andriol (oral testosterone undecanoate) | 0.767 | 0.204 | 2.49 | 0.068 | Sheet; PMC4168025 |
| Support | Arimidex (anastrozole) | 1.95 | 0.042 | 3930 | 0.8 | Sheet; PMID 19470631 |
| Support | Aromasin (exemestane) | 0.946 | 0.059 | 57.6 | 0.05 | Sheet; PMC1884784 |
| Support | Femara (letrozole) | 1.39 | 0.078 | 45.7 | 1.0 | Sheet; PMID 16229115 |
| Support | Nolvadex (tamoxifen) | 1.975 | 0.344 | 200 | 0.15 | Sheet; Nolvadex FDA review |
| Support | Clomid (clomiphene) | 5.0 | 0.208 | 40 | 0.95 | Sheet; PMID 19033451 |
| Support | Enclomiphene | 10 h | 2.5 h | relative | 1.0 | Published t½ ≈ 10 h, tmax 2–3 h |
| Support | hCG | 1.96 | 1.0 | 2072 per mg | 0.45 | Sheet; PMC8301557 |
| Support | Caber (cabergoline) | 3.58 | 0.083 | 4.03 | 0.465 | Sheet; PMID 12844325 |
| Support | Telmisartan | 0.971 | 0.071 | 770 | 0.43 | Sheet; PMID 17009837 |
| Support | Nebivolol | 0.705 | 0.130 | 11.6 | 0.54 | Sheet; PMID 24845234 |
| Support | Cialis (tadalafil) | 0.708 | 0.104 | 1725 | 1.0 | Sheet; PMC1885023 |
| Support | T3 (liothyronine) | 0.918 | 0.104 | 6.92 per mcg | 1.0 | Sheet; PMC5167556 |
| Support | Clen (clenbuterol) | 1.107 | 0.108 | 0.333 per mcg | 1.0 | Sheet; PMC4694390 |
| Peptide | Semaglutide | 6.5 | 1.25 | 2879 | 0.89 | Sheet; PMC7854449 |
| Peptide | Semaglutide (oral) | 0.54 | 0.059 | 232 | 0.008 | Sheet; Rybelsus EPAR |
| Peptide | Tirzepatide | 4.86 | 1.5 | 5827 | 0.8 | Sheet; PMC9268041 |
| Peptide | Retatrutide | 6.14 | 1.68 | 11495 | 0.8 | Sheet; Cell Metab S1550-4131(22)00312-6 |
| Peptide | Mazdutide | 18.1 | 3.01 | 7348 | 1.0 | Sheet; PMC9561728 |
| Peptide | HGH (somatropin) | 0.172 | 0.221 | 623 per mg | 0.63 | Sheet |
| Peptide | Liraglutide | 13 h | 11 h | 5833 | 0.55 | Victoza label: 0.6 mg → Cmax 35 ng/mL, tmax 8–12 h, t½ ≈ 13 h |
| Peptide | Tesamorelin | 0.53 h | 0.15 h | 191.6 | 0.04 | Egrifta label: 2 mg → Cmax 3 831 pg/mL, tmax 0.15 h, t½ 26–38 min |
| Peptide | PT-141 (bremelanotide) | 2.7 h | 1 h | 4160 | 1.0 | Vyleesi label: 1.75 mg → Cmax 72.8 ng/mL, tmax 1 h, t½ 2.7 h |
| Peptide | Cagrilintide | 177 h | 48 h | relative | 1.0 | Lancet 2021 phase 1b: t½ 159–195 h, tmax 24–72 h |
| Peptide | CJC-1295 DAC | 166.8 h | est. | relative | 1.0 | Teichman et al. 2006 (JCEM): t½ 5.8–8.1 d |
| Peptide | Ipamorelin | 2 h | est. | relative | 1.0 | Gobburu et al. 1999: t½ ≈ 2 h |
| Peptide | CJC-1295 (no DAC), MK-677, BPC-157, TB-500, GHK-Cu, Melanotan II, AOD-9604, MOTS-c | — | — | — | — | No reliable human level data (MK-677: reported half-lives conflict) |

Sheet values are copied as given. Values that look off against labels are kept and noted here: tamoxifen t½ 1.98 d
(label 5–7 d), fluoxymesterone t½ 2 h (label ≈ 9 h), oral semaglutide t½ 0.54 d (the sheet scaled it to match injectable
levels), and Tmax longer than t½ for EQ and Winstrol Depot.

## Limitations
- Steroid Plotter's "linear regression" for Test E and Test C (smaller Cmax increase at higher doses) is not implemented;
  its coefficients are not published. Peaks scale linearly with dose.
- No endogenous production, suppression, active metabolites or individual factors (weight, injection site, volume).
- Estimates are for planning and comparison, not blood test results.
