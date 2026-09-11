# Development Guide

This guide covers prerequisites, build commands, running each module, project structure, and how to extend the platform with new covers, plans, and rate tables.

## Table of Contents
1. [Prerequisites](#1-prerequisites)
2. [Database Setup](#2-database-setup)
3. [Build Commands](#3-build-commands)
4. [Running Each Module](#4-running-each-module)
5. [Project Structure](#5-project-structure)
6. [Key Files Quick Reference](#6-key-files-quick-reference)
7. [How to Add a New Cover](#7-how-to-add-a-new-cover)
8. [How to Add a New Plan](#8-how-to-add-a-new-plan)
9. [How to Extend the Excel Importer](#9-how-to-extend-the-excel-importer)
10. [Coding Patterns and Conventions](#10-coding-patterns-and-conventions)
11. [Testing the Pricing Engine](#11-testing-the-pricing-engine)
12. [Troubleshooting Common Issues](#12-troubleshooting-common-issues)

---

## 1. Prerequisites

### Required

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 17 or 21 | Required by all modules |
| Gradle | 8.11.1 (wrapper included) | Build system |
| PostgreSQL | 15+ | Required by `:server` for quote persistence and import |

### Recommended

| Tool | Version | Purpose |
|------|---------|---------|
| IntelliJ IDEA | 2024.x+ | Kotlin + Compose Multiplatform support |
| Kotlin Plugin | 2.1.0+ | Matching the project Kotlin version |
| TablePlus / DBeaver | Any | PostgreSQL GUI for inspecting database tables |

### Optional (for future targets)

| Tool | Purpose |
|------|---------|
| Xcode 15+ | Building the iOS targets of `:shared` |
| Android Studio | If Android target is restored to `:buyonline` |

### JDK Installation Check

```bash
java -version
# Expected: openjdk version "17.x.x" or "21.x.x"

javac -version
# Expected: javac 17.x.x or javac 21.x.x
```

---

## 2. Database Setup

### Create the Database and User

```bash
# Connect as superuser
psql -U postgres

# Inside psql:
CREATE DATABASE rate_calculator;
CREATE USER rate_admin WITH PASSWORD 'rate123';
GRANT ALL PRIVILEGES ON DATABASE rate_calculator TO rate_admin;

# Connect to the database
\c rate_calculator
GRANT ALL ON SCHEMA public TO rate_admin;
\q
```

### Verify Connection

```bash
psql -h localhost -p 5432 -U rate_admin -d rate_calculator -c "SELECT 1;"
# Expected: 1 row returned
```

### Application Config

The connection is configured in `server/src/main/resources/application.conf`:

```hocon
database {
    url        = "jdbc:postgresql://localhost:5432/rate_calculator"
    driver     = "org.postgresql.Driver"
    user       = "rate_admin"
    password   = "rate123"
    maxPoolSize = 10
}
```

### Database Schema Creation

The Exposed ORM creates tables automatically on first run (`SchemaUtils.createMissingTablesAndColumns`). No manual schema creation is needed.

---

## 3. Build Commands

All commands are run from the project root `/Users/viveksingh/Developer/yogesh/rate`.

### Build and Verify Each Module

```bash
# Compile the shared pricing engine (JVM target)
./gradlew :shared:jvmJar

# Compile + run all shared tests
./gradlew :shared:jvmTest

# Verify the server compiles
./gradlew :server:compileKotlin

# Verify the desktop compiles
./gradlew :desktop:compileKotlin

# Verify the buyonline module compiles
./gradlew :buyonline:jvmJar
```

### Build All Modules

```bash
./gradlew build
```

> **`build` needs a headless Chrome.** 19 modules declare `wasmJs { browser() }`,
> so `build` runs `wasmJsBrowserTest`, and with no browser it stops at
> *"No binary for ChromeHeadless browser on your platform. Please, set
> CHROME_BIN"*. Install Chrome or Chromium, or point `CHROME_BIN` at one you
> already have — `npx puppeteer@23 browsers install chrome-headless-shell`
> fetches one into `~/.cache/puppeteer` if you would rather not install a
> system package.
>
> If you run as **root** (a container, a CI-shaped box), Chrome additionally
> refuses to start at all — *"Running as root without --no-sandbox is not
> supported"* — and Karma's launcher does not pass that flag, so `CHROME_BIN`
> must point at a wrapper that adds it:
>
> ```bash
> printf '#!/usr/bin/env bash\nexec /path/to/chrome --no-sandbox "$@"\n' > /tmp/chrome-ns
> chmod +x /tmp/chrome-ns && export CHROME_BIN=/tmp/chrome-ns
> ```
>
> Added 2026-09-11: this section said only `./gradlew build` and every
> contributor without Chrome hit the wall with no idea why. `.github/workflows/build.yml`
> does exactly the above, and the comment there records how the fleet was
> measured.

### Clean Build (if you hit stale cache issues)

```bash
./gradlew clean build
```

---

## 4. Running Each Module

### Start the API Server

```bash
PORT=9090 ./gradlew :server:run
```

The server starts on port 9090. Wait for the log line:
```
Application started in X.XXX seconds.
```

Verify with:
```bash
curl http://localhost:9090/health
# Expected: {"status":"ok","service":"rate-calculator"}
```

**Why port 9090?** Port 8080 is occupied by a separate project (`Deen`) on this machine. The `PORT=9090` environment variable overrides the default from `application.conf`.

### Start the Desktop Calculator

```bash
./gradlew :desktop:run
```

Opens a native desktop window. The server does not need to be running — calculations use `LocalRateDataProvider` (in-process). The server is only needed for:
- Saving quotes to PostgreSQL
- The Import screen (file upload)

### Start the Buy Online App

```bash
./gradlew :buyonline:run
```

Opens the buy-online journey desktop window. Requires the server on port 9090 for OTP, eligibility, and proposal endpoints.

---

## 5. Project Structure

```
rate/
├── build.gradle.kts              ← Root build file (plugin versions)
├── settings.gradle.kts           ← Module includes
├── gradle/
│   ├── libs.versions.toml        ← Version catalog (all dependency versions)
│   └── wrapper/
│
├── shared/                       ← KMP pricing engine
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/rate/domain/
│       │   ├── model/
│       │   │   ├── Models.kt             ← Enums, Plan, Member, QuoteRequest/Result
│       │   │   └── CoverDefinitions.kt   ← CoverIds constants, COVER_ACCUM_BASES
│       │   ├── engine/
│       │   │   └── PricingEngine.kt      ← Core calculation logic
│       │   ├── data/
│       │   │   ├── RateTables.kt         ← All actuarial rate tables (hard-coded)
│       │   │   ├── CoverCatalog.kt       ← UI metadata (names, params, options)
│       │   │   └── LocalRateDataProvider.kt  ← Offline implementation
│       │   └── repository/
│       │       └── RateDataProvider.kt   ← Interface
│       └── jvmTest/
│
├── server/                       ← Ktor REST API
│   ├── build.gradle.kts
│   └── src/main/
│       ├── resources/application.conf
│       └── kotlin/com/rate/server/
│           ├── Application.kt
│           ├── plugins/
│           │   ├── Routing.kt
│           │   ├── Serialization.kt
│           │   └── Databases.kt
│           ├── routes/
│           │   ├── QuoteRoutes.kt
│           │   ├── PlanRoutes.kt
│           │   ├── CoverRoutes.kt
│           │   ├── ImportRoutes.kt
│           │   └── BuyOnlineRoutes.kt
│           ├── database/
│           │   ├── tables/           ← Exposed table definitions
│           │   └── repositories/     ← QuoteRepositoryImpl, PlanRepositoryImpl
│           └── import/
│               └── ExcelImporter.kt
│
├── desktop/                      ← Compose Desktop
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/rate/desktop/
│       ├── Main.kt
│       ├── navigation/
│       │   └── Screen.kt
│       ├── ui/
│       │   ├── calculator/
│       │   │   ├── CalculatorScreen.kt
│       │   │   └── CalculatorViewModel.kt
│       │   ├── configurator/
│       │   ├── import/
│       │   ├── components/         ← Reusable composables
│       │   └── theme/
│       └── data/
│           └── PincodeZoneMapper.kt
│
├── buyonline/                    ← Compose Multiplatform buy-online
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/rate/buyonline/
│       │   ├── App.kt
│       │   ├── navigation/Navigation.kt
│       │   ├── model/BuyOnlineModels.kt
│       │   ├── api/BuyOnlineApiClient.kt
│       │   ├── viewmodel/BuyOnlineViewModel.kt
│       │   └── ui/
│       │       ├── theme/
│       │       ├── components/
│       │       └── [22 screen packages]/
│       └── jvmMain/kotlin/Main.kt
│
└── docs/                         ← This documentation
    ├── README.md
    ├── 01-architecture.md
    ├── 02-pricing-engine.md
    ├── 03-rate-tables.md
    ├── 04-api-reference.md
    ├── 05-desktop-guide.md
    ├── 06-excel-import.md
    ├── 07-business-rules.md
    └── 08-development.md
```

---

## 6. Key Files Quick Reference

| File | When to edit |
|------|-------------|
| `shared/.../model/Models.kt` | Add new enums (Tenure, Zone, PaymentMode), modify QuoteRequest/Result shape |
| `shared/.../model/CoverDefinitions.kt` | Add new cover IDs to `CoverIds`, define accumulation bases |
| `shared/.../engine/PricingEngine.kt` | Add new cover calculation logic (new row in Excel) |
| `shared/.../data/RateTables.kt` | Add new rate table constants |
| `shared/.../data/CoverCatalog.kt` | Add UI metadata for new covers (name, description, params) |
| `shared/.../data/LocalRateDataProvider.kt` | Wire new cover ID to rate lookup |
| `server/.../routes/CoverRoutes.kt` | Expose new cover in the API catalog |
| `server/.../routes/QuoteRoutes.kt` | Only if `QuoteRequest`/`QuoteResult` shape changes |
| `server/.../routes/PlanRoutes.kt` | Plan CRUD — rarely needs changes |
| `desktop/.../calculator/CalculatorScreen.kt` | UI changes to the calculator form or results |
| `desktop/.../calculator/CalculatorViewModel.kt` | Business logic changes in the ViewModel |

---

## 7. How to Add a New Cover

### Step 1: Define the Cover ID

In `shared/src/commonMain/kotlin/com/rate/domain/model/CoverDefinitions.kt`:

```kotlin
object CoverIds {
    // ...existing covers...
    const val MY_NEW_COVER = "my_new_cover"   // row XX
}
```

### Step 2: Define the Accumulation Base (if % cover)

In the same file, add the accumulation base list:

```kotlin
// If the cover is at row N, its base = SUM of all rows before it
private val R_NEW_BASE = R_PREVIOUS_BASE + CoverIds.PREVIOUS_COVER

val COVER_ACCUM_BASES: Map<String, List<String>> = mapOf(
    // ...existing entries...
    CoverIds.MY_NEW_COVER to R_NEW_BASE,
)
```

For a flat cover (fixed INR), no accumulation base is needed.

### Step 3: Add Rate Table Data

In `shared/src/commonMain/kotlin/com/rate/domain/data/RateTables.kt`:

```kotlin
// Flat cover
val MY_NEW_COVER_FLAT: Double = 250.0   // INR per year

// OR parameterised rates
val MY_NEW_COVER_RATES: Map<String, Double> = mapOf(
    "Option A" to 0.05,
    "Option B" to 0.10
)
```

### Step 4: Wire the Cover in LocalRateDataProvider

In `shared/src/commonMain/kotlin/com/rate/domain/data/LocalRateDataProvider.kt`, inside `getCoverRate()`:

```kotlin
"my_new_cover" -> {
    val option = param1 ?: "Option A"
    RateTables.MY_NEW_COVER_RATES[option] ?: 0.0
}
```

Or for a flat cover:
```kotlin
"my_new_cover" -> RateTables.MY_NEW_COVER_FLAT
```

### Step 5: Add Engine Logic

In `shared/src/commonMain/kotlin/com/rate/domain/engine/PricingEngine.kt`, insert the calculation in the correct row order:

```kotlin
// For a flat cover:
if (enabled(CoverIds.MY_NEW_COVER)) {
    val flat = data.getCoverRate(CoverIds.MY_NEW_COVER)
    yr[CoverIds.MY_NEW_COVER] = DoubleArray(years) { flat }
}

// For a % cover with static rate:
pctCover(CoverIds.MY_NEW_COVER, COVER_ACCUM_BASES[CoverIds.MY_NEW_COVER]!!) {
    data.getCoverRate(CoverIds.MY_NEW_COVER, params(CoverIds.MY_NEW_COVER).param1)
}

// For a per-year age-band % cover:
pctCoverPerYear(CoverIds.MY_NEW_COVER, COVER_ACCUM_BASES[CoverIds.MY_NEW_COVER]!!) { band ->
    data.getCoverRate(CoverIds.MY_NEW_COVER, null, null, band, request.sumInsured)
}
```

### Step 6: Add UI Metadata

In `shared/src/commonMain/kotlin/com/rate/domain/data/CoverCatalog.kt`:

```kotlin
CoverMeta("my_new_cover", "My New Cover",
    "Description of what this cover does",
    param1 = ParamDef("Option", listOf("Option A", "Option B"))
),
```

### Step 7: Expose via API

In `server/src/main/kotlin/com/rate/server/routes/CoverRoutes.kt`:

```kotlin
cover(CoverIds.MY_NEW_COVER, "My New Cover", rowNumber,
    p1 = "Option", p1opts = listOf("Option A", "Option B")),
```

### Step 8: Test

```bash
./gradlew :shared:jvmTest
```

And run a spot-check API call with the new cover in `selectedCovers`.

---

## 8. How to Add a New Plan

### Step 1: Define the Plan in LocalRateDataProvider

In `shared/src/commonMain/kotlin/com/rate/domain/data/LocalRateDataProvider.kt`, add to the `allPlans` list:

```kotlin
Plan(
    id = "PHI_NEW_PLAN",
    name = "PHI New Plan",
    planType = PlanType.DOMESTIC_FLAGSHIP,
    underwritingCategory = UnderwritingCategory.STANDARD,
    geographyScope = GeographyScope.DOMESTIC,
    coPaymentTable = CoPaymentTable.OMNIBUS,
    description = "Description of the new plan",
    availableSumInsureds = listOf(500_000L, 1_000_000L, 2_500_000L, 5_000_000L),
    availableZones = ALL_ZONES,                      // or TWO_ZONES for Senior/SubStandard
    availableFamilyTypes = ALL_FT,
    rateTableId = "PHI_NEW_PLAN",                   // or reuse existing like "PHI_BASIC"
    minAge = 18,
    maxAge = 65,
    isActive = true
)
```

### Step 2: Add Plan Factor (if needed)

In `LocalRateDataProvider.getBasePremium()`, add the plan factor:

```kotlin
val planFactor = when {
    // ...existing cases...
    planId == "PHI_NEW_PLAN" -> 1.50   // 50% above PHI Basic rates
    else -> 1.00
}
```

### Step 3: Add Plan-Specific Cover Rates (if applicable)

If the new plan has different rates for covers like `modern_treatment_plus` or `room_rent_mod`, add entries to the relevant rate tables in `RateTables.kt`:

```kotlin
val MODERN_TREATMENT_PLUS_RATES: Map<String, Double> = mapOf(
    // ...existing entries...
    "PHI_NEW_PLAN" to -0.0289   // bundled, like Flagship
)
```

### Step 4: Rebuild and Verify

```bash
./gradlew :shared:jvmJar
./gradlew :server:run &
curl http://localhost:9090/api/plans | python3 -m json.tool | grep "PHI_NEW_PLAN"
```

---

## 9. How to Extend the Excel Importer

The importer lives in `server/src/main/kotlin/com/rate/server/import/ExcelImporter.kt`.

### Adding a New Rate Table

When a new rate table is added to the Excel workbook, extend the importer:

```kotlin
class ExcelImporter {

    fun importFromExcel(stream: InputStream) {
        val workbook = WorkbookFactory.create(stream)
        importBaseRates(workbook)
        importCoverRates(workbook)
        importMemberRates(workbook)
        importNewRateTable(workbook)   // ← new method
    }

    private fun importNewRateTable(workbook: Workbook) {
        val sheet = workbook.getSheet("NewSheet") ?: return
        // Read cells and insert into DB via Exposed
        transaction {
            // NewRatesTable.deleteAll()  // clear existing
            for (row in sheet) {
                // parse row
                // NewRatesTable.insert { ... }
            }
        }
    }
}
```

### Adding a New DB Table

In `server/src/main/kotlin/com/rate/server/database/tables/`, create a new Exposed table:

```kotlin
object NewRatesTable : Table("new_rates") {
    val coverId    = varchar("cover_id", 50)
    val param1     = varchar("param1", 100).nullable()
    val rate       = double("rate")
    override val primaryKey = PrimaryKey(coverId, param1)
}
```

Register it in `Databases.kt`:
```kotlin
SchemaUtils.createMissingTablesAndColumns(
    // ...existing tables...
    NewRatesTable
)
```

### Wiring to RateDataProviderImpl

In `RateDataProviderImpl.getCoverRate()`, add the lookup:

```kotlin
"my_new_cover" -> {
    transaction {
        NewRatesTable
            .select { NewRatesTable.coverId eq coverId }
            .firstOrNull()
            ?.get(NewRatesTable.rate) ?: 0.0
    }
}
```

---

## 10. Coding Patterns and Conventions

### ViewModel Pattern

```kotlin
// CalculatorViewModel pattern
class CalculatorViewModel {
    var result by mutableStateOf<QuoteResult?>(null)
    var loading by mutableStateOf(false)

    fun calculate() {
        scope.launch {
            loading = true
            runCatching {
                engine.calculate(buildRequest())
            }.onSuccess { res ->
                result = res
            }.onFailure { e ->
                error = e.message
            }
            loading = false
        }
    }
}
```

### Navigation Pattern

```kotlin
// Simple backstack navigation — no Voyager/Decompose
var currentScreen by mutableStateOf<Screen>(Screen.Calculator)

fun navigate(screen: Screen) {
    currentScreen = screen
}
```

### Server Route Pattern

```kotlin
// Always use @Serializable data classes for responses (never raw mapOf with mixed types)
@Serializable
data class MyResponse(val field1: String, val field2: Int)

post("/my-route") {
    val req = call.receive<MyRequest>()
    call.respond(MyResponse("value", 42))
}
```

### Accumulation Base Helpers

Inside `PricingEngine`, use the pre-defined helpers:

```kotlin
// Constant rate cover:
pctCover(coverId, accumulationBase) { rate }

// Per-year age-band rate cover:
pctCoverPerYear(coverId, accumulationBase) { ageBandMin -> rate(ageBandMin) }

// Flat cover:
if (enabled(coverId)) {
    val flat = data.getCoverRate(coverId)
    yr[coverId] = DoubleArray(years) { flat }
}

// Member-level cover:
if (enabled(coverId)) {
    val arr = DoubleArray(years)
    for (y in 0 until years) {
        arr[y] = members.filter { it.isAdult }.sumOf { m ->
            data.getMemberLevelRate(coverId, getAgeBand(m.age + y).minAge, param)
        }
    }
    yr[coverId] = arr
}
```

### Theme Colors

```kotlin
// Defined in desktop/ui/theme/ and buyonline/ui/theme/
val PruRed        = Color(0xFFE31837)
val PruBackground = Color(0xFFF5F5F5)
val PruSubtext    = Color(0xFF757575)
val PruSuccess    = Color(0xFF2E7D32)
val DiscountColor = Color(0xFF2E7D32)   // green for discounts
val LoadingColor  = Color(0xFFF57C00)   // orange for loadings/surcharges
```

---

## 11. Testing the Pricing Engine

### Unit Test Location

```
shared/src/jvmTest/kotlin/com/rate/domain/engine/
```

### Writing a New Test

```kotlin
class PricingEngineTest {

    private val engine = PricingEngine(LocalRateDataProvider())

    @Test
    fun `base premium for 1A Zone4 age35 SI10L matches Excel`() = runTest {
        val result = engine.calculate(QuoteRequest(
            planId       = "PHI_BASIC",
            primaryAge   = 35,
            sumInsured   = 1_000_000L,
            familyType   = "1A",
            zone         = "Zone 4",
            tenure       = Tenure.ONE_YEAR,
            paymentMode  = PaymentMode.ANNUAL,
            paymentTenure = Tenure.ONE_YEAR,
            members      = listOf(Member(1, 35, "Self", "M")),
            selectedCovers = emptyList(),
            selectedDiscounts = emptyList()
        ))

        assertTrue(result.isValid)
        assertEquals(4417.0, result.basePremiumTotal, delta = 1.0)
    }

    @Test
    fun `consumables list1 adds 5pct to base`() = runTest {
        val baseResult = engine.calculate(baseRequest())
        val withCover  = engine.calculate(baseRequest().copy(
            selectedCovers = listOf(CoverSelection("consumables_list1"))
        ))

        val expectedCoverPremium = baseResult.basePremiumTotal * 0.05
        val actualCoverPremium   = withCover.totalAddons
        assertEquals(expectedCoverPremium, actualCoverPremium, delta = 1.0)
    }
}
```

### Run Tests

```bash
./gradlew :shared:jvmTest
```

---

## 12. Troubleshooting Common Issues

### Issue: `FlowRow` compilation error

**Symptom:** `@OptIn(ExperimentalLayoutApi::class)` missing on a screen.

**Fix:** Add the annotation to the composable function:
```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyScreen() { ... }
```

### Issue: Ktor response serialization fails with mixed-type mapOf

**Symptom:** `Serialization error: Polymorphic serializer was not found for...`

**Fix:** Replace `mapOf("key" to value)` with a `@Serializable` data class:
```kotlin
// BAD:
call.respond(mapOf("status" to "ok", "count" to 5))

// GOOD:
@Serializable data class StatusResponse(val status: String, val count: Int)
call.respond(StatusResponse("ok", 5))
```

### Issue: Port 8080 already in use

**Fix:** Always use `PORT=9090 ./gradlew :server:run`

### Issue: Android plugin error in buyonline module

**Symptom:** `Plugin 'com.android.library' not found`

**Fix:** The Android target has been removed from `:buyonline`. Ensure `buyonline/build.gradle.kts` does not include `id("com.android.library")` and no `android { }` block.

### Issue: `gradle build` fails on iOS targets

**Symptom:** iOS compilation fails on non-macOS machine.

**Fix:** iOS targets require Xcode. On non-Mac or CI without Xcode, add to `shared/build.gradle.kts`:
```kotlin
// Optionally skip iOS targets on non-Mac:
if (System.getProperty("os.name").contains("Mac")) {
    iosArm64()
    iosX64()
    iosSimulatorArm64()
}
```

### Issue: Database tables not found on first server start

**Fix:** Exposed creates tables automatically via `SchemaUtils.createMissingTablesAndColumns`. If tables are missing, ensure the database user has `CREATE TABLE` privileges. Re-run the grants:
```bash
psql -U postgres -d rate_calculator -c "GRANT ALL ON SCHEMA public TO rate_admin;"
```

### Issue: Plan list empty in desktop app

**Symptom:** The Plan dropdown shows no options.

**Check:** The desktop app loads plans from `LocalRateDataProvider.getAllPlans()`. This is in-process and should always return data. If the dropdown is empty, check for a Kotlin exception in the ViewModel:
```kotlin
// CalculatorViewModel.kt — verify:
scope.launch {
    runCatching { provider.getAllPlans() }
        .onSuccess { plans = it }
        .onFailure { e -> error = e.message }
}
```

---

*Return to: [Documentation Home](./README.md)*
