# API Reference

The Rate Calculator server exposes a JSON REST API on **port 9090**.

**Base URL:** `http://localhost:9090`

**Content-Type:** All requests and responses use `application/json` unless noted otherwise.

---

## Table of Contents
1. [Health Check](#1-health-check)
2. [Quotes — Calculate (No Save)](#2-quotes--calculate-no-save)
3. [Quotes — Calculate and Save](#3-quotes--calculate-and-save)
4. [Quotes — Retrieve by ID](#4-quotes--retrieve-by-id)
5. [Quotes — List All](#5-quotes--list-all)
6. [Plans — List All](#6-plans--list-all)
7. [Plans — Get by ID](#7-plans--get-by-id)
8. [Plans — Create / Update](#8-plans--create--update)
9. [Plans — Delete](#9-plans--delete)
10. [Covers — List All](#10-covers--list-all)
11. [Covers — Age Bands](#11-covers--age-bands)
12. [Covers — Family Types](#12-covers--family-types)
13. [Covers — Sum Insureds](#13-covers--sum-insureds)
14. [Import — Seed Built-in Data](#14-import--seed-built-in-data)
15. [Import — Upload Excel](#15-import--upload-excel)
16. [Buy Online Routes](#16-buy-online-routes)
17. [Common Data Schemas](#17-common-data-schemas)
18. [Error Handling](#18-error-handling)

---

## 1. Health Check

```
GET /health
```

Returns service status. Use to verify the server is running before making calculations.

**Response 200:**
```json
{
  "status": "ok",
  "service": "rate-calculator"
}
```

**curl:**
```bash
curl http://localhost:9090/health
```

---

## 2. Quotes — Calculate (No Save)

```
POST /api/quotes/calculate
```

Calculates premium for a given `QuoteRequest`. The result is returned immediately without persisting to the database.

### Request Body — `QuoteRequest`

```json
{
  "planId": "PHI_BASIC",
  "primaryAge": 35,
  "sumInsured": 1000000,
  "familyType": "2A1C",
  "zone": "Zone 1",
  "tenure": "THREE_YEARS",
  "paymentMode": "ANNUAL",
  "paymentTenure": "THREE_YEARS",
  "members": [
    { "memberId": 1, "age": 35, "relationship": "Self",   "gender": "M" },
    { "memberId": 2, "age": 32, "relationship": "Spouse", "gender": "F" },
    { "memberId": 3, "age": 7,  "relationship": "Son",    "gender": "M" }
  ],
  "selectedCovers": [
    { "coverId": "consumables_list1", "params": {} },
    { "coverId": "pre_post_hosp",     "params": {} },
    {
      "coverId": "daily_hospital_cash",
      "params": { "param1": "1000" }
    },
    {
      "coverId": "critical_illness",
      "params": { "param1": "500000" }
    }
  ],
  "selectedDiscounts": [
    { "discountId": "disc_auto_debit" }
  ],
  "uwLoadingFactor": 0.0,
  "maxDiscountCap": 0.30
}
```

### QuoteRequest Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `planId` | String | Yes | Plan identifier (see [Plans section](#6-plans--list-all)) |
| `primaryAge` | Int | Yes | Primary insured age (5–99) |
| `sumInsured` | Long | Yes | Sum insured in INR (e.g., 1000000 = ₹10L) |
| `familyType` | String | Yes | Family type code (1A, 2A, 2A1C, etc.) |
| `zone` | String | Yes | "Zone 1" / "Zone 2" / "Zone 3" / "Zone 4" / "Pan India" |
| `tenure` | String | Yes | Enum: ONE_YEAR, TWO_YEARS, THREE_YEARS, FOUR_YEARS, FIVE_YEARS |
| `paymentMode` | String | Yes | Enum: SINGLE_PREMIUM, MONTHLY, QUARTERLY, HALF_YEARLY, ANNUAL |
| `paymentTenure` | String | Yes | Same enum as tenure — payment frequency period |
| `members` | Array | Yes | List of insured members |
| `selectedCovers` | Array | No | Optional covers to include |
| `selectedDiscounts` | Array | No | Discounts to apply |
| `uwLoadingFactor` | Double | No | UW extra loading (0.0 = none, 0.25 = 25%) |
| `maxDiscountCap` | Double | No | Override discount cap (default 0.30 = 30%) |

#### Tenure Enum Values

| JSON Value | Years |
|-----------|-------|
| `ONE_YEAR` | 1 |
| `TWO_YEARS` | 2 |
| `THREE_YEARS` | 3 |
| `FOUR_YEARS` | 4 |
| `FIVE_YEARS` | 5 |

#### PaymentMode Enum Values

| JSON Value | Instalment Loading |
|-----------|-------------------|
| `SINGLE_PREMIUM` | 0% |
| `ANNUAL` | 0% |
| `HALF_YEARLY` | 2% |
| `QUARTERLY` | 4% |
| `MONTHLY` | 6% |

#### Member Object

```json
{
  "memberId": 1,
  "age": 35,
  "relationship": "Self",
  "gender": "M"
}
```

| Field | Values |
|-------|--------|
| `memberId` | 1-based integer |
| `age` | 5–99 |
| `relationship` | "Self", "Spouse", "Son", "Daughter" |
| `gender` | "M" or "F" |

#### CoverSelection Object

```json
{
  "coverId": "daily_hospital_cash",
  "params": {
    "param1": "1000",
    "param2": null
  }
}
```

See [Rate Tables Reference](./03-rate-tables.md) for valid `param1` and `param2` values per cover.

#### DiscountSelection Object

```json
{
  "discountId": "disc_cibil",
  "param": "751-800"
}
```

---

### Response 200 — `QuoteResult`

```json
{
  "requestId": "Q-1708600000000",
  "planId": "PHI_BASIC",
  "basePremiumTotal": 21432.87,
  "coverBreakdown": [
    {
      "coverId": "consumables_list1",
      "coverName": "Consumables List1",
      "yearlyPremiums": [714.43, 714.43, 714.43],
      "totalPremium": 2143.29,
      "rateApplied": null,
      "isDiscount": false
    },
    {
      "coverId": "smart_select",
      "coverName": "Smart Select",
      "yearlyPremiums": [-3214.93, -3214.93, -3214.93],
      "totalPremium": -9644.79,
      "isDiscount": true
    }
  ],
  "totalAddons": 8572.20,
  "uwLoadingAmount": 0.0,
  "totalBeforeDiscount": 30005.07,
  "discountBreakdown": [
    {
      "discountId": "disc_auto_debit",
      "discountName": "Auto Debit Discount",
      "rateApplied": -0.025,
      "amount": -750.13
    }
  ],
  "totalDiscountAmount": -750.13,
  "totalAfterDiscount": 29254.94,
  "instalmentLoadingAmount": 0.0,
  "instalmentPremium": 0.0,
  "instalmentCount": 1,
  "yearlyBreakdown": [
    {
      "year": 1, "age": 35, "ageBand": "31 - 35",
      "basePremium": 7144.31,
      "coverPremiums": { "consumables_list1": 357.22, "pre_post_hosp": 794.92 },
      "subtotal": 10001.69
    }
  ],
  "isValid": true,
  "validationErrors": []
}
```

### Response 422 (Validation Failure)

```json
{
  "requestId": "Q-...",
  "isValid": false,
  "validationErrors": [
    "Consumables Cover List I cannot be combined with Consumable Plus (List I–IV)"
  ]
}
```

**curl:**
```bash
curl -X POST http://localhost:9090/api/quotes/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "planId": "PHI_BASIC",
    "primaryAge": 35,
    "sumInsured": 1000000,
    "familyType": "1A",
    "zone": "Zone 1",
    "tenure": "ONE_YEAR",
    "paymentMode": "ANNUAL",
    "paymentTenure": "ONE_YEAR",
    "members": [
      {"memberId": 1, "age": 35, "relationship": "Self", "gender": "M"}
    ],
    "selectedCovers": [],
    "selectedDiscounts": [],
    "uwLoadingFactor": 0.0,
    "maxDiscountCap": 0.30
  }'
```

---

## 3. Quotes — Calculate and Save

```
POST /api/quotes
```

Same as `/calculate` but also persists the quote to PostgreSQL.

**Response 201 Created:**
```json
{
  "id": "QS-1708600001234",
  "result": { ... QuoteResult ... }
}
```

**Response 422** — if invalid, returns `QuoteResult` with `isValid: false` (not saved).

**curl:**
```bash
curl -X POST http://localhost:9090/api/quotes \
  -H "Content-Type: application/json" \
  -d '{ ...same body as /calculate... }'
```

---

## 4. Quotes — Retrieve by ID

```
GET /api/quotes/{id}
```

Retrieves the full request and result for a previously saved quote.

**Path Parameter:** `id` — the quote ID returned from `POST /api/quotes`

**Response 200:**
```json
{
  "request": { ... QuoteRequest ... },
  "result":  { ... QuoteResult ... }
}
```

**Response 404** — if the quote ID does not exist.

**curl:**
```bash
curl http://localhost:9090/api/quotes/QS-1708600001234
```

---

## 5. Quotes — List All

```
GET /api/quotes?limit=50
```

Returns a summary list of saved quotes.

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `limit` | Int | 50 | Maximum number of quotes to return |

**Response 200:**
```json
[
  {
    "id": "QS-1708600001234",
    "planId": "PHI_BASIC",
    "age": 35,
    "sumInsured": 1000000,
    "familyType": "2A1C",
    "zone": "Zone 1",
    "tenure": "3 Years"
  }
]
```

**curl:**
```bash
curl "http://localhost:9090/api/quotes?limit=10"
```

---

## 6. Plans — List All

```
GET /api/plans
```

Returns all active plans with their full configuration.

**Response 200:**
```json
[
  {
    "id": "PHI_BASIC",
    "name": "PHI Basic",
    "planType": "DOMESTIC",
    "underwritingCategory": "STANDARD",
    "geographyScope": "DOMESTIC",
    "coPaymentTable": "OMNIBUS",
    "description": "Standard domestic floater health plan — all ages 5–85",
    "availableSumInsureds": [200000, 300000, 400000, 500000, 750000, 1000000,
                              2000000, 2500000, 3000000, 4000000, 5000000, 7500000, 10000000],
    "availableZones": ["Zone 1", "Zone 2", "Zone 3", "Zone 4", "Pan India"],
    "availableFamilyTypes": ["1A", "2A", "2A1C", "2A2C", "2A3C", "2A4C",
                              "1A1C", "1A2C", "1A3C", "1A4C", "multi"],
    "allowedCoverIds": [],
    "maxDiscountCap": 0.30,
    "rateTableId": "PHI_BASIC",
    "minAge": 5,
    "maxAge": 85,
    "isActive": true
  }
]
```

**curl:**
```bash
curl http://localhost:9090/api/plans
```

### All Available Plan IDs

| Plan ID | Name | Type | Min–Max Age |
|---------|------|------|------------|
| `PHI_BASIC` | PHI Basic | DOMESTIC | 5–85 |
| `PHI_POSP` | PHI POSP | DOMESTIC_POSP | 5–85 |
| `PHI_FLAGSHIP1` | PHI Flagship 1 | DOMESTIC_FLAGSHIP | 5–85 |
| `PHI_FLAGSHIP2` | PHI Flagship 2 | DOMESTIC_FLAGSHIP | 5–85 |
| `PHI_FLAGSHIP4` | PHI Flagship 4 (Private Banker) | DOMESTIC_FLAGSHIP | 5–85 |
| `PHI_SENIOR` | PHI Senior | DOMESTIC_SENIOR | 46–85 |
| `PHI_SUBSTANDARD` | PHI Sub Standard | DOMESTIC_SUBSTANDARD | 46–85 |
| `PHI_GLOBAL_EXCL` | PHI Global (Excl. USA & Canada) | GLOBAL | 5–65 |
| `PHI_GLOBAL_ASIA` | PHI Global (Asia excl. India) | GLOBAL | 5–65 |
| `PHI_GLOBAL_EUROPE` | PHI Global (Europe) | GLOBAL | 5–65 |
| `PHI_GLOBAL_PLUS` | PHI Global Plus (Incl. USA & Canada) | GLOBAL_PLUS | 5–65 |
| `PHI_GLOBAL_PLUS_EXCL` | PHI Global Plus (Excl. USA & Canada) | GLOBAL_PLUS | 5–65 |
| `PHI_GLOBAL_PLUS_ASIA` | PHI Global Plus (Asia excl. India) | GLOBAL_PLUS | 5–65 |
| `PHI_GLOBAL_PLUS_EUROPE` | PHI Global Plus (Europe) | GLOBAL_PLUS | 5–65 |

---

## 7. Plans — Get by ID

```
GET /api/plans/{id}
```

**Response 200:** Full `Plan` object (same schema as list item above).

**Response 404** — if plan not found.

**curl:**
```bash
curl http://localhost:9090/api/plans/PHI_FLAGSHIP1
```

---

## 8. Plans — Create / Update

```
POST /api/plans
```

Creates or updates a plan (upsert by `id`).

**Request Body:** Full `Plan` object.

**Response 200:** The upserted `Plan` object.

**curl:**
```bash
curl -X POST http://localhost:9090/api/plans \
  -H "Content-Type: application/json" \
  -d '{
    "id": "PHI_CUSTOM",
    "name": "PHI Custom Plan",
    "planType": "DOMESTIC",
    "underwritingCategory": "STANDARD",
    "geographyScope": "DOMESTIC",
    "coPaymentTable": "OMNIBUS",
    "description": "Custom plan for testing",
    "availableSumInsureds": [500000, 1000000, 2500000],
    "availableZones": ["Zone 1", "Zone 2", "Zone 3", "Zone 4", "Pan India"],
    "availableFamilyTypes": ["1A", "2A", "2A1C"],
    "maxDiscountCap": 0.30,
    "rateTableId": "PHI_BASIC",
    "minAge": 18,
    "maxAge": 65,
    "isActive": true
  }'
```

---

## 9. Plans — Delete

```
DELETE /api/plans/{id}
```

**Response 204 No Content** — plan deleted.

**curl:**
```bash
curl -X DELETE http://localhost:9090/api/plans/PHI_CUSTOM
```

---

## 10. Covers — List All

```
GET /api/covers
```

Returns the complete catalogue of all 52 covers with their metadata.

**Response 200:** Array of cover definition objects.

```json
[
  {
    "id": "day1_instant",
    "name": "Day 1 Instant Increase of Cover",
    "row": 7,
    "isDiscount": false,
    "memberLevel": false,
    "adultsOnly": false,
    "param1Name": "Coverage Multiple",
    "param1Options": ["1.5X", "2X", "3X", "4X"]
  },
  {
    "id": "smart_select",
    "name": "Smart Select Network Discount (-15%)",
    "row": 26,
    "isDiscount": true,
    "memberLevel": false,
    "adultsOnly": false
  },
  {
    "id": "daily_hospital_cash",
    "name": "Daily Hospital Cash",
    "row": 32,
    "isDiscount": false,
    "memberLevel": true,
    "adultsOnly": false,
    "param1Name": "Cash/Day (₹)",
    "param1Options": ["500", "1000", "1500", "2000", "2500", "3000", "4000", "5000"]
  }
]
```

**curl:**
```bash
curl http://localhost:9090/api/covers
```

---

## 11. Covers — Age Bands

```
GET /api/covers/age-bands
```

Returns all 15 age bands used in the rate tables.

**Response 200:**
```json
[
  { "minAge": 5,  "maxAge": 17,  "label": "5 - 17"  },
  { "minAge": 18, "maxAge": 25,  "label": "18 - 25" },
  { "minAge": 26, "maxAge": 30,  "label": "26 - 30" },
  { "minAge": 31, "maxAge": 35,  "label": "31 - 35" },
  { "minAge": 36, "maxAge": 40,  "label": "36 - 40" },
  { "minAge": 41, "maxAge": 45,  "label": "41 - 45" },
  { "minAge": 46, "maxAge": 50,  "label": "46 - 50" },
  { "minAge": 51, "maxAge": 55,  "label": "51 - 55" },
  { "minAge": 56, "maxAge": 60,  "label": "56 - 60" },
  { "minAge": 61, "maxAge": 65,  "label": "61 - 65" },
  { "minAge": 66, "maxAge": 70,  "label": "66 - 70" },
  { "minAge": 71, "maxAge": 75,  "label": "71 - 75" },
  { "minAge": 76, "maxAge": 80,  "label": "76 - 80" },
  { "minAge": 81, "maxAge": 85,  "label": "81 - 85" },
  { "minAge": 86, "maxAge": 999, "label": "85+"     }
]
```

**curl:**
```bash
curl http://localhost:9090/api/covers/age-bands
```

---

## 12. Covers — Family Types

```
GET /api/covers/family-types
```

Returns all supported family type combinations.

**Response 200:**
```json
[
  { "code": "1A",   "adultCount": 1, "childCount": 0, "totalMembers": 1, "isFloater": false },
  { "code": "2A",   "adultCount": 2, "childCount": 0, "totalMembers": 2, "isFloater": true  },
  { "code": "2A1C", "adultCount": 2, "childCount": 1, "totalMembers": 3, "isFloater": true  },
  { "code": "2A2C", "adultCount": 2, "childCount": 2, "totalMembers": 4, "isFloater": true  },
  { "code": "2A3C", "adultCount": 2, "childCount": 3, "totalMembers": 5, "isFloater": true  },
  { "code": "2A4C", "adultCount": 2, "childCount": 4, "totalMembers": 6, "isFloater": true  },
  { "code": "1A1C", "adultCount": 1, "childCount": 1, "totalMembers": 2, "isFloater": true  },
  { "code": "1A2C", "adultCount": 1, "childCount": 2, "totalMembers": 3, "isFloater": true  },
  { "code": "1A3C", "adultCount": 1, "childCount": 3, "totalMembers": 4, "isFloater": true  },
  { "code": "1A4C", "adultCount": 1, "childCount": 4, "totalMembers": 5, "isFloater": true  },
  { "code": "multi","adultCount": 2, "childCount": 0, "totalMembers": 2, "isFloater": false }
]
```

**curl:**
```bash
curl http://localhost:9090/api/covers/family-types
```

---

## 13. Covers — Sum Insureds

```
GET /api/covers/sum-insureds
```

Returns all valid sum insured options grouped by domestic vs. global.

**Response 200:**
```json
{
  "domestic": [200000, 300000, 400000, 500000, 750000, 1000000, 2000000,
               2500000, 3000000, 4000000, 5000000, 7500000, 10000000],
  "global":   [5000000, 7500000, 10000000, 20000000, 30000000]
}
```

**curl:**
```bash
curl http://localhost:9090/api/covers/sum-insureds
```

---

## 14. Import — Seed Built-in Data

```
POST /api/import/seed
```

Seeds the PostgreSQL database with the built-in actuarial data extracted from `Rate_Calculator_v7.0.xlsm`. No file upload required. Idempotent — safe to run multiple times.

**Response 200:**
```json
{ "message": "Built-in rate data seeded successfully" }
```

**curl:**
```bash
curl -X POST http://localhost:9090/api/import/seed
```

---

## 15. Import — Upload Excel

```
POST /api/import/upload
Content-Type: multipart/form-data
```

Uploads a new Excel workbook and imports rates into the database.

**Request:** Multipart form with a file field containing the `.xlsx` / `.xlsm` file.

**Response 200:**
```json
{ "message": "Excel data imported successfully" }
```

**Response 400:**
```json
{ "error": "No file provided" }
```

**curl:**
```bash
curl -X POST http://localhost:9090/api/import/upload \
  -F "file=@/path/to/Rate_Calculator_v8.0.xlsm"
```

---

## 16. Buy Online Routes

The buy-online module exposes the following endpoints, all under `/api/buy-online`:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/buy-online/otp/send` | Send mobile OTP |
| POST | `/api/buy-online/otp/verify` | Verify mobile OTP |
| GET | `/api/buy-online/hospitals?pincode=` | Hospital network by pincode |
| POST | `/api/buy-online/eligibility` | Check member eligibility |
| POST | `/api/buy-online/premium` | Calculate buy-online premium |
| POST | `/api/buy-online/kyc/otp/send` | Send KYC E-KYC OTP |
| POST | `/api/buy-online/kyc/otp/verify` | Verify KYC OTP |
| POST | `/api/buy-online/proposal` | Submit proposal |
| GET | `/api/buy-online/proposal/{proposalNumber}` | Get proposal status |

Detailed buy-online API documentation is part of the buy-online journey and is not covered in this rate calculator reference.

---

## 17. Common Data Schemas

### Plan Object

```typescript
interface Plan {
  id: string                          // "PHI_BASIC"
  name: string                        // "PHI Basic"
  planType: "DOMESTIC" | "DOMESTIC_FLAGSHIP" | "DOMESTIC_SENIOR" |
            "DOMESTIC_SUBSTANDARD" | "DOMESTIC_POSP" | "GLOBAL" | "GLOBAL_PLUS"
  underwritingCategory: "STANDARD" | "SUB_STANDARD" | "SENIOR"
  geographyScope: "DOMESTIC" | "GLOBAL_EXCL_US_CANADA" | "GLOBAL_ASIA_EXCL_INDIA" |
                  "GLOBAL_EUROPE" | "GLOBAL_INCL_US_CANADA"
  coPaymentTable: "OMNIBUS" | "SENIOR" | "SUB_STANDARD"
  description: string
  availableSumInsureds: number[]      // INR amounts
  availableZones: string[]            // ["Zone 1", "Zone 2", ...]
  availableFamilyTypes: string[]      // ["1A", "2A", "2A1C", ...]
  allowedCoverIds: string[]           // empty = all covers allowed
  maxDiscountCap: number              // 0.30 = 30%
  rateTableId: string
  minAge: number
  maxAge: number
  isActive: boolean
}
```

### QuoteResult Object

```typescript
interface QuoteResult {
  requestId: string
  planId: string
  basePremiumTotal: number
  coverBreakdown: CoverPremiumBreakdown[]
  totalAddons: number
  uwLoadingAmount: number
  totalBeforeDiscount: number
  discountBreakdown: DiscountBreakdown[]
  totalDiscountAmount: number          // negative
  totalAfterDiscount: number
  instalmentLoadingAmount: number
  instalmentPremium: number
  instalmentCount: number
  yearlyBreakdown: YearBreakdown[]
  isValid: boolean
  validationErrors: string[]
}

interface CoverPremiumBreakdown {
  coverId: string
  coverName: string
  yearlyPremiums: number[]
  totalPremium: number
  rateApplied: number | null
  isDiscount: boolean
}

interface DiscountBreakdown {
  discountId: string
  discountName: string
  rateApplied: number           // negative, e.g., -0.025
  amount: number                // negative INR amount
}

interface YearBreakdown {
  year: number
  age: number
  ageBand: string
  basePremium: number
  coverPremiums: Record<string, number>
  subtotal: number
}
```

---

## 18. Error Handling

The API uses standard HTTP status codes:

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 201 | Created (quote saved) |
| 204 | No Content (delete) |
| 400 | Bad Request (missing file, bad JSON) |
| 404 | Not Found (quote/plan ID not found) |
| 422 | Unprocessable Entity (validation failure in quote) |
| 500 | Internal Server Error |

For validation failures from the pricing engine, the response body is the full `QuoteResult` with `isValid: false` and `validationErrors` populated. The HTTP status is 422.

For `NoSuchElementException` and `IllegalArgumentException`, Ktor's default exception handler returns 400 or 404 with an error message.

---

*Next: [Desktop Calculator Guide](./05-desktop-guide.md)*
