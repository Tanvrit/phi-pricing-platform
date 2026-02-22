# Pricing Engine — Deep Dive

The `PricingEngine` class in `:shared` replicates **every formula** in the Excel workbook `Rate_Calculator_v7.0.xlsm`, in the exact same row order. This document explains how each stage works.

## Table of Contents
1. [Calculation Order](#1-calculation-order)
2. [Base Premium — Age-Progressive Lookup](#2-base-premium--age-progressive-lookup)
3. [Zone Factors](#3-zone-factors)
4. [Plan-Tier Factors](#4-plan-tier-factors)
5. [Accumulation Bases — The Key Concept](#5-accumulation-bases--the-key-concept)
6. [Rows 7–18: First Batch of % Covers](#6-rows-718-first-batch-of--covers)
7. [Row 19: Home Care (Flat)](#7-row-19-home-care-flat)
8. [Rows 20–30: Second Batch of % Covers](#8-rows-2030-second-batch-of--covers)
9. [Rows 31–51: Flat and Member-Level Covers](#9-rows-3151-flat-and-member-level-covers)
10. [Rows 54, 55, 57, 58: Pre-Calculated Covers](#10-rows-54-55-57-58-pre-calculated-covers)
11. [Rows 52, 53, 56: Post Covers](#11-rows-52-53-56-post-covers)
12. [Row 59: UW Loading](#12-row-59-uw-loading)
13. [Rows 64–72: Discounts](#13-rows-6472-discounts)
14. [Rows 76–77: Instalment Loading](#14-rows-7677-instalment-loading)
15. [Tenure Discount](#15-tenure-discount)
16. [Family Type Auto-Derivation](#16-family-type-auto-derivation)
17. [Validation Rules](#17-validation-rules)
18. [QuoteResult Structure](#18-quoteresult-structure)

---

## 1. Calculation Order

The engine processes each policy year independently, then aggregates. For a 3-year policy, all arrays are length 3.

```
Step 1:  Base premium per policy year           (age-progressive rate table lookup)
Step 2:  Rows  7–18  Accumulating % covers
Step 3:  Row   19    Home Care flat
Step 4:  Rows 20–30  More accumulating % covers (some skip Row 19)
Step 5:  Rows 31–51  Flat / member-level covers
Step 6:  Rows 54,55,57,58  Pre-calculated covers (referenced by rows 52 & 56)
Step 7:  Rows 52,53,56  Post covers
Step 8:  Row  59     UW loading
Step 9:  Rows 64–72  Discounts (capped at plan maxDiscountCap, default 30%)
Step 10: Rows 76–77  Instalment loading ÷ instalment count
```

The engine maintains a working map `yr: Map<coverId, DoubleArray>` where each array has one element per policy year. All subsequent calculations reference this map via `sumYr(ids, yearIndex)`.

---

## 2. Base Premium — Age-Progressive Lookup

For each policy year `y`, the primary insured ages by 1:

```kotlin
val base = DoubleArray(years) { yr ->
    val band = getAgeBand(request.primaryAge + yr)
    data.getBasePremium(planId, familyType, zone, band.minAge, sumInsured)
}
```

The lookup chain inside `LocalRateDataProvider.getBasePremium`:

```
1. Select per-family-type rate table: RateTables.PHI_BASIC_FT[familyType]
2. Find nearest age-band (approximate-match, same as Excel VLOOKUP)
3. Find nearest sum-insured key (nearest lower or equal)
4. Multiply: base × zoneFactor × planFactor
```

### Age Bands (from Excel Ref sheet I:J, rows 2–16)

| Band Label | Age Range | Band Min Key |
|-----------|-----------|-------------|
| 5 - 17 | 5 to 17 | 5 |
| 18 - 25 | 18 to 25 | 18 |
| 26 - 30 | 26 to 30 | 26 |
| 31 - 35 | 31 to 35 | 31 |
| 36 - 40 | 36 to 40 | 36 |
| 41 - 45 | 41 to 45 | 41 |
| 46 - 50 | 46 to 50 | 46 |
| 51 - 55 | 51 to 55 | 51 |
| 56 - 60 | 56 to 60 | 56 |
| 61 - 65 | 61 to 65 | 61 |
| 66 - 70 | 66 to 70 | 66 |
| 71 - 75 | 71 to 75 | 71 |
| 76 - 80 | 76 to 80 | 76 |
| 81 - 85 | 81 to 85 | 81 |
| 85+ | 86+ | 86 (same rates as 81–85) |

Age-band lookup uses approximate match: returns the last band whose `minAge <= age`.

### Sample Base Rates — PHI Basic, 1A family, Zone 4 (cheapest zone, INR/year)

| Age Band | ₹5L | ₹10L | ₹25L | ₹50L | ₹1Cr |
|----------|-----|------|------|------|------|
| 5–17 | 1,800 | 2,023 | 2,798 | 3,223 | 4,020 |
| 18–35 | 3,875 | 4,417 | 6,287 | 7,307 | 9,227 |
| 36–40 | 4,598 | 5,253 | 7,528 | 8,770 | 11,098 |
| 41–45 | 5,472 | 6,265 | 9,032 | 10,532 | 13,358 |
| 46–50 | 6,923 | 7,922 | 11,403 | 13,297 | 16,853 |
| 51–55 | 8,595 | 9,863 | 14,273 | 16,673 | 21,183 |
| 56–60 | 11,380 | 13,092 | 19,053 | 22,295 | 28,395 |
| 61–65 | 15,347 | 17,692 | 25,862 | 30,310 | 38,670 |
| 66–70 | 19,932 | 23,015 | 33,742 | 39,575 | 50,547 |
| 71–75 | 27,270 | 31,525 | 46,338 | 54,398 | 69,552 |
| 76–80 | 34,410 | 39,805 | 58,598 | 68,820 | 88,038 |
| 81+ | 42,982 | 49,748 | 73,310 | 86,132 | 110,232 |

---

## 3. Zone Factors

Zone factors multiply the Zone 4 base rate. Zone 4 is the cheapest (rural / non-metro).

| Zone | Label | Factor |
|------|-------|--------|
| Zone 1 | Metro cities | 1.63 |
| Zone 2 | Major cities | 1.49 |
| Zone 3 | Tier-2 cities | 1.26 |
| Zone 4 | Rural / others | 1.00 (base) |
| Pan India | Senior / floater plans | 1.15 |

**Example:** A 35-year-old in Zone 1 with ₹10L SI pays `4,417 × 1.63 = ₹7,200/year` (PHI Basic, 1A).

---

## 4. Plan-Tier Factors

Applied on top of the PHI Basic Zone 4 rate table:

| Plan ID prefix / exact | Factor |
|------------------------|--------|
| `PHI_BASIC` | 1.00 |
| `PHI_POSP` | 0.90 |
| `PHI_FLAGSHIP*` | 1.35 |
| `PHI_SENIOR` | 1.20 |
| `PHI_SUBSTANDARD` | 1.50 |
| `PHI_GLOBAL*` (excl. Plus) | 2.80 |
| `PHI_GLOBAL_PLUS*` | 3.20 |

Final base premium per year = `tableRate × zoneFactor × planFactor`

---

## 5. Accumulation Bases — The Key Concept

Each percentage cover is calculated as:

```
cover_premium[year] = rate × SUM(selected prior covers, year)
```

The "accumulation base" is the ordered list of cover IDs whose year-premiums are summed to form the multiplication base. This exactly mirrors the Excel `SUM(X7:X_n)` formulas.

```kotlin
val COVER_ACCUM_BASES: Map<String, List<String>>
```

Key facts:
- The accumulation base always includes `BASE` (the base premium)
- Each row adds itself to the base for subsequent rows
- Row 19 (HOME_CARE) is a flat — some subsequent rows include it in their base, some skip it
- Row 22 (DONOR_PLUS) is flat × members — included in rows 23+ bases but skipped by rows 20–21

This cascading structure means that enabling earlier covers increases the cost of later covers — consistent with the Excel model.

---

## 6. Rows 7–18: First Batch of % Covers

| Row | Cover ID | Rate Type | Key Parameters |
|-----|----------|-----------|----------------|
| 7 | `day1_instant` | % × base(r4) | Coverage multiple (1.5X/2X/3X/4X) × SI |
| 8 | `loyalty_bonus` | % per-year age band | Bonus type × age band (young ≤45 / senior >45) |
| 9 | `double_cover_7yr` | Flat 3.25% | None |
| 10 | `chronic_instant` | % by condition count | Single/Two/Three comorbid conditions |
| 11 | `consumables_list1` | Flat 5% | None |
| 12 | `ped_waiting` | % — **Year 1 only** | Reduction type (3→2yr: 12%, 3→1yr: higher) |
| 13 | `specific_illness_waiting` | % by primary age band | None |
| 14 | `modern_treatment_plus` | % by plan | PHI Basic: +2.89%, Flagship/Global: −2.89% |
| 15 | `room_rent_mod` | % by room type × plan | General/Shared/Single Private AC/Any Room |
| 16 | `disease_sublimit` | % by plan | PHI Basic only — removes sub-limits |
| 17 | `pre_post_hosp` | Flat 10% | None |
| 18 | `consumable_plus` | Flat 9% | None |

### Row 12 — PED Waiting (Year 1 Only)

This is the only cover that is charged in year 1 only. For a 3-year policy, years 2 and 3 have zero PED loading:

```kotlin
if (enabled(CoverIds.PED_WAITING)) {
    val rate = data.getCoverRate(CoverIds.PED_WAITING, params.param1)
    val arr  = DoubleArray(years)
    arr[0]   = rate * sumYr(accumBases[PED_WAITING]!!, 0)   // year 1 only
    yr[PED_WAITING] = arr
}
```

---

## 7. Row 19: Home Care (Flat)

Home Care is a flat INR amount (₹167/year) regardless of sum insured, zone, or plan. It is not part of the accumulation base for rows 20–21 (Infinite Claim, Restoration Plus), but IS included in the bases for rows 23+ (Spouse Protect onwards).

```kotlin
if (enabled(HOME_CARE)) {
    val flat = data.getCoverRate(HOME_CARE)  // = 167.0
    yr[HOME_CARE] = DoubleArray(years) { flat }
}
```

---

## 8. Rows 20–30: Second Batch of % Covers

| Row | Cover ID | Rate Type | Key Params |
|-----|----------|-----------|------------|
| 20 | `infinite_claim` | % by SI | SI tier lookup |
| 21 | `restoration_plus` | % per-year age+SI | Age ≤35: lower rate, >35: higher rate |
| 22 | `donor_plus` | Flat ₹417 × total members | None |
| 23 | `spouse_protect` | % by SI (base includes r19+r22) | SI tier lookup |
| 24 | `durable_medical` | % per-year age+SI | Age ≤45: lower, >45: higher |
| 25 | `tenure_wise` | % by tenure × SI | Tenure label × SI tier |
| 26 | `smart_select` | Flat −15% (discount) | None — base skips r19 |
| 27 | `good_health` | Flat 0% currently | — |
| 28 | `per_claim_deductible` | % discount by deductible × SI | ₹15,000 or ₹25,000 |
| 29 | `aggregate_deductible` | % discount by deductible × SI | ₹25,000 / ₹50,000 / ₹1L |
| 30 | `co_pay` | % discount by co-pay % × plan table | 5%–60% co-pay options |

### Row 22 — Donor Plus: Flat Per Member

```kotlin
if (enabled(DONOR_PLUS)) {
    val perMember = data.getCoverRate(DONOR_PLUS)  // = 417.0
    val count     = getFamilyTypeInfo(request.familyType).totalMembers
    yr[DONOR_PLUS] = DoubleArray(years) { perMember * count }
}
```

For a 2A2C family (4 members): `417 × 4 = ₹1,668/year`

---

## 9. Rows 31–51: Flat and Member-Level Covers

These covers are independent of the accumulation chain. Their premiums are fixed or scale with member count, not with accumulated premium base.

### Flat Covers (single INR amount regardless of family/SI)

| Row | Cover ID | Flat Amount |
|-----|----------|------------|
| 31 | `child_protect` | ₹100 |
| 36 | `air_ambulance` | ₹433 |
| 37 | `fitness_plus` | ₹649 |
| 38 | `wellness_package` | ₹0 (complimentary) |
| 39 | `second_opinion` | ₹67 |
| 41 | `post_delivery_care` | ₹167 |
| 43 | `surrogate_mother` | ₹908 (3yr+ policy) |
| 44 | `oocyte_donor` | ₹845.75 (12 months) |
| 45 | `post_discharge_care` | ₹167 |
| 49 | `pru_health_specialist` | ₹83 |

### Flat Per-Member Covers

| Row | Cover ID | Amount | Scaling |
|-----|----------|--------|---------|
| 22 | `donor_plus` | ₹417 | × total members |
| 46 | `adventure_sports` | ₹183 | × adult count |
| 48 | `female_vaccination` | ₹3,333 | × female member count |
| 50 | `advance_health_checkup` | ₹975 (Basic) / higher (Advance) | × adult count |

### Member-Level Covers (per-member rates, summed)

| Row | Cover ID | Rate Basis |
|-----|----------|------------|
| 32 | `daily_hospital_cash` | Per member, per age band, per cash limit |
| 35 | `personal_accident` | Per adult member, by coverage amount |
| 47 | `chronic_management` | Per adult member, by condition count |

**Daily Hospital Cash example:** For a 35yo + 32yo couple with ₹1,000/day limit:
```
yr[32] = getCoverRate(daily_hospital_cash, band=31, param1="1000")
       + getCoverRate(daily_hospital_cash, band=31, param1="1000")
```

### Parameterised Flat Covers

| Row | Cover ID | Param1 | Param2 |
|-----|----------|--------|--------|
| 33 | `convalescence` | Benefit amount (₹5K/10K/20K) | Trigger (3/5/10+ days) |
| 34 | `compassionate` | Benefit amount (₹25K/₹50K) | — |
| 40 | `maternity_newborn` | Cover amount (₹50K/₹1L/₹2L) | Waiting period (9M/24M/36M/48M) |
| 42 | `infertility` | Cover amount (₹1L/₹2L) | Waiting period (9M/24M/36M/48M) |
| 51 | `cashless_opd` | OPD limit (₹2,500/₹5,000/₹10,000) | — |

---

## 10. Rows 54, 55, 57, 58: Pre-Calculated Covers

These rows are evaluated before rows 52 and 56 because those rows reference them in their accumulation bases.

### Row 54 — Critical Illness (Member-Level, Adults Only)

Rate per mille lookup by member's age band, applied to the chosen coverage amount:

```kotlin
arr[y] = members.filter { it.isAdult }.sumOf { m ->
    val band       = getAgeBand(m.age + y)
    val ratePerMil = getMemberLevelRate(CRITICAL_ILLNESS, band.minAge)
    ratePerMil * coverage / 1000.0
}
```

Coverage options: ₹5L / ₹10L / ₹20L / ₹30L / ₹50L. Adults only (age >= 18).

### Row 57 — Cancer Booster

Per-year age+SI factor applied to the full rows 4–30 base (`R57_BASE`):

```kotlin
pctCoverPerYear(CANCER_BOOSTER, R57_BASE) { band ->
    val rates  = RateTables.CANCER_BOOSTER_RATES
    val nearSI = nearestSI(sumInsured, rates)
    val pair   = rates[nearSI]   // Pair(youngRate, seniorRate)
    if (band <= 45) pair.first else pair.second
}
```

### Row 58 — Cancer Screening

Flat annual INR by age band (not a percentage cover):

```kotlin
yr[CANCER_SCREENING] = DoubleArray(years) { y ->
    val band = getAgeBand(primaryAge + y)
    getCoverRate(CANCER_SCREENING, ageBandMinAge = band.minAge)
}
```

---

## 11. Rows 52, 53, 56: Post Covers

These are evaluated after the flat/member covers because their accumulation bases include rows 54, 57, 58.

| Row | Cover ID | Rate | Accumulation Base |
|-----|----------|------|-------------------|
| 52 | `prudential_healthy` | 0.75% | SUM(base, r7–r30, r31–r51, r54, r57, r58) |
| 53 | `premium_return` | 15% | SUM(base, r7–r52) |
| 56 | `enhanced_geo` | % by geography | SUM(base, r7–r55, r57, r58) |

### Row 52 — Prudential Healthy Loading

This 0.75% loading accumulates over virtually everything. It is labelled "loading" but appears as a positive charge. On large policies with many covers selected, this can add a few thousand rupees.

### Row 56 — Enhanced Geographical Scope

Applies only to Global plans. The rate varies by geography:

| Geography | Rate |
|-----------|------|
| Worldwide excl. USA & Canada | ~16.67% |
| Asia excl. India | lower |
| Europe | intermediate |
| Worldwide incl. USA & Canada | highest |

---

## 12. Row 59: UW Loading

Underwriting loading is a per-case additional charge applied for sub-standard health risks. The `uwLoadingFactor` is entered by the underwriter (e.g., 0.25 = 25% loading).

```kotlin
val uwBase = COVER_ACCUM_BASES[UW_LOADING]!!   // = R59_BASE
for (y in 0 until years) {
    uwArr[y] = uwLoadingFactor * sumYr(uwBase, y)
}
```

The `R59_BASE` includes rows 4–30 (accumulating covers) + select flat covers (child protect, daily cash, convalescence, compassionate, air ambulance, second opinion, adventure sports, chronic management, pru health specialist) + CI, prudential healthy, premium return, enhanced geo, cancer booster.

UW loading is excluded from its own base (no recursive loop) and excluded from member-level flat amounts like maternity, infertility, personal accident.

---

## 13. Rows 64–72: Discounts

Discounts are applied to `totalBeforeDiscount` and **hard-capped at the plan's `maxDiscountCap`** (default 30%).

```kotlin
val rawDiscTotal    = discBreakdowns.sumOf { it.amount }      // negative
val cappedDiscTotal = -min(abs(rawDiscTotal), total * maxDiscountCap)
val totalAfterDisc  = totalBeforeDisc + cappedDiscTotal
```

### Standalone Discounts

| Discount ID | Name | Rate |
|-------------|------|------|
| `disc_employee` | Employee / Affiliate | −10% |
| `disc_nri` | NRI | −15% |
| `disc_auto_debit` | Auto-Debit | −2.5% |
| `disc_commission_lieu` | In Lieu of Commission | −15% |
| `disc_gmc` | Corporate GMC | −5% |

### CIBIL Score Discount

| CIBIL Band | Rate |
|------------|------|
| ≤700 | lowest |
| 701–750 | 2.5% |
| 751–800 | ... |
| 801–849 | ... |
| ≥850 | up to 15% |

### Smart Select Discount (Row 26, counted in discount section)

Smart Select Network Discount applies −15% of the accumulated premium up to row 25. It is evaluated during the cover calculation pass (not the discount pass) but is marked `isDiscount = true` in the result breakdown.

### Mutual Exclusion Rules

- `per_claim_deductible` and `aggregate_deductible` cannot be combined
- `disc_employee` and `disc_commission_lieu` cannot be combined

---

## 14. Rows 76–77: Instalment Loading

Instalment loading is applied after discounts:

```kotlin
val instalLoadingRate = paymentMode.instalmentLoadingRate
val instalLoadingAmt  = instalLoadingRate * totalAfterDisc
val instalCount       = getInstalmentCount(tenure, paymentTenure, paymentMode)
val instalPremium     = (totalAfterDisc + instalLoadingAmt) / instalCount
```

### Loading Rates by Payment Mode

| Payment Mode | Loading Rate |
|-------------|-------------|
| Single Premium | 0% |
| Annual | 0% |
| Half-Yearly | 2% |
| Quarterly | 4% |
| Monthly | 6% |

Instalment count = `policyYears × paymentsPerYear`. For a 3-year policy paid monthly: `3 × 12 = 36` instalments.

---

## 15. Tenure Discount

The tenure discount applies to **Single Premium** policies with tenure > 1 year. Rates are per-year and applied progressively:

| Policy Year | Discount Rate |
|-------------|--------------|
| Year 1 | 0% |
| Year 2 | 7.5% |
| Year 3 | 10% |
| Year 4 | 12.5% |
| Year 5 | 15% |

```kotlin
val tenureRates = listOf(0.0, 0.075, 0.10, 0.125, 0.15)
var discAmt = 0.0
for (y in 0 until years) {
    val yearTotal = yr.values.sumOf { it.getOrElse(y) { 0.0 } }
    discAmt += yearTotal * (-tenureRates.getOrElse(y) { 0.0 })
}
```

This means a 5-year single-premium policy benefits from a weighted average discount of approximately 8.5% on the total multi-year premium.

---

## 16. Family Type Auto-Derivation

The desktop app auto-derives the family type from the member input fields:

| Adults | Children | Family Type Code | Floater? |
|--------|----------|-----------------|---------|
| 1 | 0 | `1A` | No (Individual) |
| 2 | 0 | `2A` | Yes |
| 2 | 1 | `2A1C` | Yes |
| 2 | 2 | `2A2C` | Yes |
| 2 | 3 | `2A3C` | Yes |
| 2 | 4 | `2A4C` | Yes |
| 1 | 1 | `1A1C` | Yes |
| 1 | 2 | `1A2C` | Yes |
| 1 | 3 | `1A3C` | Yes |
| 1 | 4 | `1A4C` | Yes |
| 2+ | any | `multi` | No (multi-individual) |

Primary insured age drives age-band selection; spouse age is captured as a separate `Member` in the `members` list.

---

## 17. Validation Rules

The engine validates before calculating and returns `isValid = false` with error messages if rules are violated:

```kotlin
// Age range
if (primaryAge < 5 || primaryAge > 99)
    errors += "Age must be between 5 and 99"

// Positive SI
if (sumInsured <= 0)
    errors += "Sum insured must be positive"

// Mutual exclusions
if (CHRONIC_INSTANT in ids && PED_WAITING in ids)
    errors += "Cannot combine Chronic Instant with PED Waiting"

if (CONSUMABLES_LIST1 in ids && CONSUMABLE_PLUS in ids)
    errors += "Cannot combine Consumables List I with Consumable Plus"

if (PER_CLAIM_DEDUCTIBLE in ids && AGGREGATE_DEDUCTIBLE in ids)
    errors += "Cannot combine Per Claim and Aggregate Deductible"

if (DISC_EMPLOYEE in discIds && DISC_COMMISSION_LIEU in discIds)
    errors += "Cannot combine Employee Discount with Commission Discount"
```

---

## 18. QuoteResult Structure

```kotlin
data class QuoteResult(
    val requestId: String,            // "Q-{timestamp}"
    val planId: String,
    val basePremiumTotal: Double,     // sum of base[yr] across all years
    val coverBreakdown: List<CoverPremiumBreakdown>,
    val totalAddons: Double,          // sum of all selected cover totals
    val uwLoadingAmount: Double,
    val totalBeforeDiscount: Double,  // base + addons + UW loading
    val discountBreakdown: List<DiscountBreakdown>,
    val totalDiscountAmount: Double,  // negative value (capped at 30%)
    val totalAfterDiscount: Double,
    val instalmentLoadingAmount: Double,
    val instalmentPremium: Double,    // 0 for Single Premium / Annual
    val instalmentCount: Int,
    val yearlyBreakdown: List<YearBreakdown>,   // per-year age, band, subtotal
    val isValid: Boolean,
    val validationErrors: List<String>
)
```

Each `YearBreakdown` contains:
- `year` (1-based)
- `age` (primary insured age that year)
- `ageBand` label
- `basePremium` for that year
- `coverPremiums` map of coverId → annual premium that year
- `subtotal` (base + all covers for that year)

---

*Next: [Rate Tables Reference](./03-rate-tables.md)*
