# Proposal: calibrate the testosterone level curves

Status: **superseded** (presets-2026-10b): the owner chose the PK sheet's parameters and model, whose 0.33 Test E multiplier already brings the 300 mg/wk trough to 1,328 ng/dL ([MODELS.md](../MODELS.md)). Kept for the record.

## Problem
1. **Test E reads about 3x high against the study it cites.** Bhasin et al. 2001 (AJP-Endo 281:E1172, the
   `ajpendo.00502.2001` source of the Test E preset) gave TE weekly to GnRH-suppressed men. Their testosterone was
   therefore all exogenous. With the current peak of 11.31 ng/dL per mg, the engine's steady-state troughs are:

   | TE mg/wk | Model trough | Observed nadir | Model / observed |
   |---|---|---|---|
   | 25 | 335 | 253 | 1.33 |
   | 50 | 671 | 306 | 2.19 |
   | 125 | 1677 | 542 | 3.09 |
   | 300 | 4025 | 1345 | 2.99 |
   | 600 | 8049 | 2370 | 3.40 |

   At 300 mg/wk the model shows a peak of 6914 and an average of 5364 ng/dL. The observed nadirs here are the ones quoted
   in the review; check them against the paper's table before applying anything.
   The 25 and 50 mg doses do not scale linearly. That is probably residual endogenous production or the assay floor, so
   the fit below uses 125–600 mg.

2. **The esters disagree with each other.** Every ester releases the same testosterone, so all of them share one
   clearance. The area under the curve per active mg should then be equal (Css,avg = F·D / (CL·τ)). Currently it is not:

   | Preset | AUC per active mg (ng·h/dL) |
   |---|---|
   | Test E (and Test PP / Iso / Dec, derived from it) | 4172 |
   | Test C | 2327 |
   | Nebido | 2363 |
   | Test P | 1507 |
   | Test Susp | 256 |

   With the same weekly active dose, the model predicts a 1.8x higher average on Test E than on Test C.

## Proposed calibration
- **One area per active mg of testosterone: A\* ≈ 1265 ng·h/dL.** This is a least-squares fit of the model trough to the
  125, 300 and 600 mg nadirs, which gives a peak of 3.43 ng/dL per mg TE (3.66 / 3.78 / 3.33 for each dose alone).
- **Every testosterone ester's peak follows from A\*** through the existing rule (`Presets.derivedPeak`):
  `P = F · A* / (Tmax/2 + t½/ln2)`. Each ester keeps its own t½, Tmax and F.

  | Preset | Current peak | Proposed peak (ng/dL per mg) |
  |---|---|---|
  | Test E | 11.31 | 3.43 |
  | Test C | 5.56 | 3.02 |
  | Test P | 26.0 | 21.8 |
  | Nebido (F 0.63) | 1.187 | 0.62 |
  | Test PP / Iso / Dec | derived | 9.43 / 8.27 / 3.97 (derived as now) |
  | Test Susp | 5.07 | 25.0 |

  With these peaks, the TE troughs are 508 / 1220 / 2441 ng/dL at 125 / 300 / 600 mg (0.94 / 0.91 / 1.03 of observed).
- **Implementation:**
  - Add a `testosteroneArea` constant with its source.
  - Give Test E, Test C, Test P, Nebido and Test Susp `derivedFrom` a reference built from it, instead of a sheet peak.
  - Bump `Presets.VERSION` and update the tables in `docs/MODELS.md`.
- **Tests:**
  - TE 300 mg/wk steady-state trough within ±25 % of 1345.
  - Every "Testosterone" preset's area per active mg (`peak · (Tmax/2 + t½/ln2) / F`) within 10 % of A\*.

## Open questions for the owner
- Test Susp's current peak comes from a horse study (scaled). Under A\*, its peak would rise five-fold. That is
  consistent with the model, but there is no human data to check it against.
- Derived peaks keep the area but not the shape. Short esters (Test P, Susp) may peak higher in the model than in people.
  A separate tmax/peak check against Test P data (JCEM 63(6):1361) would show this.
- Nandrolone (Deca / NPP) has not been checked the same way. It would need its own reference study.
- Oral testosterone undecanoate (Andriol) has bioavailability below its ester fraction. Leave it on its own sheet values.
