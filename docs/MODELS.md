# Level model

## Equations
One-compartment model with first-order absorption, per dose *D* (base units: mg or IU):

```
A' = −ka·A            (depot)
C' =  ka·A − ke·C     (amount in body / central compartment)
A(0⁺) += D · activeFraction · bioavailability
```

`ka = ln2 / absorption t½`, `ke = ln2 / elimination t½`. For depot esters the elimination half-life is the
**apparent terminal** half-life (ester release included), so the curve reflects the amount of active hormone
the depot is releasing and the body is clearing. Output unit: estimated mg (or IU) in the body.

### Engine (`core/domain/.../pk/PkEngine.kt`)
- Doses are grouped by (ka, ke). For each group the state (A, C) is advanced exactly between consecutive
  dose/sample times using the closed-form solution
  `C(t+Δ) = C·e^(−ke·Δ) + A·ka/(ka−ke)·(e^(−ke·Δ) − e^(−ka·Δ))`, `A(t+Δ) = A·e^(−ka·Δ)`.
  Cost O(doses + samples) instead of O(doses × samples).
- The Bateman term uses `expm1` and the smaller exponential (no cancellation/overflow) and a series for ka ≈ ke.
- Tests check the engine against naive superposition (≤1e−9 relative), an RK4 ODE solution (≤1e−6),
  and closed-form steady state for dose every τ:
  `Css(t) = D·ka/(ka−ke)·[e^(−ke·t)/(1−e^(−ke·τ)) − e^(−ka·t)/(1−e^(−ka·τ))]`, average `D/(ke·τ)`.

### Metrics (`Levels.kt`)
- **Steady range/average**: current plan items simulated until transients decay (≥ 10 slowest half-lives, ≥ 28 days),
  then measured over 7 days (covers weekday and interval schedules).
- **90 % of steady**: `ln(10) / min(ka, ke)` for the slowest compound in the group.
- **Below 10 %**: first time after the final dose that the level drops under 10 % of its post-dose peak;
  shown only when the plan ends within a year.
- Doses older than 10 half-lives before the window are ignored (< 0.1 % contribution).

## Presets
Estimates for relative curves, editable per compound. `activeFraction` = MW(parent) / MW(ester).

| Compound | Absorption t½ | Elimination t½ | Active fraction | Source |
|---|---|---|---|---|
| Testosterone propionate | 6 h | 19 h | 0.837 | Community-cited apparent half-life ≈0.8 d |
| Testosterone enanthate | 8.66 h | 189 h | 0.720 | IM popPK, PMC10174206 Table 1 (ka 0.08/h, CL 50.6 L/h, V 13 800 L) |
| Testosterone cypionate | 13.6 h | 92.2 h | 0.699 | popPK, PMID 29436172 Table 2 (ka 1.22/d, CL/F 2.6 kL/d, V/F 14.4 kL) |
| Testosterone undecanoate (IM) | 48 h | 504 h | 0.632 | Label terminal half-life ≈21 d, tmax ≈7 d |
| Nandrolone phenylpropionate | 6 h | 65 h | 0.675 | Community-cited ≈2.7 d |
| Nandrolone decanoate | 12 h | 168 h | 0.640 | Release half-life ≈6–7 d, PMID 3865478 |
| Trenbolone acetate | 4 h | 24 h | 0.865 | Community-cited ≈1 d |
| Trenbolone enanthate | 10 h | 120 h | 0.707 | Community-cited ≈5 d |
| Drostanolone propionate | 6 h | 19 h | 0.844 | Community-cited ≈0.8 d |
| Drostanolone enanthate | 10 h | 120 h | 0.731 | Community-cited ≈5 d |
| Methenolone enanthate | 10 h | 120 h | 0.729 | Community-cited ≈5 d |
| Boldenone undecylenate | 24 h | 336 h | 0.633 | Community-cited ≈14 d |
| hCG (SC, IU) | 6 h | 33 h | 1 | Label terminal half-life ≈29–33 h, tmax 12–24 h |
| Anastrozole | 0.5 h | 46 h | 1 | Label ≈40–50 h |
| Letrozole | 0.4 h | 48 h | 1 | Label ≈2 d |
| Exemestane | 0.4 h | 24 h | 1 | Label ≈24 h |
| Tamoxifen | 1.5 h | 132 h | 1 | Label 5–7 d |
| Clomiphene | 2 h | 120 h | 1 | Label ≈5 d (isomer mixture) |
| Enclomiphene | 0.7 h | 10 h | 1 | Published ≈10 h |
| Oxandrolone | 0.3 h | 9.4 h | 1 | Label ≈9.4 h |
| Methandienone | 0.4 h | 4.5 h | 1 | Reported 3–6 h |
| Stanozolol (oral) | 0.5 h | 9 h | 1 | Reported ≈9 h |
| Oxymetholone | 0.5 h | 8.5 h | 1 | Reported ≈8–9 h |

Bioavailability defaults to 1 (not applied) for all presets; users can set it per compound.

## Limitations
No endogenous production, feedback/suppression, active metabolites, covariates (weight, injection site, volume)
or individual calibration. Absolute values are not blood concentrations.
