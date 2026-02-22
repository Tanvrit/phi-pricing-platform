# Desktop Calculator Guide

The Rate Calculator Desktop application is a Compose Desktop UI for actuaries, underwriters, and product managers to calculate health insurance premiums interactively.

## Table of Contents
1. [Launching the Application](#1-launching-the-application)
2. [Navigation Overview](#2-navigation-overview)
3. [Calculator Screen — Input Form](#3-calculator-screen--input-form)
4. [Pincode to Zone Auto-Detection](#4-pincode-to-zone-auto-detection)
5. [Family Type Auto-Derivation](#5-family-type-auto-derivation)
6. [Optional Covers Section](#6-optional-covers-section)
7. [Discounts Section](#7-discounts-section)
8. [Calculating Premium](#8-calculating-premium)
9. [Reading the Results](#9-reading-the-results)
10. [Tenure Comparison Table](#10-tenure-comparison-table)
11. [Year-wise Breakdown](#11-year-wise-breakdown)
12. [Cover Details Panel](#12-cover-details-panel)
13. [Configurator Screen](#13-configurator-screen)
14. [Import Screen](#14-import-screen)
15. [Tips and Workflow](#15-tips-and-workflow)

---

## 1. Launching the Application

### Prerequisites

- JDK 17 or 21 installed
- Gradle available (or use the wrapper `./gradlew`)
- PostgreSQL is optional — the desktop app calculates fully offline

### Start Command

```bash
cd /Users/viveksingh/Developer/yogesh/rate
./gradlew :desktop:run
```

The application opens as a native desktop window. It does not require the API server to be running for calculations. The server is only needed if you want to save quotes to the database.

### First Launch

On first launch, the app loads all plans from `LocalRateDataProvider`. No network call is made. The plan dropdown populates immediately.

---

## 2. Navigation Overview

The application has a bottom navigation bar with three destinations:

```
┌──────────────────────────────────────────────────────────┐
│                    Rate Calculator                        │
│                                                          │
│   [main content area — changes per screen]               │
│                                                          │
├──────────────┬─────────────────┬────────────────────────┤
│  Calculator  │   Configurator  │       Import            │
└──────────────┴─────────────────┴────────────────────────┘
```

| Tab | Purpose |
|-----|---------|
| Calculator | Main premium calculation form and results |
| Configurator | Plan and cover management (view/edit plans) |
| Import | Upload Excel rate tables to update rates in DB |

---

## 3. Calculator Screen — Input Form

The screen is split into two columns:

```
┌──────────────────────┬──────────────────────────────────┐
│   LEFT: Inputs       │   RIGHT: Results                 │
│   (scrollable)       │   (scrollable)                   │
│                      │                                  │
│  Policy Details      │  Premium Breakdown               │
│  Optional Covers     │   - Tenure Comparison Table      │
│  Discounts           │   - Summary                      │
│  [Calculate]         │   - Year-wise Breakdown          │
│                      │   - Cover Details                │
│                      │   - Discount Breakdown           │
└──────────────────────┴──────────────────────────────────┘
```

### Policy Details Card — All Input Fields

#### Plan

Dropdown listing all 14 available plans. Selecting a plan:
- Updates the available Sum Insured options (e.g., POSP has only ₹2L–₹5L)
- Updates the available zones (Senior/Sub-Standard have only Pan India and Zone 1)
- Filters the Optional Covers list to only shows covers allowed for that plan

#### Primary Age

Integer input (5–99). This is the age of the primary insured (Self).
- Drives age-band lookup for each policy year
- For multi-year policies, the engine increments this by 1 each year

#### Spouse Age (opt)

Optional integer. When filled:
- Creates a `Member` with `relationship = "Spouse"`
- Is used in member-level cover calculations (Daily Hospital Cash, Critical Illness, etc.)
- Causes family type to include an adult (e.g., `1A` becomes `2A`)

#### Child Ages (comma-separated)

Example: `7, 10` creates two child members aged 7 and 10. Children have `relationship = "Son"` or `"Daughter"` depending on position (can be updated in the `members` array via API).

#### Family Type (auto-derived, display only)

Shows: **"Family Type: 2A1C  (2 adults + 1 child)"**

Derived automatically from the age inputs above. Not directly editable in the UI — change it by adjusting the member inputs.

#### Sum Insured

Dropdown showing available sum insureds for the selected plan. Values displayed as `₹ X,XX,XXX`.

Example dropdown values for PHI Basic:
```
₹ 2,00,000
₹ 3,00,000
₹ 4,00,000
₹ 5,00,000
₹ 7,50,000
₹ 10,00,000
₹ 20,00,000
₹ 25,00,000
₹ 30,00,000
₹ 40,00,000
₹ 50,00,000
₹ 75,00,000
₹ 1,00,00,000
```

#### Pincode

6-digit numeric input. Triggers auto-detection of city and zone after all 6 digits are entered (see [Section 4](#4-pincode-to-zone-auto-detection)).

#### Zone

Dropdown with 5 options:
- Zone 1 (factor 1.63)
- Zone 2 (factor 1.49)
- Zone 3 (factor 1.26)
- Zone 4 (factor 1.00)
- Pan India (factor 1.15)

Auto-populated when a pincode is detected. Can be overridden manually.

#### Policy Tenure and Payment Tenure

Two separate dropdowns:
- **Policy Tenure:** How many years the policy covers (1–5 years)
- **Payment Tenure:** How many years premiums are paid (1–5 years)

Both use the same options: `1 Year`, `2 Years`, `3 Years`, `4 Years`, `5 Years`.

For a limited-pay plan: set Policy Tenure to `5 Years` and Payment Tenure to `3 Years`.

#### Payment Mode

| Option | Instalment Loading |
|--------|-------------------|
| Single Premium | 0% loading, tenure discounts apply |
| Annual | 0% loading |
| Half-Yearly | 2% loading, 2 payments/year |
| Quarterly | 4% loading, 4 payments/year |
| Monthly | 6% loading, 12 payments/year |

#### UW Loading %

Numeric input for underwriter-applied additional loading. Enter as a percentage (e.g., `25` = 25% extra loading). Defaults to `0`.

This field is visible to underwriters for pricing sub-standard risks without changing the plan.

---

## 4. Pincode to Zone Auto-Detection

When exactly 6 digits are entered in the Pincode field:

1. The app looks up the pincode in the local zone mapping table
2. If found, the city name appears to the right of the pincode field and the Zone dropdown is automatically set
3. If not found, no auto-selection happens and the city field remains empty

**Example:**
```
Pincode: 400001  →  City: "Mumbai"  →  Zone: Zone 1 (auto-set)
Pincode: 110001  →  City: "New Delhi"  →  Zone: Zone 1 (auto-set)
Pincode: 500001  →  City: "Hyderabad"  →  Zone: Zone 1 (auto-set)
Pincode: 600001  →  City: "Chennai"  →  Zone: Zone 1 (auto-set)
Pincode: 411001  →  City: "Pune"  →  Zone: Zone 2 (auto-set)
Pincode: 462001  →  City: "Bhopal"  →  Zone: Zone 3 (auto-set)
```

You can always override the auto-detected zone by selecting a different value from the Zone dropdown.

---

## 5. Family Type Auto-Derivation

The Family Type code is derived from the member input fields:

| Primary Age | Spouse Age | Child Ages | Family Type |
|-------------|------------|-----------|-------------|
| 35 | (empty) | (empty) | `1A` — Individual |
| 35 | 32 | (empty) | `2A` — Couple floater |
| 35 | 32 | `7` | `2A1C` — Family of 3 |
| 35 | 32 | `7, 10` | `2A2C` — Family of 4 |
| 35 | 32 | `7, 10, 5` | `2A3C` — Family of 5 |
| 35 | (empty) | `7` | `1A1C` — Single parent + 1 child |
| 35 | (empty) | `7, 10` | `1A2C` — Single parent + 2 children |

The derived family type is displayed below the child ages field:
```
Family Type: 2A1C  (2 adults + 1 child)
```

The displayed family type is automatically used in the base premium lookup and all member-level cover calculations.

---

## 6. Optional Covers Section

The Optional Covers section appears below Policy Details and shows all covers available for the selected plan. Each cover row contains:

```
[checkbox]  Cover Name
            Cover description (grey subtitle)
            [if checked and has params: dropdown(s) appear]
```

### Cover Row Example — Daily Hospital Cash

```
[✓]  Daily Hospital Cash
     Fixed daily cash benefit during hospitalisation

     [Cash/Day (₹): 1000 ▾]
```

When you check a cover:
- If it has `param1`, the first dropdown auto-populates with the first option
- If it has `param2`, a second dropdown appears alongside

Covers are colour-coded:
- Standard covers: no special colour
- Discount-type covers (Smart Select, Co-Pay, Per Claim Deductible, Aggregate Deductible, Good Health): displayed in discount colour

### Cover Param Dropdowns

Param options appear indented 40dp from the left (past the checkbox), in a row:

```
[✓]  Convalescence Benefit
     Lump sum payment for extended hospital stay

          [Benefit Amount (₹): 10000 ▾]  [Trigger (Days): More than 10 Days ▾]
```

---

## 7. Discounts Section

Discounts appear in a separate "Discounts" card below Optional Covers. The discount list includes:

| Discount | Notes |
|----------|-------|
| Employee / Affiliate Discount | −10%; mutually exclusive with Commission discount |
| NRI Discount | −15% |
| Auto-Debit Discount | −2.5% |
| Discount in Lieu of Commission | −15%; mutually exclusive with Employee discount |
| Corporate GMC Discount | −5% |
| CIBIL Score Discount | Up to −15%; requires CIBIL band selection |
| Multi-Year Discount | 0–15% stepped; only applies with Single Premium |
| Multi-Member Discount | −5% for 2–3 members, −10% for 4+ members |

All discounts are combined and capped at 30% of `totalBeforeDiscount`.

---

## 8. Calculating Premium

Click the **"Calculate Premium"** button at the bottom of the left column.

During calculation:
- The button shows a circular progress indicator and is disabled
- A `Loading` overlay may appear if the plan list is still loading

The engine runs synchronously in a coroutine on the ViewModel's scope. For typical inputs, calculation completes in under 50ms.

After calculation, the right column populates with results.

---

## 9. Reading the Results

The right column is titled "Premium Breakdown" and shows:

### Summary Card

```
Base Premium                      ₹ 7,144
Optional Covers                   ₹ 2,858
UW Loading (+)                    ₹ 0          [shown in orange if > 0]
Total Discounts                   ₹ -250       [shown in green/teal]
─────────────────────────────────────────────
Annual Premium                    ₹ 9,752      [highlighted in primary colour]
Instalment Loading (+)            ₹ 585        [shown if mode ≠ Annual/Single]
Per Instalment (×12)              ₹ 861
```

The **Annual Premium** row is the final premium after all covers, UW loading, and discounts — but before instalment loading. This is the headline number.

---

## 10. Tenure Comparison Table

When a calculation completes, the engine automatically re-runs for all 5 tenure options (1–5 years) using the same cover selections. The results are displayed in a comparison grid:

```
Premium Comparison — All Tenures

                    1yr      2yr      3yr      4yr      5yr
Base Premium      ₹7,144  ₹14,288  ₹22,147  ₹30,006  ₹38,680
Add-ons           ₹2,858   ₹5,715   ₹8,859  ₹12,002  ₹15,472
Discounts           ₹-250    ₹-501    ₹-776  ₹-1,050  ₹-1,352
──────────────────────────────────────────────────────────────
Annual Premium    ₹9,752  ₹19,502  ₹30,230  ₹40,958  ₹52,800  ← highlighted
──────────────────────────────────────────────────────────────
Monthly EMI (+6%) ₹ 861   ₹ 861    ₹ 891    ₹ 902    ₹ 930
Effective/Year    ₹9,752   ₹9,751   ₹10,077  ₹10,240  ₹10,560
```

**Row explanations:**

| Row | Description |
|-----|-------------|
| Base Premium | Sum of base premiums across all policy years for that tenure |
| Add-ons | Total optional cover premiums across all years |
| Discounts | Total discount amounts (may vary with tenure due to stepped tenure discount) |
| Annual Premium | Base + Add-ons + UW Loading − Discounts — the total cost for that tenure |
| Monthly EMI (+6%) | `Annual Premium × 1.06 ÷ (tenure years × 12)` — indicative monthly cost |
| Effective/Year | `Annual Premium ÷ tenure years` — useful for comparing multi-year value |

The "Annual Premium" row is highlighted with `primaryContainer` background. Lower `Effective/Year` means better value per year of coverage.

---

## 11. Year-wise Breakdown

Below the tenure comparison, the detailed breakdown for the **selected tenure** is shown.

```
Year-wise Breakdown

Year 1 — Age 35 (31 - 35)
  Base Premium           ₹ 7,144
  Covers / Add-ons       ₹ 2,858
  Subtotal               ₹ 10,002

Year 2 — Age 36 (36 - 40)
  Base Premium           ₹ 8,459
  Covers / Add-ons       ₹ 3,384
  Subtotal               ₹ 11,843
```

Notice the age band changes at year 2 (35→36 crosses the 36–40 band boundary), causing a premium step-up. This is the age-progressive nature of the pricing model.

---

## 12. Cover Details Panel

Shows the individual premium contribution of each selected cover:

```
Cover Details

Consumables List1                  ₹  357
Pre Post Hosp                      ₹  714
Daily Hospital Cash                ₹  876
Critical Illness                  ₹ 2,340
Smart Select          [green]     ₹ -3,215
Co Pay                [green]     ₹ -1,850
UW Loading                         ₹    0
```

Discount-type covers (Smart Select, Co-Pay, Per-Claim Deductible, Aggregate Deductible, Good Health) are shown in the discount colour to visually distinguish them from additive covers.

---

## 13. Configurator Screen

The Configurator tab (accessible from the bottom nav bar) allows viewing and editing plan configurations.

**Features:**
- View all plans with their settings (sum insureds, zones, family types, age limits)
- Edit plan properties
- Toggle plan active/inactive

Changes made here call `POST /api/plans` on the server (requires server to be running on port 9090). The desktop's local provider is unchanged — restart the app to pick up new plans from the server.

---

## 14. Import Screen

The Import screen allows uploading a new Excel rate workbook to update the rates stored in the PostgreSQL database.

### Steps to Import

1. Navigate to the **Import** tab
2. Click **"Browse..."** to select an `.xlsx` or `.xlsm` file
3. The file path is displayed in the text field
4. Click **"Import"** to upload

The file is sent to `POST /api/import/upload` on the server (port 9090 must be running).

### Built-in Seed

Alternatively, click **"Seed Built-in Data"** to populate the database with the rates extracted from `Rate_Calculator_v7.0.xlsm` (the same rates used by `LocalRateDataProvider`). This is equivalent to:

```bash
curl -X POST http://localhost:9090/api/import/seed
```

### After Import

After import, the server's `RateDataProviderImpl` uses the database rates. The desktop app continues to use `LocalRateDataProvider` (in-process) unless explicitly configured to call the server for rates.

---

## 15. Tips and Workflow

### Actuarial Workflow

1. Select the plan and enter the primary age, spouse age, and child ages
2. Choose sum insured and enter or detect the pincode/zone
3. Set tenure to `THREE_YEARS`, payment mode to `ANNUAL`
4. Enable relevant covers (e.g., Consumables List I, Pre-Post Hosp)
5. Enable relevant discounts (e.g., Auto-Debit 2.5%)
6. Click Calculate
7. Review the Tenure Comparison Table to see 1–5 year cost comparison
8. Review Year-wise Breakdown to verify age-band step-ups

### Checking Mutual Exclusion

If you select two mutually exclusive covers (e.g., `consumables_list1` + `consumable_plus`), the right panel shows:

```
[Error Banner]
Consumables Cover List I cannot be combined with Consumable Plus (List I–IV)
```

Deselect one of the conflicting covers and recalculate.

### Single Premium vs Annual

Setting Payment Mode to `Single Premium` activates the Multi-Year Discount (select it in the Discounts section). The stepped discount of 0%/7.5%/10%/12.5%/15% per year is then applied. For long-tenure customers, Single Premium with tenure discount often has a lower effective cost than Annual mode.

### Comparing Plans

To compare PHI Basic vs PHI Flagship 1:
1. Set all inputs for one plan, click Calculate, note the Annual Premium
2. Change only the Plan dropdown to the other plan, click Calculate
3. Compare the Annual Premium values

The Tenure Comparison Table updates for each calculation, showing all 5 tenures simultaneously.

---

*Next: [Excel Import Guide](./06-excel-import.md)*
