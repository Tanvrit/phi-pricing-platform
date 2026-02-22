# Rate Tables Reference

This document catalogues all covers, their Excel row numbers, rate structures, and valid parameter options. All data is sourced from `Rate_Calculator_v7.0.xlsm` and the corresponding Kotlin files `RateTables.kt`, `CoverCatalog.kt`, and `CoverDefinitions.kt`.

## Table of Contents
1. [Section A — Accumulating % Covers (Rows 7–30)](#section-a--accumulating--covers-rows-730)
2. [Section B — Flat and Member-Level Covers (Rows 31–51)](#section-b--flat-and-member-level-covers-rows-3151)
3. [Section C — Pre-Calculated Covers (Rows 54–58)](#section-c--pre-calculated-covers-rows-5458)
4. [Section D — Post Covers (Rows 52, 53, 56)](#section-d--post-covers-rows-52-53-56)
5. [Section E — UW Loading (Row 59)](#section-e--uw-loading-row-59)
6. [Section F — Standalone Discounts (Rows 64–72)](#section-f--standalone-discounts-rows-6472)
7. [Sum Insured Options](#sum-insured-options)
8. [Zone Factors Table](#zone-factors-table)

---

## Section A — Accumulating % Covers (Rows 7–30)

These covers are expressed as a percentage of the accumulated premium base up to their row.

### A1. Day 1 Instant Hospitalisation (`day1_instant`) — Row 7

- **Type:** Percentage of base premium
- **Excel Row:** 7
- **Description:** Covers pre-existing diseases from Day 1 at a multiple of sum insured
- **Rate Basis:** Coverage multiple × SI — varies by both SI tier and selected multiple
- **Accumulation Base:** BASE only (Row 4)
- **Param1:** Coverage Multiple

| Coverage Multiple | Rate (varies by SI) |
|------------------|-------------------|
| 1.5X | Lower rate |
| 2X | Medium rate |
| 3X | Higher rate |
| 4X | Highest rate |

---

### A2. Loyalty Bonus (`loyalty_bonus`) — Row 8

- **Type:** Per-year age-band percentage
- **Excel Row:** 8
- **Description:** Increases sum insured every year at no extra cost
- **Rate Basis:** Bonus type × age band (young = age ≤45; senior = age >45)
- **Accumulation Base:** BASE + DAY1_INSTANT
- **Param1:** Bonus Type

| Bonus Option | Young Rate (≤45) | Senior Rate (>45) |
|-------------|-----------------|------------------|
| 100% upto 200% | Lower | Lower |
| 100% upto 500% | Medium | Medium |
| 100% upto 1000% | Higher | Higher |

---

### A3. Double Your Cover after 7 Years (`double_cover_7yr`) — Row 9

- **Type:** Flat percentage
- **Excel Row:** 9
- **Rate:** 3.25% of accumulated base
- **Accumulation Base:** BASE + DAY1_INSTANT + LOYALTY_BONUS
- **Params:** None

---

### A4. Instant Hospitalisation for Chronic Conditions (`chronic_instant`) — Row 10

- **Type:** Percentage by condition count
- **Excel Row:** 10
- **Description:** Covers hospitalisation for chronic conditions from Day 1
- **Accumulation Base:** BASE through DOUBLE_COVER_7YR
- **Param1:** Conditions

| Conditions | Rate |
|-----------|------|
| Single condition | ~20% |
| Two comorbid condition | Higher |
| Three comorbid condition | Highest |

> **Mutual Exclusion:** Cannot be combined with `ped_waiting`

---

### A5. Consumables Cover – List I (`consumables_list1`) — Row 11

- **Type:** Flat percentage
- **Excel Row:** 11
- **Rate:** 5% of accumulated base
- **Accumulation Base:** BASE through CHRONIC_INSTANT
- **Params:** None
- **Description:** Covers consumables from List I (gloves, syringes, etc.)

> **Mutual Exclusion:** Cannot be combined with `consumable_plus` (List I–IV)

---

### A6. Modification of PED Waiting Period (`ped_waiting`) — Row 12

- **Type:** Percentage — **Year 1 only**
- **Excel Row:** 12
- **Description:** Reduces pre-existing disease waiting period from 3 years
- **Accumulation Base:** BASE through CONSUMABLES_LIST1
- **Param1:** New Waiting Period

| Reduction | Rate |
|-----------|------|
| 3 to 2 Years | ~12% |
| 3 to 1 Year | Higher |

> **Business Rule:** Premium charged in Year 1 only. Years 2–N carry zero PED loading.

> **Mutual Exclusion:** Cannot be combined with `chronic_instant`

---

### A7. Modification of Specific Illness Waiting Period (`specific_illness_waiting`) — Row 13

- **Type:** Percentage by primary age band
- **Excel Row:** 13
- **Description:** Removes specific disease waiting period
- **Accumulation Base:** BASE through PED_WAITING
- **Params:** None (rate auto-derived from primary insured's age band)

| Age Band | Rate |
|----------|------|
| 18–35 | ~8.25% |
| 36–50 | Moderate |
| 51+ | Higher |

---

### A8. Modern Treatment Plus (`modern_treatment_plus`) — Row 14

- **Type:** Percentage by plan
- **Excel Row:** 14
- **Description:** Enhanced coverage for modern medical treatments
- **Accumulation Base:** BASE through SPECIFIC_ILLNESS_WAITING
- **Params:** None (plan auto-selected)

| Plan Type | Rate |
|-----------|------|
| PHI Basic | +2.89% (adds cost) |
| PHI Flagship | −2.89% (included, discounted) |
| PHI Global | −2.89% |

---

### A9. Room Rent Modification (`room_rent_mod`) — Row 15

- **Type:** Percentage by room type and plan
- **Excel Row:** 15
- **Accumulation Base:** BASE through MODERN_TREATMENT_PLUS
- **Param1:** Room Type

| Room Type | Notes |
|-----------|-------|
| General Room | Lowest cost |
| Shared Room | Moderate |
| Single Private AC | Higher |
| Any Room | Highest (no restriction) |

---

### A10. Diseases Specific Sub-limit Plus (`disease_sublimit`) — Row 16

- **Type:** Percentage by plan and SI
- **Excel Row:** 16
- **Rate:** ~6.47% (PHI Basic only)
- **Accumulation Base:** BASE through ROOM_RENT_MOD
- **Params:** None
- **Availability:** PHI Basic only; rate is 0% for Flagship/Global (already included)

---

### A11. Pre-Post Hospitalisation Lump Sum (`pre_post_hosp`) — Row 17

- **Type:** Flat percentage
- **Excel Row:** 17
- **Rate:** 10% of accumulated base
- **Accumulation Base:** BASE through DISEASE_SUBLIMIT
- **Params:** None

---

### A12. Consumable Plus – List I to IV (`consumable_plus`) — Row 18

- **Type:** Flat percentage
- **Excel Row:** 18
- **Rate:** 9% of accumulated base
- **Accumulation Base:** BASE through PRE_POST_HOSP
- **Params:** None
- **Description:** Covers consumables across all four lists (superset of List I)

> **Mutual Exclusion:** Cannot be combined with `consumables_list1`

---

### A13. Infinite Claim (`infinite_claim`) — Row 20

- **Type:** Percentage by SI
- **Excel Row:** 20
- **Description:** Unlimited claim restoration during the policy year
- **Accumulation Base:** BASE through CONSUMABLE_PLUS (skips HOME_CARE at Row 19)

| SI Tier | Rate |
|---------|------|
| ₹5L | ~10% |
| ₹10L | Moderate |
| ₹25L+ | Lower % |

---

### A14. Restoration Plus (`restoration_plus`) — Row 21

- **Type:** Per-year age+SI percentage
- **Excel Row:** 21
- **Description:** Enhanced restoration — restores sum insured for same illness
- **Accumulation Base:** BASE through INFINITE_CLAIM (skips HOME_CARE)

| Age Band | Rate |
|----------|------|
| ≤35 | Lower (e.g., 0.8%) |
| >35 | Higher (e.g., 1.35%) |

---

### A15. Spouse Protect (`spouse_protect`) — Row 23

- **Type:** Percentage by SI (base includes HOME_CARE + DONOR_PLUS)
- **Excel Row:** 23
- **Accumulation Base:** BASE through RESTORATION_PLUS + HOME_CARE + DONOR_PLUS

---

### A16. Durable Medical Equipment (`durable_medical`) — Row 24

- **Type:** Per-year age+SI percentage
- **Excel Row:** 24
- **Accumulation Base:** BASE through RESTORATION_PLUS + HOME_CARE + SPOUSE_PROTECT (skips DONOR_PLUS)

| Age Band | Rate |
|----------|------|
| ≤45 | ~10% |
| >45 | ~15% |

---

### A17. Tenure Wise (`tenure_wise`) — Row 25

- **Type:** Percentage by tenure × SI
- **Excel Row:** 25
- **Description:** Additional cover loading for 3+ year policies
- **Accumulation Base:** BASE through DURABLE_MEDICAL + DONOR_PLUS

---

### A18. Smart Select Network Discount (`smart_select`) — Row 26

- **Type:** Flat −15% (discount)
- **Excel Row:** 26
- **Rate:** −15% of accumulated base (skips HOME_CARE in base)
- **Accumulation Base:** BASE through TENURE_WISE (skips HOME_CARE)
- **isDiscount:** true
- **Params:** None

---

### A19. Incentivise Good Health (`good_health`) — Row 27

- **Type:** Flat % (currently 0%)
- **Excel Row:** 27
- **Rate:** 0% — reserved for future health rewards programme
- **Params:** None

---

### A20. Per Claim Deductible (`per_claim_deductible`) — Row 28

- **Type:** Percentage discount by deductible amount × SI
- **Excel Row:** 28
- **Accumulation Base:** FULL_THROUGH_R27
- **isDiscount:** true
- **Param1:** Deductible Amount

| Deductible | Rate |
|-----------|------|
| ₹15,000 | Lower discount % |
| ₹25,000 | Higher discount % |

> **Mutual Exclusion:** Cannot be combined with `aggregate_deductible`

---

### A21. Aggregate Deductible (`aggregate_deductible`) — Row 29

- **Type:** Percentage discount by deductible × SI
- **Excel Row:** 29
- **Accumulation Base:** FULL_THROUGH_R27 + PER_CLAIM_DEDUCTIBLE
- **isDiscount:** true
- **Param1:** Deductible Amount

| Deductible | Rate |
|-----------|------|
| ₹25,000 | ~12% |
| ₹50,000 | Higher |
| ₹1,00,000 | Highest |

> **Mutual Exclusion:** Cannot be combined with `per_claim_deductible`

---

### A22. Co-Payment (`co_pay`) — Row 30

- **Type:** Percentage discount by co-pay % × plan co-pay table
- **Excel Row:** 30
- **Accumulation Base:** FULL_THROUGH_R27 + AGGREGATE_DEDUCTIBLE
- **isDiscount:** true
- **Param1:** Co-Pay %

| Co-Pay Rate | Discount |
|------------|---------|
| 5% | Small discount |
| 10% | Moderate |
| 15% | ... |
| 20% | ... |
| 25% | ... |
| 30% | ... |
| 40% | Large discount |
| 50% | Larger |
| 60% | Largest |

Rates vary by plan (OMNIBUS / SENIOR / SUB_STANDARD co-pay tables).

---

## Section B — Flat and Member-Level Covers (Rows 31–51)

These covers have fixed INR amounts or scale linearly with member count. They do not use the accumulation-base mechanism.

### Complete Reference Table

| Row | Cover ID | Cover Name | Type | Amount / Rate | Key Params |
|-----|----------|-----------|------|--------------|-----------|
| 31 | `child_protect` | Child Protect | Flat | ₹100 | None |
| 32 | `daily_hospital_cash` | Daily Hospital Cash | Per member × age band | ₹/day table | Cash/Day: ₹500–₹9,000; member's age band |
| 33 | `convalescence` | Convalescence Benefit | Flat lookup | ₹137–₹2,739 | Amount (₹5K/10K/20K), Trigger (3/5/10 days) |
| 34 | `compassionate` | Compassionate Benefit | Flat lookup | ₹10 or ₹20 | Amount (₹25K/₹50K) |
| 35 | `personal_accident` | Personal Accident | Per adult, flat lookup | ₹700–₹7,000+ | Coverage Amount (₹10L–₹1Cr) |
| 36 | `air_ambulance` | Air Ambulance | Flat | ₹433 | None |
| 37 | `fitness_plus` | Fitness Plus | Flat | ₹649 | None |
| 38 | `wellness_package` | Wellness Package | Flat | ₹0 | None (complimentary) |
| 39 | `second_opinion` | Second E-Opinion | Flat | ₹67 | None |
| 40 | `maternity_newborn` | Maternity & New Born | Flat lookup | ₹47,098 typical | Limit (₹50K/₹1L/₹2L), Waiting (9M/24M/36M/48M) |
| 41 | `post_delivery_care` | Post Delivery Care | Flat | ₹167 | None |
| 42 | `infertility` | Infertility Cover | Flat lookup | ₹5,544 typical | Limit (₹1L/₹2L), Waiting (9M/24M/36M/48M) |
| 43 | `surrogate_mother` | Surrogate Mother | Flat | ₹908 | None (3yr+ policy) |
| 44 | `oocyte_donor` | Oocyte Donor | Flat | ₹845.75 | None (12 months) |
| 45 | `post_discharge_care` | Post Discharge Care | Flat | ₹167 | None |
| 46 | `adventure_sports` | Adventure Sports | Flat × adult count | ₹183/adult | None |
| 47 | `chronic_management` | Chronic Management | Per adult, lookup | ₹3,650 typical | Conditions (1/2/3) |
| 48 | `female_vaccination` | Female Vaccination | Flat × female count | ₹3,333/female | None |
| 49 | `pru_health_specialist` | Pru Health Specialist | Flat | ₹83 | None |
| 50 | `advance_health_checkup` | Advance Health Check-up | Flat × adult count | ₹975 (Basic) | Tier: Advance/Basic |
| 51 | `cashless_opd` | Cashless OPD | Flat lookup | ₹2,917 typical | OPD Limit (₹2.5K/₹5K/₹10K) |

---

## Section C — Pre-Calculated Covers (Rows 54–58)

### C1. Critical Illness (`critical_illness`) — Row 54

- **Type:** Per adult member, rate per mille × coverage amount
- **Excel Row:** 54
- **Adults Only:** Yes (members aged 18+)
- **Param1:** Coverage Amount

| Coverage Amount | Notes |
|----------------|-------|
| ₹5,00,000 | Minimum |
| ₹10,00,000 | Standard |
| ₹20,00,000 | Enhanced |
| ₹30,00,000 | High |
| ₹50,00,000 | Maximum |

Rate per mille lookup by each member's age band:

```
premium = ratePerMil × coverage / 1000
```

For a 35yo + 32yo couple with ₹10L CI:
```
= CRITICAL_ILLNESS_RATE[31] × 10,00,000 / 1000
+ CRITICAL_ILLNESS_RATE[31] × 10,00,000 / 1000
```

---

### C2. Maternity Fixed Benefit (`maternity_fixed`) — Row 55

- **Type:** Flat lookup by limit × waiting period
- **Excel Row:** 55
- **Description:** Fixed benefit maternity (requires 3yr+ single premium policy)
- **Param1:** Benefit Amount (₹50,000 / ₹1,00,000)
- **Param2:** Waiting Period

| Waiting Period | Notes |
|---------------|-------|
| 0 Month | Immediate coverage (highest premium) |
| 3 Months | Short wait |
| 6 Months | Medium wait |
| 9 Months | Standard wait (lower premium) |

---

### C3. Cancer Booster (`cancer_booster`) — Row 57

- **Type:** Per-year age+SI factor applied to R57_BASE
- **Excel Row:** 57
- **Rate:** ~2.5% (young) / ~4.5% (senior) — varies by SI
- **Accumulation Base:** R57_BASE = FULL_THROUGH_R27 + CO_PAY

---

### C4. Annual Cancer Screening (`cancer_screening`) — Row 58

- **Type:** Flat INR by age band per year
- **Excel Row:** 58
- **Description:** Annual cancer screening benefit

| Age Band | Annual Amount |
|----------|--------------|
| 18–35 | ~₹227 |
| 36–50 | Higher |
| 51+ | Highest |

---

## Section D — Post Covers (Rows 52, 53, 56)

### D1. Prudential Healthy Loading (`prudential_healthy`) — Row 52

- **Type:** 0.75% of full accumulated base
- **Excel Row:** 52
- **Accumulation Base:** R52_BASE = everything through rows 7–30 + 31–51 + 54 + 57 + 58
- **Params:** None

---

### D2. Premium Return (`premium_return`) — Row 53

- **Type:** 15% of full accumulated base including Row 52
- **Excel Row:** 53
- **Accumulation Base:** R53_BASE = R52_BASE + PRUDENTIAL_HEALTHY
- **Params:** None
- **Description:** Returns a portion of premium on claim-free years

---

### D3. Enhanced Geographical Scope (`enhanced_geo`) — Row 56

- **Type:** Percentage by geography
- **Excel Row:** 56
- **Availability:** Global plans only
- **Accumulation Base:** R56_BASE = R57_BASE + flat covers + CI + maternity fixed + prudential healthy + premium return + cancer
- **Param1:** Coverage Geography

| Geography | Rate |
|-----------|------|
| Worldwide excl. USA & Canada | ~16.67% |
| Asia excluding India | Lower |
| Europe | Intermediate |
| Worldwide incl. USA & Canada | Highest |

---

## Section E — UW Loading (Row 59)

### Underwriting Loading (`uw_loading`) — Row 59

- **Type:** Percentage applied by underwriter
- **Excel Row:** 59
- **Description:** Per-case additional charge for sub-standard health risks
- **Basis:** R59_BASE — covers through rows 4–30 + select flat covers + CI + post covers
- **Rate:** Set by underwriter, entered as a decimal (e.g., 0.25 = 25%)

**Base includes:**
- Rows 4–30 (all accumulating covers + discounts through co-pay)
- Child Protect, Daily Hospital Cash, Convalescence, Compassionate
- Air Ambulance, Second Opinion, Adventure Sports, Chronic Management
- Pru Health Specialist, Critical Illness, Prudential Healthy
- Premium Return, Enhanced Geo, Cancer Booster

**Base excludes (intentionally):**
- Personal Accident (separate PA product)
- Maternity covers
- Female Vaccination, Oocyte Donor, Surrogate Mother

---

## Section F — Standalone Discounts (Rows 64–72)

These discounts are applied to `totalBeforeDiscount` (after all covers and UW loading), capped at 30%.

| Discount ID | Name | Rate | Notes |
|-------------|------|------|-------|
| `disc_employee` | Employee / Affiliate | −10% | Cannot combine with `disc_commission_lieu` |
| `disc_nri` | NRI | −15% | For Non-Resident Indians |
| `disc_auto_debit` | Auto-Debit | −2.5% | Auto-debit payment setup |
| `disc_commission_lieu` | In Lieu of Commission | −15% | Cannot combine with `disc_employee` |
| `disc_gmc` | Corporate GMC | −5% | Group Medical Cover policyholders |
| `disc_cibil` | CIBIL Score | Up to −15% | Parameterised by CIBIL band |
| `disc_tenure` | Multi-Year | 0–15% stepped | Single Premium, 2–5 year only |
| `disc_multi_member` | Multi-Member | −5% or −10% | 2–3 members: 5%; 4+: 10% |

### CIBIL Score Discount Bands

| CIBIL Score | Discount Rate |
|-------------|--------------|
| ≤700 | Lowest |
| 701–750 | ~2.5% |
| 751–800 | Higher |
| 801–849 | Higher |
| ≥850 | Up to 15% |

---

## Sum Insured Options

### Domestic Plans (PHI Basic, Flagship 1 & 2)

| Amount | INR |
|--------|-----|
| ₹2 Lakh | 2,00,000 |
| ₹3 Lakh | 3,00,000 |
| ₹4 Lakh | 4,00,000 |
| **₹5 Lakh** | **5,00,000** |
| ₹7.5 Lakh | 7,50,000 |
| **₹10 Lakh** | **10,00,000** |
| ₹15 Lakh | 15,00,000 |
| ₹20 Lakh | 20,00,000 |
| **₹25 Lakh** | **25,00,000** |
| ₹30 Lakh | 30,00,000 |
| ₹40 Lakh | 40,00,000 |
| **₹50 Lakh** | **50,00,000** |
| ₹75 Lakh | 75,00,000 |
| **₹1 Crore** | **1,00,00,000** |

Flagship 2 additionally offers ₹1.5Cr and ₹2Cr.

### POSP Plan

| Amount |
|--------|
| ₹2L, ₹3L, ₹4L, ₹5L only |

### Senior / Sub-Standard Plans

| Amount |
|--------|
| ₹5L, ₹7L, ₹10L, ₹15L, ₹20L, ₹25L, ₹50L, ₹75L, ₹1Cr |

### Global Plans

| Amount |
|--------|
| ₹50L, ₹75L, ₹1Cr, ₹2Cr, ₹3Cr |

---

## Zone Factors Table

| Zone | Label | Factor | Typical Cities |
|------|-------|--------|---------------|
| Zone 1 | Metro | 1.63 | Mumbai, Delhi, Bengaluru, Chennai, Kolkata, Hyderabad |
| Zone 2 | Major | 1.49 | Pune, Ahmedabad, Jaipur, Lucknow, Chandigarh |
| Zone 3 | Tier-2 | 1.26 | Nagpur, Bhopal, Patna, Indore, Vadodara |
| Zone 4 | Rural | 1.00 | All other locations |
| Pan India | Blended | 1.15 | Senior and Sub-Standard plan rates |

Pincodes are mapped to zones via a lookup table in the desktop application. When a 6-digit pincode is entered, the city and zone are auto-detected.

---

*Next: [API Reference](./04-api-reference.md)*
