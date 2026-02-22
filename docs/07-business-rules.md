# Business Rules

This document defines all business rules enforced by the Rate Calculator platform. Rules are implemented in `PricingEngine.kt`, `LocalRateDataProvider.kt`, and the domain models in `:shared`.

## Table of Contents
1. [Age Rules](#1-age-rules)
2. [Sum Insured Constraints](#2-sum-insured-constraints)
3. [Family Type Rules](#3-family-type-rules)
4. [Mutual Exclusion Rules](#4-mutual-exclusion-rules)
5. [Cover Eligibility Rules](#5-cover-eligibility-rules)
6. [PED Waiting Period Rule](#6-ped-waiting-period-rule)
7. [Discount Cap Rule](#7-discount-cap-rule)
8. [Tenure Discount Rule](#8-tenure-discount-rule)
9. [Instalment Loading Rules](#9-instalment-loading-rules)
10. [Zone Assignment Rules](#10-zone-assignment-rules)
11. [Plan-Level Constraints](#11-plan-level-constraints)
12. [UW Loading Rules](#12-uw-loading-rules)
13. [All 14 Plan IDs and Descriptions](#13-all-14-plan-ids-and-descriptions)
14. [Payment Mode Constraints](#14-payment-mode-constraints)
15. [Member-Level Cover Rules](#15-member-level-cover-rules)

---

## 1. Age Rules

### Primary Insured Age

| Rule | Value |
|------|-------|
| Minimum age | 5 years |
| Maximum age | 99 years |

```kotlin
if (req.primaryAge < 5 || req.primaryAge > 99)
    errors += "Age must be between 5 and 99"
```

### Plan-Level Age Restrictions

Individual plans impose tighter age limits:

| Plan | Minimum Age | Maximum Age |
|------|------------|------------|
| PHI Basic | 5 | 85 |
| PHI POSP | 5 | 85 |
| PHI Flagship 1 | 5 | 85 |
| PHI Flagship 2 | 5 | 85 |
| PHI Flagship 4 | 5 | 85 |
| PHI Senior | **46** | 85 |
| PHI Sub Standard | **46** | 85 |
| PHI Global (all variants) | 5 | **65** |
| PHI Global Plus (all variants) | 5 | **65** |

The desktop app and buy-online journey should validate against `Plan.minAge` / `Plan.maxAge` before submitting to the engine.

### Age Band Boundaries

Age bands use **approximate match** (VLOOKUP-equivalent): the band with the highest `minAge ≤ primaryAge`. When the primary insured ages across a band boundary during a multi-year policy, the premium steps up accordingly.

| Crossing Age | Transition | Effect |
|-------------|-----------|--------|
| 36 | 31–35 → 36–40 | Premium step-up (~15–20%) |
| 46 | 41–45 → 46–50 | Premium step-up (~25–30%) |
| 56 | 51–55 → 56–60 | Premium step-up (~30–35%) |
| 61 | 56–60 → 61–65 | Premium step-up (~35–40%) |

---

## 2. Sum Insured Constraints

### Sum Insured Validation

```kotlin
if (req.sumInsured <= 0)
    errors += "Sum insured must be positive"
```

### Sum Insured Options by Plan

| Plan Group | Available Sum Insureds |
|-----------|----------------------|
| PHI Basic, Flagship 1 | ₹2L to ₹1Cr (13 options) |
| PHI Flagship 2 | ₹2L to ₹2Cr (15 options, includes ₹1.5Cr, ₹2Cr) |
| PHI Flagship 4 (Private Banker) | ₹5L, ₹7.5L, ₹10L, ₹15L, ₹20L, ₹25L, ₹1Cr |
| PHI POSP | ₹2L, ₹3L, ₹4L, ₹5L only |
| PHI Senior | ₹5L, ₹7L, ₹10L, ₹15L, ₹20L, ₹25L, ₹50L, ₹75L, ₹1Cr |
| PHI Sub Standard | ₹5L, ₹7L, ₹10L, ₹15L, ₹25L |
| PHI Global (all) | ₹50L, ₹75L, ₹1Cr, ₹2Cr, ₹3Cr |

### Nearest-SI Interpolation

If the requested sum insured is not an exact key in the rate table, the engine uses `nearestSI()` which returns the nearest lower or equal key. This mirrors Excel's MATCH / VLOOKUP behaviour for SI columns.

---

## 3. Family Type Rules

### Valid Family Type Codes

| Code | Adults | Children | Total | Floater? |
|------|--------|----------|-------|---------|
| `1A` | 1 | 0 | 1 | No — individual |
| `2A` | 2 | 0 | 2 | Yes |
| `2A1C` | 2 | 1 | 3 | Yes |
| `2A2C` | 2 | 2 | 4 | Yes |
| `2A3C` | 2 | 3 | 5 | Yes |
| `2A4C` | 2 | 4 | 6 | Yes |
| `1A1C` | 1 | 1 | 2 | Yes |
| `1A2C` | 1 | 2 | 3 | Yes |
| `1A3C` | 1 | 3 | 4 | Yes |
| `1A4C` | 1 | 4 | 5 | Yes |
| `multi` | 2+ | any | 2+ | No — multi-individual |

### Adult vs Child Definition

- **Adult:** `age >= 18`
- **Child:** `age < 18`

This affects:
- Member-level covers (Critical Illness, Personal Accident, Chronic Management — adults only)
- Donor Plus (flat amount × total members)
- Adventure Sports (flat × adult count)
- Female Vaccination (flat × female member count)
- Advance Health Check-up (flat × adult count)

---

## 4. Mutual Exclusion Rules

The following cover combinations are **explicitly prohibited** and cause the engine to return `isValid = false`:

### Rule ME-1: Chronic Instant + PED Waiting

```
"chronic_instant"  CANNOT be combined with  "ped_waiting"
```

**Reason:** Both covers address pre-existing disease coverage from Day 1. They serve overlapping purposes and have conflicting underwriting logic.

**Error message:** `"Instant Hospitalisation for Chronic Conditions cannot be combined with PED Waiting Period modification"`

### Rule ME-2: Consumables List I + Consumable Plus

```
"consumables_list1"  CANNOT be combined with  "consumable_plus"
```

**Reason:** `consumables_list1` covers List I only (+5%). `consumable_plus` covers Lists I–IV (+9%). Selecting both would double-count List I items.

**Error message:** `"Consumables Cover List I cannot be combined with Consumable Plus (List I–IV)"`

### Rule ME-3: Per Claim Deductible + Aggregate Deductible

```
"per_claim_deductible"  CANNOT be combined with  "aggregate_deductible"
```

**Reason:** These are two distinct deductible mechanisms. Only one deductible structure can apply to a policy.

**Error message:** `"Per Claim Deductible and Aggregate Deductible cannot be combined"`

### Rule ME-4: Employee Discount + Commission Discount

```
"disc_employee"  CANNOT be combined with  "disc_commission_lieu"
```

**Reason:** Both discounts represent distribution channel concessions. The `disc_commission_lieu` is explicitly the channel commission waived in lieu of a customer discount. An employee benefiting from both would be double-dipping.

**Error message:** `"Employee Discount and Discount in Lieu of Commission cannot be combined"`

---

## 5. Cover Eligibility Rules

### Adults-Only Covers

The following covers apply only to adult members (age >= 18). Children are excluded from their premium calculations:

| Cover | Restriction | Effect |
|-------|------------|--------|
| `critical_illness` | Adults only | Premium calculated per adult member only |
| `personal_accident` | Adults only | Premium = adult count × rate lookup |
| `chronic_management` | Adults only | Premium per adult × condition rate |
| `adventure_sports` | Adults only | Flat ₹183 × adult count |
| `advance_health_checkup` | Adults only | Rate per adult × adult count |

### Female-Only Covers

| Cover | Restriction |
|-------|------------|
| `female_vaccination` | Female members only (₹3,333/female) |
| `maternity_newborn` | Implicitly for females — no age-gender check in engine, policy-level rule |
| `maternity_fixed` | Same as above |
| `surrogate_mother` | No code-level restriction — policy underwriting |
| `oocyte_donor` | No code-level restriction — policy underwriting |

### Global-Plan-Only Covers

| Cover | Restriction |
|-------|------------|
| `enhanced_geo` | Meaningful only for Global plan variants. On Domestic plans, the rate is effectively 0%. |

### PHI Basic Only

| Cover | Restriction |
|-------|------------|
| `disease_sublimit` | Rate is non-zero only for `PHI_BASIC`. Returns 0% for Flagship and Global plans (sub-limits already removed in those plans). |
| `modern_treatment_plus` | Rate is positive (+2.89%) for PHI Basic; negative (−2.89%) for Flagship/Global (already bundled in). |

---

## 6. PED Waiting Period Rule

### Year 1 Only

`ped_waiting` (Modification of PED Waiting Period) is charged **in Year 1 only**. Years 2 through N of a multi-year policy carry zero PED loading.

```kotlin
val arr = DoubleArray(years)
arr[0] = rate * sumYr(accumBases[PED_WAITING]!!, 0)   // year 1 only
// arr[1..n] remain 0.0
yr[PED_WAITING] = arr
```

This reflects the actuarial logic: the waiting period modification benefit applies in Year 1 when coverage begins. In subsequent years, the pre-existing disease waiting period has already been served or the cover remains continuously active.

### PED Reduction Options

| Option | Waiting Period Changed From | Changed To |
|--------|---------------------------|-----------|
| "3 to 2 Years" | 3 years (standard) | 2 years |
| "3 to 1 Year" | 3 years (standard) | 1 year |

A higher reduction (3 to 1 Year) carries a higher premium loading.

---

## 7. Discount Cap Rule

### Maximum Total Discount

The combined total of all discounts (standalone + in-cover discounts like Smart Select, Co-Pay, Per Claim Deductible, Aggregate Deductible) is **hard-capped** at the plan's `maxDiscountCap`.

Default cap: **30%** (`maxDiscountCap = 0.30`)

```kotlin
val rawDiscTotal    = discBreakdowns.sumOf { it.amount }   // sum of negative amounts
val cappedDiscTotal = -min(abs(rawDiscTotal), totalBeforeDisc * maxDiscountCap)
```

If a customer selects:
- Smart Select: −15%
- Co-Pay 20%: −15%
- Auto-Debit: −2.5%
- NRI: −15%

Raw total = −47.5%. But the cap limits this to −30% of `totalBeforeDiscount`.

### Plan-Level Cap Override

Individual plans can set a different cap via `Plan.maxDiscountCap`. For example, a restricted distribution plan might have a 20% cap.

The cap is passed into the `QuoteRequest.maxDiscountCap` field. If not supplied, it defaults to 0.30.

---

## 8. Tenure Discount Rule

### Eligibility

- Only applies to **Single Premium** payment mode
- Only meaningful for tenure > 1 year
- Selected via the `disc_tenure` discount ID

### Stepped Discount Rates

| Policy Year | Discount Rate Applied to That Year's Premium |
|-------------|---------------------------------------------|
| 1 | 0% |
| 2 | 7.5% |
| 3 | 10% |
| 4 | 12.5% |
| 5 | 15% |

The discount is calculated per year and then summed:

```kotlin
val tenureRates = listOf(0.0, 0.075, 0.10, 0.125, 0.15)
var discAmt = 0.0
for (y in 0 until years) {
    val yearTotal = yr.values.sumOf { it.getOrElse(y) { 0.0 } }
    discAmt += yearTotal * (-tenureRates.getOrElse(y) { 0.0 })
}
```

**Effective weighted average discounts:**

| Tenure | Year Discounts | Effective Total Discount |
|--------|---------------|--------------------------|
| 1 year | 0% | 0% |
| 2 years | 0%, 7.5% | ~3.75% weighted |
| 3 years | 0%, 7.5%, 10% | ~5.83% weighted |
| 4 years | 0%, 7.5%, 10%, 12.5% | ~7.5% weighted |
| 5 years | 0%, 7.5%, 10%, 12.5%, 15% | ~9% weighted |

The effective percentage is approximate because base premiums change with age.

---

## 9. Instalment Loading Rules

Loading is applied **after** discounts and **before** dividing into instalments.

| Payment Mode | Loading | Formula |
|-------------|---------|---------|
| Single Premium | 0% | No loading |
| Annual | 0% | No loading |
| Half-Yearly | 2% | `totalAfterDisc × 0.02` |
| Quarterly | 4% | `totalAfterDisc × 0.04` |
| Monthly | 6% | `totalAfterDisc × 0.06` |

### Instalment Count

```
Single Premium:  1 instalment (total premium, once)
Annual:          policyYears instalments (one per year)
Half-Yearly:     policyYears × 2
Quarterly:       policyYears × 4
Monthly:         policyYears × 12
```

For a 3-year policy, monthly mode:
- Instalment count = 36
- Per instalment = `(totalAfterDisc × 1.06) / 36`

### Tenure Discount + Instalment Mode Incompatibility

Tenure discount (`disc_tenure`) only applies when `paymentMode == SINGLE_PREMIUM`. If any other payment mode is selected, the tenure discount has no effect even if selected. This rule is enforced in the discount calculation block:

```kotlin
if (DISC_TENURE in discountIds &&
    request.paymentMode == PaymentMode.SINGLE_PREMIUM &&
    request.tenure != Tenure.ONE_YEAR) {
    // apply tenure discount
}
```

---

## 10. Zone Assignment Rules

### Zone Factors

| Zone | Factor | Description |
|------|--------|-------------|
| Zone 1 | 1.63 | Highest-cost metros: Mumbai, Delhi, Bengaluru, Chennai, Kolkata, Hyderabad |
| Zone 2 | 1.49 | Major cities: Pune, Ahmedabad, Jaipur |
| Zone 3 | 1.26 | Tier-2 cities |
| Zone 4 | 1.00 | Rural / all other — baseline |
| Pan India | 1.15 | Used for Senior and Sub-Standard plans |

### Senior / Sub-Standard Zone Restriction

PHI Senior and PHI Sub Standard plans are restricted to:
- Pan India
- Zone 1

They cannot be purchased at Zone 2, 3, or 4 rates:

```kotlin
private val TWO_ZONES = listOf("Pan India", "Zone 1")

Plan(
    id = "PHI_SENIOR",
    availableZones = TWO_ZONES,  // Only Pan India and Zone 1
    ...
)
```

### Pincode Mapping

The desktop application includes a pincode-to-zone lookup. Zone auto-detection is a UX feature only — the API does not validate or enforce pincode-zone consistency. The zone submitted in the `QuoteRequest` is used directly.

---

## 11. Plan-Level Constraints

### Plan Factor on Base Rate

On top of PHI Basic Zone 4 rates, each plan applies a multiplier:

| Plan | Factor |
|------|--------|
| PHI Basic | 1.00 (reference) |
| PHI POSP | 0.90 (simplified product, lower cost) |
| PHI Flagship (any) | 1.35 (+35% over Basic) |
| PHI Senior | 1.20 (+20% over Basic) |
| PHI Sub Standard | 1.50 (+50% over Basic) |
| PHI Global (excl. Plus) | 2.80 (+180% over Basic) |
| PHI Global Plus | 3.20 (+220% over Basic) |

### Allowed Cover IDs

Each plan has an `allowedCoverIds` set. If empty, all covers are available. If non-empty, only listed covers can be selected.

Currently all plans have `allowedCoverIds = emptySet()` (all covers allowed), but this field can be populated for restricted products.

---

## 12. UW Loading Rules

### Application

UW loading is an additional percentage applied to the UW base (`R59_BASE`) on top of all selected covers.

```kotlin
uwArr[y] = uwLoadingFactor * sumYr(R59_BASE, y)
```

### UW Loading Base (R59_BASE)

Includes:
- Base premium
- All accumulating % covers (rows 7–30, including co-pay)
- Select flat covers: Child Protect, Daily Hospital Cash, Convalescence, Compassionate, Air Ambulance, Second Opinion, Adventure Sports, Chronic Management, Pru Health Specialist
- Critical Illness, Prudential Healthy, Premium Return, Enhanced Geo, Cancer Booster

Excludes intentionally:
- Personal Accident (separate PA underwriting)
- Maternity-related covers
- Female Vaccination, Oocyte Donor, Surrogate Mother (standard frequency benefits)

### UW Loading Factor

Entered as a decimal fraction (e.g., `0.25` = 25% extra loading). The `QuoteRequest.uwLoadingFactor` field accepts any non-negative value. The engine does not cap UW loading — the underwriter is responsible for setting an appropriate value.

---

## 13. All 14 Plan IDs and Descriptions

| Plan ID | Name | Type | Geography | UW Category |
|---------|------|------|-----------|-------------|
| `PHI_BASIC` | PHI Basic | DOMESTIC | India | STANDARD |
| `PHI_POSP` | PHI POSP | DOMESTIC_POSP | India | STANDARD |
| `PHI_FLAGSHIP1` | PHI Flagship 1 | DOMESTIC_FLAGSHIP | India | STANDARD |
| `PHI_FLAGSHIP2` | PHI Flagship 2 | DOMESTIC_FLAGSHIP | India | STANDARD |
| `PHI_FLAGSHIP4` | PHI Flagship 4 (Private Banker) | DOMESTIC_FLAGSHIP | India | STANDARD |
| `PHI_SENIOR` | PHI Senior | DOMESTIC_SENIOR | India | SENIOR |
| `PHI_SUBSTANDARD` | PHI Sub Standard | DOMESTIC_SUBSTANDARD | India | SUB_STANDARD |
| `PHI_GLOBAL_EXCL` | PHI Global (Excl. USA & Canada) | GLOBAL | Worldwide excl. USA & Canada | STANDARD |
| `PHI_GLOBAL_ASIA` | PHI Global (Asia excl. India) | GLOBAL | Asia excl. India | STANDARD |
| `PHI_GLOBAL_EUROPE` | PHI Global (Europe) | GLOBAL | Europe | STANDARD |
| `PHI_GLOBAL_PLUS` | PHI Global Plus (Incl. USA & Canada) | GLOBAL_PLUS | Worldwide incl. USA & Canada | STANDARD |
| `PHI_GLOBAL_PLUS_EXCL` | PHI Global Plus (Excl. USA & Canada) | GLOBAL_PLUS | Worldwide excl. USA & Canada | STANDARD |
| `PHI_GLOBAL_PLUS_ASIA` | PHI Global Plus (Asia excl. India) | GLOBAL_PLUS | Asia excl. India | STANDARD |
| `PHI_GLOBAL_PLUS_EUROPE` | PHI Global Plus (Europe) | GLOBAL_PLUS | Europe | STANDARD |

### Co-Payment Tables by UW Category

| Category | Co-Payment Table | Description |
|----------|-----------------|-------------|
| STANDARD | OMNIBUS | Standard co-payment rates (optional) |
| SENIOR | SENIOR | Mandatory co-payment for senior citizens |
| SUB_STANDARD | SUB_STANDARD | Higher co-payment for sub-standard risks |

---

## 14. Payment Mode Constraints

### Single Premium Rules

- Minimum tenure: 1 year (trivially)
- Tenure discount (`disc_tenure`) only applies to Single Premium + 2+ year policies
- Instalment loading: 0% (no loading)
- Instalment count: 1 (single payment for entire policy term)

### Annual Mode Rules

- One payment per year of coverage
- No instalment loading
- Instalment count equals policy tenure in years

### Sub-Annual Mode Rules (Monthly, Quarterly, Half-Yearly)

- Instalment loading applies (2%/4%/6% respectively)
- Instalment count = `policyYears × paymentsPerYear`
- Per instalment = `(totalAfterDiscount + loadingAmount) / instalmentCount`
- Tenure discount does **not** apply regardless of tenure

---

## 15. Member-Level Cover Rules

### Critical Illness — Adult-Only Aggregation

```kotlin
arr[y] = members.filter { it.isAdult }.sumOf { m ->
    val band = getAgeBand(m.age + y)
    val ratePerMil = getMemberLevelRate(CRITICAL_ILLNESS, band.minAge)
    ratePerMil * coverage / 1000.0
}
```

- Only adult members (age >= 18) are included
- Each adult member's age band is evaluated independently
- For multi-year policies, each member ages by 1 per year
- The coverage amount (param1) is the same for all adult members on the policy

### Daily Hospital Cash — Per-Member, Per-Age-Band

```kotlin
arr[y] = members.sumOf { m ->
    val band = getAgeBand(m.age + y)
    getMemberLevelRate(DAILY_HOSPITAL_CASH, band.minAge, cashLimit)
}
```

- All members (adults and children) are included
- Rate varies by the member's age band and chosen daily cash limit
- Children in lower age bands pay lower rates

### Personal Accident — Adults Only, Flat Lookup

```kotlin
arr[y] = members.filter { it.isAdult }.sumOf {
    getMemberLevelRate(PERSONAL_ACCIDENT, 0, coverageAmount)
}
```

- Adults only; age band is not used (flat rate by coverage amount)
- Coverage amounts: ₹10L / ₹20L / ₹30L / ₹50L / ₹1Cr

### Chronic Management — Adults Only

```kotlin
arr[y] = members.filter { it.isAdult }.sumOf {
    getMemberLevelRate(CHRONIC_MANAGEMENT, 0, conditionCount)
}
```

- Rate by condition count (1, 2, or 3 conditions)
- Same rate applies to each adult member regardless of age

---

*Next: [Development Guide](./08-development.md)*
