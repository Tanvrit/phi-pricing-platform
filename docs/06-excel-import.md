# Excel Import Guide

The Rate Calculator platform can import actuarial rate tables from the Excel workbook `Rate_Calculator_v7.0.xlsm`. This document explains the expected Excel format, how to upload new rates, and how to validate the import.

## Table of Contents
1. [Overview](#1-overview)
2. [When to Import](#2-when-to-import)
3. [Excel Workbook Format](#3-excel-workbook-format)
4. [Sheet: PHI_Basic — Base Rate Table](#4-sheet-phi_basic--base-rate-table)
5. [Sheet: Calculator — Cover Rate Rows](#5-sheet-calculator--cover-rate-rows)
6. [Sheet: Ref — Reference Tables](#6-sheet-ref--reference-tables)
7. [Importing via the Desktop App](#7-importing-via-the-desktop-app)
8. [Importing via the API](#8-importing-via-the-api)
9. [Seeding Built-in Data](#9-seeding-built-in-data)
10. [Validation After Import](#10-validation-after-import)
11. [Troubleshooting Import Failures](#11-troubleshooting-import-failures)
12. [Rate Table Update Process](#12-rate-table-update-process)

---

## 1. Overview

The Rate Calculator has two rate data strategies:

| Strategy | Class | Description |
|----------|-------|-------------|
| Hard-coded (offline) | `LocalRateDataProvider` | Rates compiled into the `:shared` module from `RateTables.kt`. No DB needed. Always available. |
| Database-backed | `RateDataProviderImpl` | Rates stored in PostgreSQL, importable from Excel. Allows updates without redeployment. |

The **import process** populates the database tables so that `RateDataProviderImpl` can serve the same rates as `LocalRateDataProvider`.

By default, the API server uses `LocalRateDataProvider`. After an import, you can switch to `RateDataProviderImpl` by modifying `Routing.kt` to use the database-backed provider.

---

## 2. When to Import

Import new rates when:
- IRDAI approves revised actuarial rates
- A new plan variant is launched with different base rates
- Cover rates (add-on loadings) are revised by the actuarial team
- Member-level rate tables (Daily Hospital Cash, Critical Illness) are updated

**Do not** modify `RateTables.kt` directly for production updates — use the import pipeline instead. Manual edits to `RateTables.kt` require a rebuild and redeployment of the `:shared` module.

---

## 3. Excel Workbook Format

The importer is designed for **`Rate_Calculator_v7.0.xlsm`** and compatible versions. The workbook must contain the following sheets:

| Sheet Name | Contents |
|------------|----------|
| `PHI_Basic` | Base premium rates by age band, family type, zone, SI |
| `Calculator` | Cover loading rates (rows 7–72) |
| `Ref` | Reference tables: age bands, zone factors, family type codes |
| `Cover_Rates` | (if present) Flat INR amounts for flat covers |
| `Member_Rates` | (if present) Per-member rate tables (DHC, CI, Chronic Mgmt) |

If your workbook uses different sheet names, update `ExcelImporter.kt` accordingly.

---

## 4. Sheet: PHI_Basic — Base Rate Table

### Column Layout

The PHI_Basic sheet organises rates in a 3D structure:

```
Rows:    Age bands (one per row, ~15 rows per family-type block)
Columns: Sum Insured amounts (one per column)
Blocks:  Family type × Zone combinations
```

#### Expected Column Headers (Row 1)

```
Age Band | ₹2L | ₹3L | ₹4L | ₹5L | ₹7.5L | ₹10L | ₹20L | ₹25L | ₹30L | ₹40L | ₹50L | ₹75L | ₹1Cr
```

#### Expected Row Layout (per family-type × zone block)

```
Row N:   [Family Type label]   [Zone label]
Row N+1: 5  (age band min)     [rate] [rate] [rate] ...
Row N+2: 18                    [rate] [rate] [rate] ...
Row N+3: 26                    [rate] [rate] [rate] ...
...
Row N+15: 86                   [rate] [rate] [rate] ...
```

#### Zone Ordering Within Each Family-Type Block

Zones are listed in the order: Zone 4, Zone 3, Zone 2, Zone 1, Pan India.

Zone 4 rates are the base; the importer reads all 5 zone columns and stores them separately. At calculation time, the zone factor is applied as a multiplier (alternately, the exact zone rate is stored and used directly).

#### Sample PHI Basic – 1A – Zone 4 Rates (from RateTables.kt)

| Age Band Min | ₹5L | ₹10L | ₹25L | ₹50L | ₹1Cr |
|-------------|-----|------|------|------|------|
| 5 | 1,800 | 2,023 | 2,798 | 3,223 | 4,020 |
| 18 | 3,875 | 4,417 | 6,287 | 7,307 | 9,227 |
| 26 | 3,875 | 4,417 | 6,287 | 7,307 | 9,227 |
| 31 | 3,875 | 4,417 | 6,287 | 7,307 | 9,227 |
| 36 | 4,598 | 5,253 | 7,528 | 8,770 | 11,098 |
| 41 | 5,472 | 6,265 | 9,032 | 10,532 | 13,358 |
| 46 | 6,923 | 7,922 | 11,403 | 13,297 | 16,853 |
| 51 | 8,595 | 9,863 | 14,273 | 16,673 | 21,183 |
| 56 | 11,380 | 13,092 | 19,053 | 22,295 | 28,395 |
| 61 | 15,347 | 17,692 | 25,862 | 30,310 | 38,670 |
| 66 | 19,932 | 23,015 | 33,742 | 39,575 | 50,547 |
| 71 | 27,270 | 31,525 | 46,338 | 54,398 | 69,552 |
| 76 | 34,410 | 39,805 | 58,598 | 68,820 | 88,038 |
| 81 | 42,982 | 49,748 | 73,310 | 86,132 | 110,232 |
| 86 | 42,982 | 49,748 | 73,310 | 86,132 | 110,232 |

---

## 5. Sheet: Calculator — Cover Rate Rows

The Calculator sheet contains one or more columns (one per plan) and many rows (one per cover). The importer reads specific rows by their row number.

### Cover Row Mapping

| Excel Row | Cover ID | Rate Type |
|-----------|----------|-----------|
| Row 7 | `day1_instant` | % by coverage multiple × SI |
| Row 8 | `loyalty_bonus` | % by bonus type × age band |
| Row 9 | `double_cover_7yr` | Flat 3.25% |
| Row 10 | `chronic_instant` | % by condition count |
| Row 11 | `consumables_list1` | Flat 5% |
| Row 12 | `ped_waiting` | % by reduction type |
| Row 13 | `specific_illness_waiting` | % by age band |
| Row 14 | `modern_treatment_plus` | % by plan |
| Row 15 | `room_rent_mod` | % by room type × plan |
| Row 16 | `disease_sublimit` | % by plan × SI |
| Row 17 | `pre_post_hosp` | Flat 10% |
| Row 18 | `consumable_plus` | Flat 9% |
| Row 19 | `home_care` | Flat ₹167 INR |
| Row 20 | `infinite_claim` | % by SI |
| Row 21 | `restoration_plus` | % by age band × SI |
| Row 22 | `donor_plus` | Flat ₹417 INR |
| Row 23 | `spouse_protect` | % by SI |
| Row 24 | `durable_medical` | % by age × SI |
| Row 25 | `tenure_wise` | % by tenure × SI |
| Row 26 | `smart_select` | Flat −15% |
| Row 27 | `good_health` | Flat 0% |
| Row 28 | `per_claim_deductible` | % by deductible × SI |
| Row 29 | `aggregate_deductible` | % by deductible × SI |
| Row 30 | `co_pay` | % by co-pay % × plan table |
| Row 31 | `child_protect` | Flat ₹100 |
| Row 36 | `air_ambulance` | Flat ₹433 |
| Row 37 | `fitness_plus` | Flat ₹649 |
| Row 38 | `wellness_package` | Flat ₹0 |
| Row 39 | `second_opinion` | Flat ₹67 |
| Row 41 | `post_delivery_care` | Flat ₹167 |
| Row 43 | `surrogate_mother` | Flat ₹908 |
| Row 44 | `oocyte_donor` | Flat ₹845.75 |
| Row 45 | `post_discharge_care` | Flat ₹167 |
| Row 46 | `adventure_sports` | Flat ₹183 per adult |
| Row 48 | `female_vaccination` | Flat ₹3,333 per female |
| Row 49 | `pru_health_specialist` | Flat ₹83 |
| Row 52 | `prudential_healthy` | 0.75% of base |
| Row 53 | `premium_return` | 15% of base |
| Row 57 | `cancer_booster` | % by age × SI |
| Row 58 | `cancer_screening` | Flat by age band |

**Rows 54, 55 (Critical Illness, Maternity Fixed):** These are parameterised lookups stored in a separate member_rates sub-table, not read directly from the Calculator sheet row.

**Rows 64–70 (Discounts):** Discount rates are stored separately in a discounts lookup table.

---

## 6. Sheet: Ref — Reference Tables

The Ref sheet contains metadata tables referenced during import:

| Cell Range | Contents |
|-----------|----------|
| I2:J16 | Age bands (min age, label) |
| L:N | Family type codes and member counts |
| P:Q | Zone names and factors |
| R:S | Sum insured options by plan type |

The importer reads these ranges to build the age-band and family-type lookup structures.

---

## 7. Importing via the Desktop App

### Step-by-Step

1. **Ensure the server is running** on port 9090:
   ```bash
   PORT=9090 ./gradlew :server:run
   ```

2. **Ensure PostgreSQL is running** at `localhost:5432/rate_calculator`

3. Launch the desktop app:
   ```bash
   ./gradlew :desktop:run
   ```

4. Navigate to the **Import** tab (bottom navigation bar)

5. Click **"Browse..."** and select your Excel file (`.xlsx` or `.xlsm`)

6. Click **"Import"**

7. A success or error message appears:
   - Success: `"Excel data imported successfully"`
   - Failure: error detail shown in a banner

### File Size

Typical Excel workbook size is 200–800 KB. The upload is handled via multipart form-data. Very large workbooks (>5 MB) may take a few seconds to process.

---

## 8. Importing via the API

The import endpoint accepts a multipart file upload:

```bash
curl -X POST http://localhost:9090/api/import/upload \
  -F "file=@/path/to/Rate_Calculator_v8.0.xlsm"
```

**Response 200:**
```json
{ "message": "Excel data imported successfully" }
```

**Response 400:**
```json
{ "error": "No file provided" }
```

If the file contains unexpected structure, the import may throw a runtime exception (500). Check server logs for details.

---

## 9. Seeding Built-in Data

The built-in rates (identical to `RateTables.kt`) can be seeded to the database without uploading a file:

**Via API:**
```bash
curl -X POST http://localhost:9090/api/import/seed
```

**Via Desktop App:** Click **"Seed Built-in Data"** on the Import tab.

This is the safest way to ensure the database contains correct rates before switching the server to use `RateDataProviderImpl`.

---

## 10. Validation After Import

After importing, verify correctness with spot-check calculations:

### Spot Check 1 — Single Adult, Base Only

```bash
curl -X POST http://localhost:9090/api/quotes/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "planId": "PHI_BASIC",
    "primaryAge": 35,
    "sumInsured": 1000000,
    "familyType": "1A",
    "zone": "Zone 4",
    "tenure": "ONE_YEAR",
    "paymentMode": "ANNUAL",
    "paymentTenure": "ONE_YEAR",
    "members": [{"memberId": 1, "age": 35, "relationship": "Self", "gender": "M"}],
    "selectedCovers": [],
    "selectedDiscounts": [],
    "uwLoadingFactor": 0.0,
    "maxDiscountCap": 0.30
  }'
```

Expected `basePremiumTotal`: **₹4,417** (1A, Zone 4, age band 31–35, ₹10L)

### Spot Check 2 — Zone Factor

Same as Spot Check 1 but change `zone` to `"Zone 1"`.

Expected: `4,417 × 1.63 ≈ ₹7,200`

### Spot Check 3 — Family Floater

Change `familyType` to `"2A"` and add a spouse member.

Expected `basePremiumTotal` should be higher than the 1A result, consistent with the 2A rate table.

### Spot Check 4 — Consumables List I (5%)

Add `consumables_list1` to `selectedCovers`. Verify that the cover premium is approximately 5% of the base premium.

### Verify Discount Cap

Add multiple discounts that sum to >30%. Verify that `totalDiscountAmount` does not exceed 30% of `totalBeforeDiscount`.

---

## 11. Troubleshooting Import Failures

### "No file provided"

The multipart request did not contain a file part. Ensure the `curl` command uses `-F "file=@..."` (form-data), not `-d @...` (raw body).

### Server returns 500

Check server logs:
```bash
./gradlew :server:run 2>&1 | grep -i error
```

Common causes:
- Excel sheet name mismatch (expected `PHI_Basic`, got `PHI Basic`)
- Missing column headers in the PHI_Basic sheet
- Unexpected number format (e.g., values stored as text, not numbers)
- PostgreSQL connection failure

### Database connection failure

Ensure PostgreSQL is running:
```bash
pg_isready -h localhost -p 5432 -U rate_admin -d rate_calculator
```

If the database does not exist, create it:
```bash
psql -U postgres -c "CREATE DATABASE rate_calculator;"
psql -U postgres -c "CREATE USER rate_admin WITH PASSWORD 'rate123';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE rate_calculator TO rate_admin;"
```

### Rates look incorrect after import

Compare the imported rate with the known correct value from `RateTables.kt`. If they differ, the Excel workbook may have been edited without updating the corresponding Kotlin constants. Run the seed command to restore the known-good baseline:

```bash
curl -X POST http://localhost:9090/api/import/seed
```

---

## 12. Rate Table Update Process

When the actuarial team delivers a new rate file:

1. **Receive the new Excel file** — confirm sheet names match the expected format
2. **Backup the current rates** — export or snapshot the current `rate_entries` DB table
3. **Run the import** via API or desktop
4. **Run spot checks** (see [Section 10](#10-validation-after-import))
5. **Compare** new rates with the previous version for expected changes:
   - Premium should be higher for revised upward rates
   - Discount amounts should be consistent with the documented rates
6. **Update `RateTables.kt`** — if the rates are intended to be permanent, update the hard-coded Kotlin tables to match. This ensures the offline `LocalRateDataProvider` stays in sync.
7. **Rebuild and redeploy** the `:shared` and `:server` modules:
   ```bash
   ./gradlew :shared:jvmJar
   PORT=9090 ./gradlew :server:run
   ```
8. **Document the rate version** in your version control commit message, including the effective date and IRDAI filing reference.

---

*Next: [Business Rules](./07-business-rules.md)*
