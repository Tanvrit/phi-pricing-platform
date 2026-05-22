# Aegis — Business Dashboard & Plan Configurator: Five-Pass Redesign Plan

> **Codename:** *Aegis* (the shield) — the business-user control plane for a Prudential-style
> Indian retail health insurance platform.
> **Theme:** Light, only.
> **Target tech:** Compose Multiplatform (Desktop primary, Web/Tablet secondary).
> **Audience:** Product managers, actuaries, underwriters, plan owners, ops leads.
> **North star:** Linear's craft × Stripe's data density × Airtable's editability × IRDAI's regulatory rigor.

This document iterates **5 passes**. Each pass critiques and improves the previous one.
Read in order. The final "Consolidated Plan" at the bottom is the 10-minute summary.

---

# Pass 1 — Baseline IA, Surface Inventory, and Primary Flows

## P1.0 What "best in the world" means here

The current Compose Desktop app is a single-purpose calculator window. The audit (`AUDIT_REPORT.md` §6.4)
calls out: read-only rate configs, no plan duplication, no versioning, no min/max validation,
stale empty-state copy, no NCB/renewal/portability/endorsement, no UIN registry, no MIS reports,
no UW queue, zero tests, no audit trail. We are not redesigning a screen — we are designing an
**insurance operations cockpit** that an actuary or a product owner uses for 6 hours a day.

The competitor bar:

| Surface                              | Inspiration                          | Why                                                                |
|--------------------------------------|--------------------------------------|--------------------------------------------------------------------|
| Information architecture / nav       | **Linear**, **Vercel**               | Two-pane, low-noise, command-palette-first.                        |
| Tables, density, sorting             | **Airtable**, **Retool**, **Stripe** | Frozen columns, multi-select, inline edit, tabular figures.        |
| Configuration depth + diff/version   | **Stripe** (API versions), **Vercel** (deployments) | Side-by-side diff, "deploy" semantics, rollback.       |
| Side-panel for inspect/edit          | **Figma**, **Notion**                | The right-rail inspector replaces 80% of modals.                   |
| Approval & audit                     | **Salesforce Lightning**, **Pipefy** | Maker-checker, change history, governance.                         |
| Forms / heavy editing                | **Notion**, **Linear**               | Inline edit > modal.                                               |
| Analytics & money charts             | **Stripe Sigma**, **Mixpanel**       | Crisp small-multiples, KPI cards with sparklines, segmented bars.  |
| Onboarding / empty states            | **Linear**, **Notion**               | Hand-drawn, conversational, action-oriented.                       |

## P1.1 The 13 surfaces — one-line purposes and personas

| #  | Surface                  | Purpose (1 sentence)                                                                              | Primary persona                |
|----|--------------------------|---------------------------------------------------------------------------------------------------|--------------------------------|
| 1  | Home Dashboard           | The morning briefing: what shipped, what's stuck, what changed.                                   | Ops lead / Product head        |
| 2  | Product Catalog          | A registry of every IRDAI-approved product with UIN, version, lifecycle status.                   | Product manager                |
| 3  | Plan Configurator        | Deep edit of a single Plan: rates, covers, discounts, eligibility, geography, business rules.     | Product manager / Actuary      |
| 4  | Cover Catalog Manager    | The library of 50+ covers — names, params, accumulation bases, mappings, Excel parity.            | Actuary                        |
| 5  | Rate Table Manager       | Versioned rate matrices, side-by-side diff, approval workflow, effective-date controls.            | Actuary / Pricing committee    |
| 6  | Discount Manager         | Discount config + cap + mutual-exclusion rules + promotional levers.                              | Product manager                |
| 7  | Business Rules Editor    | Visual rule builder for eligibility, mutual-exclusion, plan-specific restrictions.                | Product manager + Underwriter  |
| 8  | Excel Import Studio      | Multi-step wizard: upload → parse → dry-run → diff → confirm → rollback snapshot.                 | Actuary                        |
| 9  | Quote Explorer           | Search, filter, compare any quote ever calculated; drill into per-cover yearly grid.              | Underwriter / Ops              |
| 10 | Underwriting Queue       | Inbox of UW referrals with risk score, action buttons (refer, accept, decline, load).             | Underwriter                    |
| 11 | Reports & Analytics      | Premium, plan mix, zone mix, claim ratio, lapse, agent commission — all exportable.               | CFO / Product head / Compliance|
| 12 | Audit & Governance       | Every change to plan/rate/cover/discount with who/when/why; 4-eyes (maker-checker) approval.      | Compliance / IT audit          |
| 13 | Settings                 | Users, roles (RBAC), org config, GST rate, IRDAI artefacts library, integrations.                 | Admin                          |

## P1.2 Top-level shell

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  [Aegis logo]   PHI Retail Health · v2026.05.20-rc3        ⌘K Search…       [Bell]  [Avatar] │
├──────────────┬───────────────────────────────────────────────────────────────────────────────┤
│ HOME         │                                                                               │
│ ─────────    │                                                                               │
│ Products     │                                                                               │
│  · Catalog   │                                                                               │
│  · Configurat│                                                                               │
│  · Covers    │                       Workspace                                               │
│  · Rates     │                  (selected surface lives here)                                │
│  · Discounts │                                                                               │
│  · Rules     │                                                                               │
│ Operations   │                                                                               │
│  · Quotes    │                                                                               │
│  · UW Queue  │                                                                               │
│  · Import    │                                                                               │
│ Insight      │                                                                               │
│  · Reports   │                                                                               │
│  · Audit     │                                                                               │
│ ─────────    │                                                                               │
│ Settings     │                                                                               │
│ Help         │                                                                               │
├──────────────┴───────────────────────────────────────────────────────────────────────────────┤
│  Env: STAGING · DB: rate_calculator@pg-mum-01 · Engine v0.9.4 · Rate-table v2026-Q2 · 245ms  │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Left rail** (collapsible, 240px → 64px icon-only): three groups — *Products*, *Operations*, *Insight*.
- **Top bar** (56px): workspace switcher, environment chip (Staging/UAT/Prod), command palette trigger, notifications, user.
- **Status bar** (28px bottom): live engine version, rate-table version, latency.
- **Workspace** = the routed content of the selected surface. 1280×800 minimum, fluid up to 4K.

## P1.3 Surface-by-surface — purpose, IA, key screens, interactions

### S1 · Home Dashboard

**Purpose** — On Monday morning, the product head answers four questions in 60 seconds:
*Are quotes flowing? Is the rate table healthy? Is anything stuck in UW? Is anyone breaking the rules?*

**IA** — A grid of KPI cards on top, three "swim-lane" widgets below.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  KPI · Premium booked (yest.)   KPI · Quotes calc'd   KPI · UW backlog   KPI · Lapse │
│   ₹2,41,87,650   ▲ 12.4 %         8,742  ▲ 3 %         42 ⚠ red          1.8 %       │
│   spark…………………                  spark………………           spark…………           spark…… │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  Plan mix (today)              │  Zone mix (today)             │  Tenure mix         │
│  stacked bar by plan           │  donut chart                  │  bar chart 1y…5y    │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  Recent activity (live)        │  Approvals waiting (you)      │  Releases this week │
│  · Ria changed Senior rates    │  · Rate-table v2026-Q3 rev 4  │  · Cover catalog    │
│  · Anil duplicated Global Plus │  · Plan "POSP-Lite" approval  │    PR #87 merged    │
│  · Import #44 succeeded        │  · Discount disc_gmc cap +5%  │  · Engine 0.9.4     │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

**Key screens / states**: empty (first-week onboarding), loading (skeleton cards), populated, error (data source down — surface a "Run diagnostic" CTA).

**Critical interactions**: every KPI card is clickable → drills into the underlying table with the same filter pre-applied. Time-window switcher (`Today / 7d / 30d / QTD / YTD`). Cmd+K from anywhere.

### S2 · Product Catalog

**Purpose** — A single table of every approved product with UIN, version, status.

**IA** — Full-width table; right-rail "inspector" opens on row click.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  Products · 14 of 14                          [+ New product]  [Filter] [Export ⌄]   │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  ☐  Plan name              UIN          Type         Status      Effective    Owner │
│  ☐  Prudential Domestic    1145H78V01   Domestic     ● Live      2026-04-01   Ria   │
│  ☐  Prudential Flagship    1145H79V02   Flagship     ● Live      2026-04-01   Ria   │
│  ☐  Prudential Senior      1145H80V02   Senior       ● Live      2026-04-01   Anil  │
│  ☐  Prudential Sub-Std     1145H81V01   Sub-Standard ● Live      2026-04-01   Anil  │
│  ☐  Prudential POSP        1145H82V01   POSP         ● Draft     —            Ria   │
│  ☐  Prudential Global      1145H83V03   Global       ● Live      2026-01-15   Vivek │
│  ☐  Prudential Global+     1145H84V01   Global Plus  ◐ In review 2026-07-01   Vivek │
│  …                                                                                    │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

**Key screens / states**: empty (`No products yet — Import from Excel or Create one`), filtered (chips above table), bulk-select with action bar, deep-link `/products?uin=1145H79V02`.

**Right-rail inspector** (on row click, 480px):

```
┌─────────────────────────────────────────────┐
│  Prudential Flagship       [Edit] [Open ▸]   │
│  UIN 1145H79V02  · v3      ● Live           │
│  ─────────────────────────────────────────  │
│  Type                Domestic Flagship      │
│  Geography           India                  │
│  UW category         Standard               │
│  Co-pay table        Omnibus                │
│  Sum insureds        7 (₹3L → ₹1Cr)         │
│  Zones               Z1 · Z2 · Z3           │
│  Family types        7 of 11                │
│  Covers allowed      32 of 54               │
│  Rate-table          rt_flagship_v2026Q2    │
│  Last changed        2026-05-12 · Ria       │
│  ─────────────────────────────────────────  │
│  Quick stats (last 30d)                     │
│    Quotes      4,512    ▲ 8 %               │
│    Premium     ₹6.4Cr   ▲ 11 %              │
│  Open in Configurator ▸                     │
└─────────────────────────────────────────────┘
```

### S3 · Plan Configurator

The "killer" surface. Today it's 6 hardcoded fields. Tomorrow it's a 7-tab editor with autosave, versioning, diff, and approval.

**IA** — Header + tabbed body + right-rail "Quote preview".

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  ◂ Products · Prudential Flagship          ● Draft v4 · autosaved 2s ago   [Publish ▸]│
├──────────────────────────────────────────────────────────────────────────────────────┤
│  [ Overview ] Eligibility  Covers  Rates  Discounts  Rules  Geography  History       │
├──────────────────────────────────────────────────┬───────────────────────────────────┤
│                                                  │  QUOTE PREVIEW                    │
│  Plan name           Prudential Flagship         │  Age 35 · 2A2C · ₹10L · Z2        │
│  Internal ID         flagship                    │  ─────────────────────────────    │
│  IRDAI UIN           1145H79V02         ✓ valid  │  Base                ₹  35,248    │
│  Effective from      2026-04-01                  │  + Add-ons (3)       ₹   5,500    │
│  Effective to        —                           │  + UW load           ₹       0    │
│  Plan type           Domestic Flagship ▾         │  − Discounts         ₹  −2,400    │
│  UW category         Standard ▾                  │  Subtotal            ₹  38,348    │
│  Co-pay table        Omnibus ▾                   │  + 18% GST           ₹   6,903    │
│  Geography scope     India ▾                     │  ─────────────────────────────    │
│  Default GST         18 %                        │  TOTAL               ₹  45,251    │
│  Active              ●━━━━━○                     │                                   │
│                                                  │  Tweak a field on the left to     │
│                                                  │  see this update instantly.       │
└──────────────────────────────────────────────────┴───────────────────────────────────┘
```

**Tabs:**

1. **Overview** — name, UIN, effective dates, type, UW category, co-pay table, geography, GST, active toggle.
2. **Eligibility** — min/max age (with `min < max` enforcement), age bands the plan offers, family types allowed (multi-select chips with previews like `2A2C → 2 adults + 2 children`), gender constraints, occupational risk.
3. **Covers** — the *allowedCoverIds matrix*: 54 covers as toggle chips, grouped by category (Base · Add-ons · Discounts · Riders). Search box. "Default selection" checkbox per cover. Mutual-exclusion warnings inline.
4. **Rates** — read-only summary of the active rate-table version + a link to Rate Table Manager. Showing the 15×13 age-band×SI matrix as a heatmap.
5. **Discounts** — which discounts apply, per-discount caps, mutual exclusions.
6. **Rules** — opens the Business Rules Editor scoped to this plan.
7. **Geography** — zone allowlist, pincode rules, state-level exclusions (e.g., J&K Z2 restriction).
8. **History** — every change, who, when, before/after diff. (See S12 for full audit.)

**Critical interactions:**

- **Autosave** every keystroke (with a small "Saving… / Saved 2s ago / Save failed — retry" indicator).
- **Live quote preview** in the right rail — every edit re-runs the engine on the canonical preview request.
- **Status pill in header**: `Draft / In Review / Approved / Live / Retired` — clicking it shows the workflow.
- **Publish button** opens the 4-eyes flow: pick a reviewer → reviewer gets notified → on approve, version bumps and is promoted.
- **Keyboard**: `g o` overview, `g e` eligibility, `g c` covers, `g r` rates, `⌘S` force-save, `⌘P` open publish flow, `⌘Z/⌘⇧Z` undo/redo at the field level, `Esc` close inspector.

### S4 · Cover Catalog Manager

**Purpose** — The 54 covers (`shared/.../data/CoverCatalog.kt`) need editable metadata: display name, description, Excel column mapping, accumulation bases.

**IA** — Two-pane (list left, inspector right). Top toolbar with filters by category and "broken bases" warning chip.

```
┌──────────────────────┬───────────────────────────────────────────────────────────────┐
│ Filter ▾  Search…    │  HOME_CARE                       ● Used by 6 plans            │
│ ─────────────────    │  ───────────────────────────────────────────────────────────  │
│ ▾ Base               │  Display name         Home Care Treatment                     │
│   · Base premium     │  Excel name(s)        HomeCare, Domiciliary Hosp.             │
│ ▾ Add-ons (32)       │  Category             Add-on                                  │
│   · Modern Tx        │  Type                 Flat / Per-claim                        │
│   · Home Care        │  Param 1              Daily limit (₹) ▾                       │
│   · Maternity        │  Param 2              Max days/year ▾                         │
│   · Dental           │  Accumulation base    R23, R24, R25 ✎                         │
│   · Vision           │  Plans allowing it    Flagship, Senior, Global …              │
│   · …                │  Description          ……                                      │
│ ▾ Discounts (12)     │  ─────────────────────────────────────────────────────────    │
│ ▾ Riders (6)         │  Excel parity test    ● Passing (last run 2 min ago)          │
│                      │  Last modified        2026-04-30 · Anil  ✎                    │
└──────────────────────┴───────────────────────────────────────────────────────────────┘
```

**Critical interactions:**

- "**Used by 6 plans**" is a clickable badge → opens a sheet listing which plans reference this cover.
- "**Excel parity test**" runs the pinned `expected.json` fixture for this cover and shows pass/fail. (Audit DIM-2 §2.7 calls out zero tests; this surface makes pinning canonical.)
- Hovering an accumulation base (e.g. `R23`) reveals the *exact* Excel formula: `=SUM(X23:X40)`.

### S5 · Rate Table Manager

**Purpose** — Today rates are read-only from imported Excel. This surface manages **versioned** rate tables, with diff and approval.

**IA** — List of rate tables (top), selected table as a heatmap (centre), diff drawer (right).

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  Rate tables · 7 of 7                                       [+ New from Excel]       │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  ID                Plan family   Version   Effective    Status      Last changed      │
│  rt_flagship       Flagship      v2026.Q2  2026-04-01   ● Live      2026-04-01 · Anil │
│  rt_flagship       Flagship      v2026.Q3  2026-07-01   ◐ Review    2026-05-12 · Ria  │
│  rt_senior         Senior        v2026.Q2  2026-04-01   ● Live      …                 │
│  rt_global         Global        v2026.Q1  2026-01-15   ● Live      …                 │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  rt_flagship · v2026.Q3                                                              │
│                                                                                       │
│  Heatmap: SI (cols) × Age band (rows) · pre-tax annual base premium per adult         │
│           3L    5L    10L   15L   20L   25L   50L   75L   1Cr                         │
│   5-17  ░░ 1.2K  1.5K  1.8K  2.0K  2.3K  2.8K  3.6K  4.1K  4.6K                       │
│  18-25  ░░ 2.1K  2.6K  3.1K  3.5K  4.0K  4.8K  6.3K  7.2K  8.0K                       │
│  …                                                                                    │
│  56-60  ▓▓ 18K   24K   31K   37K   42K   48K   59K   67K   74K                        │
│  …                                                                                    │
│                                                                                       │
│  Δ vs v2026.Q2: avg +4.8 % · max +12 % at (56-60, 1Cr) · min −1 % at (5-17, 3L)        │
│  [Open diff ▸]                                                                       │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

**Critical interactions:**

- **Heatmap** is the primary affordance — Linear-quality, smooth, hover gives `(band, SI) → ₹value`.
- **Diff drawer** (right): table of every changed cell with `before → after`, percentage delta, conditional formatting (green decrease, amber moderate increase, red increase >10%).
- **Approve** button (visible only to actuaries / pricing committee) triggers the 4-eyes flow.
- **Rollback**: every published version is a frozen snapshot.

### S6 · Discount Manager

**Purpose** — Configure the 12 discount entries: rate, cap, mutual exclusions, eligibility rules, promo windows.

**IA** — Table left, inspector right. Promo strip on top showing "active promotional discounts ending soon".

### S7 · Business Rules Editor

**Purpose** — Validation, mutual exclusions, plan-specific restrictions encoded visually.

**IA** — Rule list left; visual builder right.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  Rules · 47   [+ New rule]                                                           │
├────────────────────────────┬─────────────────────────────────────────────────────────┤
│ ▾ Global                   │  RULE — "Senior plan mandatory co-pay"                  │
│   · GST 18 %               │  ───────────────────────────────────────────────────    │
│   · Discount cap 30 %      │  When                                                   │
│   · Sub-std co-pay floor   │     plan.id = "senior"                                  │
│ ▾ Senior plan              │     AND quote.primaryAge ≥ 61                           │
│   · Mandatory co-pay 20 %  │  Then                                                   │
│   · Max SI ₹50L            │     selectedCovers MUST contain "co_pay" cover          │
│ ▾ POSP                     │     AND co_pay.param ≥ 20 %                             │
│   · Restricted SI grid     │  Else                                                   │
│ ▾ Cover mutual-exclusions  │     reject quote with code RULE_SENIOR_COPAY            │
│   · co_pay ✕ deductible    │  ───────────────────────────────────────────────────    │
│   · employee ✕ commission  │  Severity   ● Block      Owner   Ria                    │
│   …                        │  Last triggered  142 times in last 7d                   │
└────────────────────────────┴─────────────────────────────────────────────────────────┘
```

The visual builder is **Retool-style** drag chips for `When ... Then ... Else`. Power users can flip to "Code view" (a tiny rule-DSL like `plan.id == "senior" && quote.age >= 61 ⇒ require "co_pay"@>=0.20`).

### S8 · Excel Import Studio

**Purpose** — Replace the existing `Import` screen which audit §6.5 calls out as "no dry-run, no diff, no rollback".

**IA** — A linear 5-step wizard:

```
①  Upload      →   ② Parse / Validate   →   ③ Dry-run diff   →   ④ Confirm   →   ⑤ Done
                                                                                  + Rollback
```

Each step's screen is below.

### S9 · Quote Explorer

**Purpose** — Today there is no quote search at all. Audit §6.2 lists *no save-quote, no load-quote, no compare*.

**IA** — Filterable table + side panel + compare mode.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ Quotes  · 12,841 in last 30d                                  [+ New quote]          │
│ Filters: Plan ▾ Zone ▾ Tenure ▾ Status ▾ Date ▾ Agent ▾                              │
├──────────────────────────────────────────────────────────────────────────────────────┤
│ ☐  Quote #     Plan          Family   SI       Tenure  Premium     Created   Agent   │
│ ☐  Q-2A3B…     Flagship      2A2C     ₹10L     3y      ₹1,21,500   2h ago    Ria     │
│ ☐  Q-9C7E…     Senior        1A       ₹25L     1y      ₹  47,800   3h ago    Anil    │
│ …                                                                                     │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

Multi-select two → "Compare" → side-by-side breakdown grid.

### S10 · Underwriting Queue

**Purpose** — UW dashboard with risk score, action buttons.

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  UW Queue · 42 pending     [Assigned to me · 7] [All · 42] [Closed today · 18]      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  #   Customer          SI       PED   BMI   Risk  Status     Aging                  │
│      Riya Sharma       ₹50L     ✓     31    ▓▓░  Pending     2h                     │
│      Mohan Iyer        ₹25L     ✓     —     ▓░░  Pending     6h                     │
│      Aditi Roy         ₹1Cr     —     —     ▓▓▓  Refer to CMO 1d  ●                 │
│  …                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### S11 · Reports & Analytics

**Purpose** — IRDAI MIS reports plus internal business intelligence.

Reports:
1. Premium booked (daily / weekly / monthly)
2. Plan mix
3. Zone mix
4. UW backlog
5. Claim ratio
6. Lapse + revival
7. Agent commission ledger
8. Renewal pipeline

All exportable as CSV / PDF with watermark and IRDAI reporting format.

### S12 · Audit & Governance

**Purpose** — Every change is tracked; 4-eyes approval; chain-of-custody for IRDAI inspection.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  Audit log · 8,123 events                                              [Export CSV]  │
│  Filters: Surface ▾ Actor ▾ Action ▾ Range ▾                                         │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  When                Actor   Surface          Entity              Action  Reason     │
│  05-20 09:42:11      Ria     Rate Table       rt_flagship Q3      ✎ +4%  "Q3 pricing"│
│  05-20 09:15:02      Anil    Cover Catalog    HOME_CARE           ✎       —          │
│  05-20 08:55:33      Vivek   Plan             senior              ▶ pub   "Live 4/1" │
│  …                                                                                    │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

### S13 · Settings

**Purpose** — Org config, users, RBAC, IRDAI artefacts (Prospectus, CIS, T&C), integrations (payment, SMS, KYC).

## P1.4 Primary user flows (Pass-1)

1. **Q1 close-of-quarter rate update.** Actuary → Rate Tables → New from Excel → Upload → diff vs v2026.Q1 → assign approver → approver opens diff → approves → version goes live on the effective date.
2. **Launch new product.** Product head → Products → New product → fills Overview, Eligibility, Covers, Rates link, Rules → Draft saved → publishes after sign-off.
3. **Underwriter morning.** Open → UW queue → My queue → click first case → review risk score → either approve, refer, or load → next case (`J`/`K` keyboard).
4. **Customer service compares two quotes.** Quote Explorer → filter to customer email → select 2 → Compare.
5. **Q-end MIS report.** Reports → Premium booked → date range → export PDF for IRDAI.

## P1.5 What's missing from this pass (we'll fix in Pass 2)

- No defined empty/loading/error states.
- No keyboard system.
- No data-density modes.
- No command palette spec.
- No specific inline vs modal vs drawer rules.
- No table interactions (sort/filter/group/pin/freeze).
- Visual system not specified.

---

# Pass 2 — Depth, Interaction Model, Keyboard, States

> **What I'm fixing from Pass 1.** Pass 1 nailed the surface inventory and the rough IA but it stopped at "here's a wireframe per surface". Real product quality lives in the next layer down: **what does the screen feel like when there's no data, when data is loading, when a save fails, when a user multi-selects 200 rows, when they want to edit without opening a modal?** Pass 2 fixes this. We define the interaction model end-to-end: states, table grammar, keyboard, command palette, density modes, save semantics, edit semantics.

## P2.0 The seven canonical states (every surface implements)

We commit every screen to ship these **seven** states. No more "I forgot the empty state".

| State            | Trigger                                  | Visual                                                             |
|------------------|------------------------------------------|--------------------------------------------------------------------|
| **Empty (first run)** | Zero rows, user has never created one  | Illustration + one-liner + primary CTA + secondary "Import" CTA    |
| **Empty (filtered)**  | Filter narrows to zero                  | Soft empty state + "Clear filter" CTA                              |
| **Loading**           | First paint                             | Skeleton matching the populated layout (no spinner)                |
| **Loading (more)**    | Pagination scroll                       | Bottom 6 rows shimmer                                              |
| **Populated**         | Data present                            | The canonical layout                                               |
| **Error**             | Backend / I/O failure                   | Inline banner + "Retry" + "Run diagnostic"                         |
| **Partial / stale**   | One pane succeeded, another timed out   | The failing pane gets a small banner; the rest is usable           |

Every screen also implements **edit / dirty / saving / saved / saved-with-conflict** sub-states for editable rows.

## P2.1 Table grammar — Aegis's single most important component

The entire system rests on one composable, **`AegisTable`**. Every list view inherits the same grammar:

```
[Header row · sticky]
   ☐  Column A ▾   Column B ▾   Column C ▾   …
[Filter row · optional]
   ☐  [Search…]    [▾ All]      [▾ All]      …
[Toolbar row]                       (rises when ≥1 selected)
   "3 selected"   [Bulk edit] [Export] [Archive] [⌫ Delete]
[Body]
   ☐  cell        cell         cell         …
   ☐  cell        cell         cell         …
[Footer]
   3 of 14,201 · Density [comfort ▾]   Page 1/142  ◀ ▶
```

Capabilities every table has:

1. **Column resize / reorder** — drag the right edge to resize, drag the header to reorder. Persisted per user.
2. **Sort** — click header. ⇧+click for multi-sort. Sort indicator carat shows priority.
3. **Filter** — per-column. Type-aware (range slider for money, multi-select for enums).
4. **Group by** — drag a column header onto the "Group by" zone. Collapsible groups with subtotals.
5. **Pin / freeze first column** — for wide tables (Rate Table heatmap). Cmd+Shift+P pins the focused column.
6. **Multi-select** — click first, Shift+click range, Cmd+click toggle, ⌘A select all on page, ⌘⇧A select all matching filters.
7. **Inline edit** — double-click a cell to edit. Tab/Shift+Tab to move. Enter to commit. Esc to revert. Optimistic write with toast on failure.
8. **Row actions on hover** — three small icons at the right edge of the row appear on hover.
9. **Right-click context menu** — bulk actions on selection, or single-row actions if nothing is selected.
10. **Density** — comfortable (52px) / compact (36px) / mini (28px) toggle in toolbar; persisted globally.
11. **Virtualised** — 100K rows scroll smoothly with windowing.

ASCII of comfortable vs compact vs mini:

```
COMFORTABLE   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
              ☐  Riya Sharma          riya@example.com         +91 98... ⋯
              ☐  Mohan Iyer           mohan@example.com        +91 80... ⋯

COMPACT       ───────────────────────────────────────────────────────────────
              ☐  Riya Sharma     riya@example.com     +91 98...   ⋯
              ☐  Mohan Iyer      mohan@example.com    +91 80...   ⋯

MINI          ───────────────────────────────────────────────────────────────
              ☐ Riya  riya@example.com  +91 98… ⋯
              ☐ Mohan mohan@example.com +91 80… ⋯
```

## P2.2 Inline edit vs side drawer vs full sheet vs modal — the rule

The decision tree:

1. **Edits ≤ 2 fields** → inline edit on the row. (e.g., toggle `isActive`, change `effectiveTo`.)
2. **Edits ≤ 8 fields, no relationships** → **right drawer** (480px). (e.g., edit a discount's rate + cap.)
3. **Deep edits with relationships** → **full sheet** that replaces the workspace. (e.g., Plan Configurator.)
4. **Destructive confirmation or single decision** → centre **modal** (max 480px). (e.g., "Publish v2026.Q3?")
5. **Bottom sheet** is reserved for *contextual pickers* (e.g., emoji-style cover picker in mobile/tablet).

Modals are rare. Drawers are common. Sheets are deep.

## P2.3 Save semantics: autosave + diff banner

Every editable surface autosaves on every keystroke (debounced 300ms). The header shows:

```
● Draft v4 · autosaved 2s ago        (idle)
● Draft v4 · Saving…                 (in-flight)
● Draft v4 · Save failed — Retry     (error)
⚠ Conflict — another user edited     (multi-user)
```

For changes that affect *live* plans we never autosave to live. Drafts autosave; promotion to live requires a **Publish** action with confirmation and 4-eyes.

**Undo / Redo**: per-field, per-surface. `⌘Z` and `⌘⇧Z`. The toast on every save also has a one-click "Undo" for 5 seconds (Linear-style).

## P2.4 Keyboard system — Linear-grade

Global:

| Key            | Action                                     |
|----------------|--------------------------------------------|
| `⌘K`           | Command palette                            |
| `⌘/`           | Show all shortcuts                         |
| `⌘\\`          | Toggle left rail                           |
| `⌘B`           | Toggle right rail                          |
| `⌘N`           | New (context-aware — new plan / quote …)   |
| `⌘S`           | Force save                                 |
| `⌘P`           | Publish flow                               |
| `⌘F`           | Focus search box                           |
| `⌘E`           | Export                                     |
| `⌘Z` / `⌘⇧Z`   | Undo / Redo                                |
| `Esc`          | Close drawer / sheet / modal               |
| `g h`          | Go Home                                    |
| `g p`          | Go Products                                |
| `g c`          | Go Configurator                            |
| `g r`          | Go Rates                                   |
| `g d`          | Go Discounts                               |
| `g q`          | Go Quote Explorer                          |
| `g u`          | Go UW Queue                                |
| `g i`          | Go Import                                  |
| `g a`          | Go Audit                                   |
| `?`            | Help overlay                               |

Tables:

| Key            | Action                                     |
|----------------|--------------------------------------------|
| `J` / `K`      | Next / previous row                        |
| `H` / `L`      | Previous / next column                     |
| `Space`        | Toggle selection                           |
| `Shift+J/K`    | Extend selection                           |
| `⌘A`           | Select all on page                         |
| `⌘⇧A`          | Select all matching filter                 |
| `Enter`        | Open row in drawer                         |
| `O`            | Open row in full sheet                     |
| `E`            | Inline-edit first editable cell            |
| `D`            | Duplicate row                              |
| `⌫`            | Archive (with undo toast)                  |
| `R`            | Refresh                                    |

Configurator tabs: `g o / g e / g c / g r / g d / g x / g g / g h` for Overview / Eligibility / Covers / Rates / Discounts / Rules / Geography / History.

## P2.5 Command palette (`⌘K`) — Linear/Raycast quality

```
┌──────────────────────────────────────────────────────────────────────┐
│  ⌘K  Type a command, plan name, UIN, or quote…                       │
│                                                                       │
│  RECENT                                                               │
│    ▸ Rate Table · rt_flagship · v2026.Q3                              │
│    ▸ Plan · Prudential Senior                                         │
│                                                                       │
│  ACTIONS                                                              │
│    ▸ New plan          ⌘N                                            │
│    ▸ Import Excel      g i                                           │
│    ▸ Publish current   ⌘P                                            │
│    ▸ Open UW queue     g u                                           │
│                                                                       │
│  NAVIGATE                                                             │
│    ▸ Home              g h                                           │
│    ▸ Products          g p                                           │
│    ▸ Configurator      g c                                           │
│                                                                       │
│  SEARCH                                                               │
│    ▸ "1145H79V02" — Prudential Flagship                              │
│    ▸ "Q-2A3B" — Quote, ₹1,21,500, 3y                                 │
└──────────────────────────────────────────────────────────────────────┘
```

Fuzzy search across **plans, quotes, covers, discounts, rules, rate tables, recent activity, users, settings, help articles, audit events**. Type-ahead with weighted scoring. Recent items pinned.

## P2.6 Re-spec'd surfaces with all states

### S1 Home Dashboard — Pass 2 spec

**Empty state** (week 1, no data yet):

```
┌─────────────────────────────────────────────────────────────────────┐
│  Welcome to Aegis 👋                                                 │
│  ──────────────────                                                 │
│  Your dashboard is empty because no quotes have run yet.            │
│                                                                     │
│  Get started:                                                       │
│    1. Import your Excel rate workbook       [Import Excel ▸]        │
│    2. Verify the plan catalog               [Open Products ▸]       │
│    3. Calculate your first quote            [New quote ▸]           │
│                                                                     │
│  Tour the product in 90 seconds → [Play ▸]                          │
└─────────────────────────────────────────────────────────────────────┘
```

**Loading** — Skeleton cards (animated shimmer 1.4s). No spinner.

**Error** — Inline banner across the top: `Some KPI tiles couldn't load — Engine timeout. Retry  Diagnostic`.

**Populated** — Pass-1 wireframe holds. Each KPI card has a **Δ vs window** chip and a sparkline. Hovering the sparkline shows daily values.

### S3 Plan Configurator — Pass 2 deeper spec

**State machine for the page header:**

```
   ● Live (read-only)
      │
      │  user clicks "Edit"
      ▼
   ● Draft v(n+1) (autosaving)
      │
      │  user clicks "Submit for review"
      ▼
   ◐ In review (locked, reviewer chip visible)
      │             ┌── reviewer rejects ──► back to Draft
      │             │
      │  reviewer approves
      ▼
   ▶ Scheduled (effectiveFrom in future)
      │
      │  effectiveFrom reached
      ▼
   ● Live  ── next edit cycle ──┘
```

**Covers tab — interactive depth:**

```
┌──────────────────────────────────────────────────────────────────────┐
│  Covers                          [Search 54…]  [▾ Category]  [▾ Used]│
├──────────────────────────────────────────────────────────────────────┤
│  BASE                                                                │
│    [✓ Base Premium]   default ✓                                      │
│  ADD-ONS                                                             │
│    [✓ Home Care]      default —    params: ₹2,500 / 30 days ✎        │
│    [✓ Maternity]      default —    params: 24 mo waiting ✎           │
│    [✗ Dental]         default —                                      │
│    [✓ Modern Tx]      default ✓                                      │
│    …                                                                 │
│  DISCOUNTS                                                           │
│    [✓ Smart Select]   max 5 %                                        │
│    [✓ Per-Claim Ded.] max 25 %                                       │
│    [✗ Aggregate Ded.] (incompatible with Per-Claim Ded.)  ⚠         │
│    …                                                                 │
│                                                                       │
│  ⚠ 1 conflict · Aggregate Deductible cannot coexist with Per-Claim   │
└──────────────────────────────────────────────────────────────────────┘
```

The mutual-exclusion check fires **live** in the UI (audit §6.3 calls out that it only fires on "Calculate" today).

### S5 Rate Table Manager — Pass 2 deeper spec

**Diff drawer**:

```
┌─────────────────────────────────────────────────────────────────────┐
│  rt_flagship · v2026.Q2 → v2026.Q3                       [Close ✕] │
│  ─────────────────────────────────────────────────────────────────  │
│  Summary                                                            │
│    142 cells changed  ·  avg Δ +4.8 %  ·  max Δ +12 %               │
│    Range of Δ:  −1 %  ──────────●─────  +12 %                       │
│                                                                     │
│  Largest increases                                                  │
│    (56-60, 1Cr)    74,000 → 82,880    +12 %  ▲                      │
│    (61-65, 1Cr)    93,200 → 102,520   +10 %  ▲                      │
│    (51-55, 75L)    62,000 → 67,580    +9 %   ▲                      │
│                                                                     │
│  Decreases                                                          │
│    (5-17, 3L)      1,250 → 1,237      −1 %   ▼                      │
│                                                                     │
│  Approval                                                           │
│    Maker      Ria        2026-05-12 10:42                           │
│    Checker    ◯ (pending)                                            │
│                                                                     │
│  [Approve & schedule for 2026-07-01]   [Reject with comment]        │
└─────────────────────────────────────────────────────────────────────┘
```

### S8 Excel Import Studio — Pass 2 wizard

**Step 1: Upload.**

```
┌─────────────────────────────────────────────────────────────────────┐
│  Step 1 of 5 · Upload                                               │
│  ─────────                                                          │
│  Drop your Excel workbook here or [Browse]                          │
│  Allowed: .xlsx · ≤ 50 MB                                           │
│                                                                     │
│  Recent imports                                                     │
│    rate_FY2026Q2_final.xlsx        2 days ago    Ria                │
│    rate_FY2026Q1_v3.xlsx           14 days ago   Anil               │
└─────────────────────────────────────────────────────────────────────┘
```

**Step 2: Parse / Validate.** Parsed sheets listed with per-sheet status pills:

```
Sheet                       Rows  Status            Notes
Plans                        14   ✓ OK
RatesFlagship               1,250 ✓ OK
RatesSenior                 1,250 ⚠ 3 warnings      "Age 86+ row blank"
Discounts                    12   ✓ OK
CoverMappings               54    ✗ Error            "HOME_CARE base R23 missing"
```

Click a warning/error → inspector with the exact cell, value, expected.

**Step 3: Dry-run diff.** Same diff UI as Rate Table Manager but scoped to this import.

**Step 4: Confirm.** Big checkbox: *"I have reviewed the diff and confirm this should go live."* + reviewer assignment.

**Step 5: Done.** Success state with "Imported as snapshot snap_2026-05-20_1042. Rollback available for 30 days."

**Rollback** is its own surface inside Settings → Snapshots.

### S9 Quote Explorer — Pass 2 deeper spec

**Compare mode** (two rows selected, "Compare" pressed):

```
┌──────────────────────────────────────────────────────────────────────┐
│  Compare Q-2A3B and Q-9C7E                                          │
│  ────────────────────────────────────────────────────────────────── │
│                              Q-2A3B               Q-9C7E             │
│  Plan                        Flagship             Senior             │
│  Family                      2A2C                 1A                 │
│  SI                          ₹10L                 ₹25L               │
│  Tenure                      3y                   1y                 │
│  Base premium                ₹  98,500            ₹  41,200          │
│  Add-ons                     ₹  16,500            ₹   3,200          │
│  UW load                     ₹       0            ₹       0          │
│  Discounts                   ₹ −7,400             ₹       0          │
│  Subtotal                    ₹ 107,600            ₹  44,400          │
│  GST 18 %                    ₹  19,368            ₹   7,992          │
│  ──────────────              ─────────            ─────────          │
│  TOTAL                       ₹ 126,968            ₹  52,392          │
│                                                                       │
│  Δ Total                     +₹74,576                                │
└──────────────────────────────────────────────────────────────────────┘
```

## P2.7 What this pass still lacks (for Pass 3)

- No insurance-domain mechanics: UIN registry, effective-date controls, 4-eyes workflow proper, accumulation-base inspector.
- No actuary-specific tooling.
- No regulatory artefact attachment.

---

# Pass 3 — Insurance-Domain Depth (IRDAI, Actuary, Compliance)

> **What I'm fixing from Pass 2.** Pass 2 made the *generic* dashboard great. But this isn't a generic SaaS; it's an Indian health insurance product control plane. Pass 3 layers in IRDAI compliance, actuarial discipline, and the regulatory workflows the audit (§7.1–§7.10, §1.3, §1.9) screams about: UIN registry, effective-date controls, 4-eyes (maker-checker) approval, audit trail per cell, plan-cover allowedCoverIds matrix editor, business rules visual builder, accumulation-base inspector showing the actual Excel formula behind each cover, Master Circular 2024 compliance covers (AYUSH, Mental Health, HIV).

## P3.1 The IRDAI Registry (added inside Settings → IRDAI Registry)

Every approved product variant has a 4-digit UIN. Pass-1 added a `UIN` column to Product Catalog; Pass 3 makes the **UIN the primary key for an external registry** that the product references.

```
┌───────────────────────────────────────────────────────────────────────────┐
│ IRDAI Registry                                                            │
│  ───────────                                                              │
│  UIN          Insurer         Product             Approval     File on FS │
│  1145H79V02   Prudential      Flagship            2025-12-15   ▾ PDFs (5) │
│  1145H80V02   Prudential      Senior              2025-12-15   ▾ PDFs (5) │
│  1145H82V01   Prudential      POSP                2026-02-10   ◯ pending  │
│  …                                                                         │
└───────────────────────────────────────────────────────────────────────────┘
```

Each UIN has attached:

- **Prospectus** PDF
- **Customer Information Sheet (CIS)** PDF
- **Policy Wording / T&C** PDF
- **Sales Illustration** template
- **Health Declaration Form** template
- **Need Analysis Questionnaire** template

Plus metadata:

- Approval date
- Approval letter reference
- Free-look period (default 15 days)
- Grievance ombudsman pointer
- DPDP DPO contact

The Plan Configurator's **Overview** tab forces UIN selection from the registry. Without a UIN, the plan cannot be promoted past Draft.

## P3.2 Effective dates and rate-table versioning — first-class

Today, rate data is "the current rate". After Pass 3:

- Every **Plan** has `effectiveFrom`, `effectiveTo`.
- Every **Rate Table** version has `effectiveFrom`, `effectiveTo`.
- Every **Cover** has `effectiveFrom`, `effectiveTo`.
- Every **Discount** has `effectiveFrom`, `effectiveTo` plus a promo window.

The pricing engine picks the version active on `quote.calculatedAt`. Past quotes are *reproducible*: a quote calculated on 2026-05-19 keeps a snapshot reference `(rateTableVersionId, engineVersion)` so re-running it yields the same number forever. (Audit §1.11 — `calculatedAt`, `engineVersion`, `rateTableVersion` must exist in `QuoteResult`.)

### Effective-date control widget

```
┌───────────────────────────────────────────────────────────────────────────┐
│ Effective                                                                  │
│ ──────────                                                                 │
│   From  [ 2026-07-01 ]  📅                                                 │
│   To    [ ∞ leave blank for indefinite ]                                   │
│                                                                            │
│   ●─────────────●───●●─────────────────────●─────                          │
│   v2026.Q1     v2026.Q2 — Live   v2026.Q3 — Scheduled                      │
│   2026-01-15   2026-04-01        2026-07-01                                │
│                                                                            │
│   ⓘ This version overlaps with v2026.Q2.                                   │
│     v2026.Q2 will be retired on 2026-06-30.                                │
└───────────────────────────────────────────────────────────────────────────┘
```

A **timeline** showing past, current, scheduled versions. Conflict detection runs live.

## P3.3 4-eyes (maker-checker) approval workflow

IRDAI / SOX / internal audit all require segregation. Every consequential change goes through:

```
   draft (maker)  ─►  submit for review (locks)
                       │
                       ▼
                 in review (checker)
                       │
       reject ◄────────┤
       (back to       │
        draft)        ▼
                  approved
                       │
                       ▼
                  scheduled (if effectiveFrom in future)
                       │
                       ▼
                       live
```

**The Approval drawer** when "Submit for review" is clicked:

```
┌─────────────────────────────────────────────────────────────────────┐
│  Submit "rt_flagship v2026.Q3" for review               [Close ✕] │
│  ─────────────────────────────────────────────────────────────────  │
│  Maker          You (Ria Mehta · ria@pru.com)  ●                    │
│  Reviewer       [Anil Mehrotra ▾]                                    │
│  Effective from [2026-07-01]                                         │
│  Reason for change                                                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ Q3 actuarial review: +4.8 % avg on 56–60 band per CMO memo  │   │
│  │ dated 2026-05-10. Loss ratio is at 78.4 % — needs +5 %.     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  Attach evidence   [memo.pdf]  [+ Add file]                          │
│  ───────────────────────────────────────────────────────────────    │
│  Pre-flight checks                                                  │
│  ✓ No empty cells                                                   │
│  ✓ All age-bands × SI present                                       │
│  ✓ Diff ≤ ±25 % per cell (committee guard-rail)                     │
│  ⚠ One cell at +12 % (61-65, 1Cr)                                   │
│                                                                      │
│  [Cancel]                                       [Send to Anil ▸]    │
└─────────────────────────────────────────────────────────────────────┘
```

**Reviewer view** (Anil opens the link from the email / notification):

```
┌─────────────────────────────────────────────────────────────────────┐
│  ◐ Review · rt_flagship v2026.Q3                                    │
│  ─────────                                                          │
│  Submitted by Ria · 2 min ago                                       │
│  Reason: "Q3 actuarial review: +4.8 % avg…"  [evidence: memo.pdf]   │
│                                                                     │
│  [Open diff ▸]                                                      │
│  [Open quote-impact simulator ▸]                                    │
│                                                                     │
│  Decision                                                           │
│    ◯ Approve                                                        │
│    ◯ Reject with comment                                            │
│    ◯ Request changes                                                │
│  Comment                                                            │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  [Submit decision ▸]                                                │
└─────────────────────────────────────────────────────────────────────┘
```

**Quote-impact simulator** — Pass-4 will go deep on this, but the seed is here: the reviewer can re-run a *fixed test bench* of 100 canonical quotes against the new rate table and see the population-level delta before approving.

## P3.4 Plan × Cover allowedCoverIds matrix editor

Audit §1.9: *Plan allowedCoverIds is empty for every plan in code — meaning all 50+ covers available on every plan. Likely violates IRDAI product approvals.*

We need a **matrix editor** scoped to a plan:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Covers allowed on this plan                          [Bulk select ▾]       │
│ ──────────────────────────────                                             │
│ ✓ Selected 32 of 54   ⚠ 2 default ✓ covers below conflict with each other   │
│                                                                            │
│  Category    Cover                       Allowed   Default   Conflicts     │
│  Base        Base premium                 ✓ on      ✓ on     —             │
│  Add-on      Home Care                    ✓ on        —      —             │
│  Add-on      Maternity                    ✓ on        —      —             │
│  Add-on      Modern Tx                    ✓ on      ✓ on     —             │
│  Add-on      AYUSH Treatment              ⚠ off       —      ⓘ Master 2024 │
│  Discount    Smart Select                 ✓ on        —      —             │
│  Discount    Per-Claim Deductible         ✓ on        —      ⚡ Agg.Ded    │
│  Discount    Aggregate Deductible         ✓ on        —      ⚡ P/C Ded.   │
│  …                                                                         │
└────────────────────────────────────────────────────────────────────────────┘
```

Where the matrix flags **regulatory compliance gaps** (AYUSH, Mental Health, HIV/AIDS, Modern Treatments — IRDAI Master Circular 2024 §7.8 of audit).

## P3.5 Accumulation-base inspector — the actuary's microscope

Audit §1.3 says `COVER_ACCUM_BASES` is the single most fragile object in the system. Pass 3 introduces a UI for it.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Accumulation base · R23  ·  "PHI Flagship 2A2C Z2"                      │
│  ──────────────────────────────────────────────────────────────────────  │
│  Excel formula (read-only)                                               │
│    =SUM(X23:X40)                                                         │
│                                                                          │
│  Covers included (17)                                                    │
│    × HOME_CARE        ₹ 167  (flat)                                      │
│    × MATERNITY        ₹ 425  (per ₹100k SI)                              │
│    × MODERN_TX        ₹ 1,250                                            │
│    …                                                                     │
│                                                                          │
│  Covers excluded (37)                                                    │
│    DURABLE_MEDICAL    excluded per actuary memo 2025-11-12               │
│    …                                                                     │
│                                                                          │
│  Tests pinned to this base                                               │
│    ✓ Golden fixture · 2A2C-Z2-10L                                        │
│    ✓ Golden fixture · 2A-Z1-25L                                          │
│    ⓘ Add another fixture →                                              │
│                                                                          │
│  Last modified  2026-04-30 · Anil · "Excel parity verified"              │
└──────────────────────────────────────────────────────────────────────────┘
```

Every cell of the formula is hover-explorable. Clicking a cover row jumps to its Cover-Catalog page.

## P3.6 Business Rules Editor — Pass 3 depth

Beyond the basic visual builder, the rules editor now ships:

1. **Rule packs** — bundle rules together (e.g., *Master Circular 2024 pack*, *Senior pack*, *POSP pack*) for atomic enable/disable.
2. **Test bench per rule** — every rule has a tab "Test cases" that pins canonical quotes and asserts the rule's verdict.
3. **Severity levels** — `Block` (reject quote), `Warn` (require UW review), `Inform` (display to agent only).
4. **Telemetry** — every rule shows how often it fired in the last 7 days; rules that never fire become "stale" and prompt for review.

The 47 rules the audit demands:

| #  | Rule (informally)                                          | Severity |
|----|-------------------------------------------------------------|----------|
| 1  | GST 18 % everywhere                                         | Block    |
| 2  | Discount cap 30 % (capped pool)                             | Block    |
| 3  | Senior plan: mandatory co-pay ≥ 20 %                        | Block    |
| 4  | Sub-standard: co-pay floor                                  | Block    |
| 5  | POSP: SI ∈ {₹3L, ₹5L, ₹10L}                                 | Block    |
| 6  | Global: SI ≥ ₹50L                                           | Block    |
| 7  | Co-pay ✕ per-claim deductible mutual exclusion              | Block    |
| 8  | Per-claim ✕ aggregate deductible mutual exclusion           | Block    |
| 9  | Employee ✕ commission-in-lieu mutual exclusion              | Block    |
| 10 | Age > plan.maxAge                                           | Block    |
| 11 | SI ∉ plan.availableSumInsureds                              | Block    |
| 12 | Zone ∉ plan.availableZones                                  | Block    |
| 13 | familyType ∉ plan.availableFamilyTypes                      | Block    |
| 14 | paymentTenure > tenure                                      | Block    |
| 15 | plan.isActive = false                                       | Block    |
| 16 | Members count ≠ familyType.totalMembers                     | Block    |
| 17 | Child age ≥ 18                                              | Block    |
| 18 | Spouse age gap > 15 yrs                                     | Warn     |
| 19 | Maternity for male-only families                            | Warn     |
| 20 | UW load > 100 %                                             | Block    |
| 21 | UW load < 0 %                                               | Block    |
| 22 | BMI > 30 — auto UW refer                                    | Warn     |
| 23 | Age > 60 + PED — auto UW refer                              | Warn     |
| 24 | SI > ₹50L — auto UW refer                                   | Warn     |
| 25 | PED + age > 65 + SI > ₹25L — auto UW refer to CMO           | Block    |
| 26 | NCB tracking: prior-claims-disclosure required at renewal    | Block    |
| 27 | Free-look period: 15 days mandatory                          | Inform   |
| 28 | Master 2024: AYUSH cover present                             | Warn     |
| 29 | Master 2024: Mental Health cover present                     | Warn     |
| 30 | Master 2024: HIV/AIDS cover present                          | Warn     |
| 31 | Premium ≤ ₹ 0                                                | Block    |
| 32 | Effective dates overlap                                      | Block    |
| 33 | Pincode → zone mapping resolves                              | Block    |
| 34 | Pre-existing condition: ICD code required                    | Warn     |
| 35 | Critical Illness: ICD code required                          | Warn     |
| 36 | Health declaration form attached                             | Block    |
| 37 | Aadhaar masked in proposal                                   | Block    |
| 38 | PAN masked in proposal                                       | Block    |
| 39 | DPDP consent captured                                        | Block    |
| 40 | Renewal: NCB applied correctly                               | Block    |
| 41 | Portability: 21A window respected                            | Block    |
| 42 | Endorsement: only allowed fields editable                    | Block    |
| 43 | Mid-year enrollment: pro-rata applied                        | Block    |
| 44 | Lapse: grace period 30 days                                  | Block    |
| 45 | Revival: medical re-checks for ≥ 90 days lapsed              | Block    |
| 46 | Plan duplicated: UIN must differ                             | Block    |
| 47 | Agent commission ≤ regulatory cap                            | Block    |

## P3.7 Underwriting Queue — Pass 3 depth

Add structured **risk score** with reason chips:

```
┌────────────────────────────────────────────────────────────────────────────┐
│  Aditi Roy · age 64 · 1A2C · ₹1Cr SI · Flagship                            │
│  Risk score  84 / 100  ▓▓▓▓▓▓▓▓░░                                          │
│                                                                            │
│  Reasons                                                                   │
│    + 18  PED · I10 Hypertension (severity moderate, 6 yrs, controlled)     │
│    + 12  Critical Illness disclosed · C50 Breast cancer (2018, remission)  │
│    + 22  Age 64 + SI > ₹50L (auto-refer rule 24, 25)                       │
│    + 32  BMI 32.4 (auto-refer rule 22)                                     │
│                                                                            │
│  Suggested action  Refer to CMO  ▾                                         │
│                                                                            │
│  Decision                                                                  │
│    ◯ Accept standard                                                       │
│    ◯ Accept with loading [   %]  + reason [▾]                              │
│    ◯ Refer to CMO                                                          │
│    ◯ Decline                                                               │
│                                                                            │
│  Notes                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐         │
│  │                                                              │         │
│  └─────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  [Save & next ▸]                                                           │
└────────────────────────────────────────────────────────────────────────────┘
```

The form is **structured** (ICD-10 codes, severity, year, treatment status, medications) — audit §7.3 demands this.

## P3.8 Lifecycle features (NCB, renewal, portability, endorsement, claim, free-look)

Audit §7.4 lists these as missing. Pass 3 says: *they get first-class IA slots, even if engine isn't done.* The dashboard surfaces become the design forcing-function that pulls the engine work.

Under Operations:

```
Operations
  · Quotes
  · UW Queue
  · Renewals           NEW
  · Portability        NEW
  · Endorsements       NEW
  · Claims             NEW
  · Import
```

Each is a list view that follows the same Aegis-table grammar.

### Renewals

Columns: Policy, Customer, Expiry, Renewal status (`Notified · Reminded · Paid · Lapsed`), NCB rate, Renewal premium, Action.

### Portability (Section 21A)

Columns: Policy, Source insurer, Source UIN, Continuity period, Status, Action.

### Endorsements

Columns: Endorsement #, Type (`Add member · Increase SI · Change address`), Policy, Status (`Proposed · Approved · Rejected · Applied`), Pro-rata premium, Action.

### Claims

Columns: Claim #, Policy, Hospital, Pre-auth status, Final settlement amount, Status (`Notified · Pre-auth · Under processing · Settled · Repudiated`), Action.

## P3.9 What's still missing (for Pass 4)

- Visual system (colours, type, spacing) not specified to spec level.
- Chart catalogue not defined.
- Money formatting (Indian) not specified.
- Status pill colours not committed.
- No hero data viz on Home (Sankey for premium flow).

---

# Pass 4 — Visual System, Chart Catalogue, Money & Status Conventions

> **What I'm fixing from Pass 3.** Pass 3 nailed domain depth but skirted the *visual* commitment. We now lock the design system at production-spec level: colour ramps, type scale, spacing, elevation, icon system, motion, charts, money rendering, and a hero Sankey for the Home dashboard. Everything that follows is reproducible by an engineer.

## P4.1 Colour system

Light theme only. Six families.

### P4.1.1 Neutral (Slate) — the workhorse

12-step ramp tuned for light backgrounds. Hex values are the target; AA contrast against `--bg-canvas` (Slate-0) is guaranteed from Slate-7 upward.

| Token        | Hex      | Usage                                |
|--------------|----------|--------------------------------------|
| `slate-0`    | `#FAFBFC`| Page canvas                          |
| `slate-1`    | `#F4F6F8`| Card subtle bg                       |
| `slate-2`    | `#EBEEF2`| Hover row, dividers                  |
| `slate-3`    | `#DDE2E8`| Borders, inputs                      |
| `slate-4`    | `#C2C9D2`| Hairline, disabled bg                |
| `slate-5`    | `#A2ABB7`| Tertiary icons                       |
| `slate-6`    | `#7C8593`| Disabled text                        |
| `slate-7`    | `#5C6573`| Secondary text                       |
| `slate-8`    | `#404754`| Body text, table cells               |
| `slate-9`    | `#2E3441`| Headings                             |
| `slate-10`   | `#1E2330`| Display heads                        |
| `slate-11`   | `#0E1218`| Inky text (rare)                     |

### P4.1.2 Brand / hero accent — Indigo

We move *away* from PruRed dominance (the legacy buyonline app keeps PruRed; the **business dashboard** uses a sober, trust-coded indigo so it's distinct from the customer-facing journey).

| Token         | Hex      | Usage                              |
|---------------|----------|------------------------------------|
| `indigo-50`   | `#EEF2FF`| Tint bg                            |
| `indigo-100`  | `#E0E7FF`| Selected row, focus ring outer     |
| `indigo-500`  | `#4F46E5`| Primary buttons, links, active     |
| `indigo-600`  | `#4338CA`| Hover                              |
| `indigo-700`  | `#3730A3`| Pressed                            |

### P4.1.3 Semantic

| Family       | 50         | 100        | 500       | 700        | Usage                              |
|--------------|------------|------------|-----------|------------|------------------------------------|
| Success      | `#ECFDF5`  | `#D1FAE5`  | `#10B981` | `#047857`  | Live, approved, positive Δ         |
| Warning      | `#FFFBEB`  | `#FEF3C7`  | `#F59E0B` | `#B45309`  | Warn rules, in-review              |
| Danger       | `#FEF2F2`  | `#FEE2E2`  | `#EF4444` | `#B91C1C`  | Block rules, destructive           |
| Info         | `#EFF6FF`  | `#DBEAFE`  | `#3B82F6` | `#1D4ED8`  | Informational banners              |
| Premium*     | `#FDF4FF`  | `#FAE8FF`  | `#A855F7` | `#7E22CE`  | Money column highlights, hero KPI  |

`* Premium` is the *one* place we let purple in — for the headline money KPI on the Home dashboard.

### P4.1.4 Money colours (Indian convention)

We never colour money digits themselves. Money is always `slate-9`. Movement (Δ) is coloured:

- `Δ < 0` (decrease) → `success-700` with `▼` glyph
- `Δ > 0` (increase) → `danger-700` with `▲` glyph *for cost lines* (loss ratio etc.)
- `Δ > 0` (increase) → `success-700` with `▲` glyph *for revenue lines* (premium booked)

Because the colour depends on context (revenue vs cost), we encode it via the cell's "polarity" prop: `polarity={"revenue" | "cost"}`. Never auto-colour money lines without a polarity.

## P4.2 Typography

Two families:

- **UI / body / heads**  → **Inter** (variable, fallback `-apple-system`, `Segoe UI`, `Roboto`)
- **Mono / numbers / code** → **JetBrains Mono** (variable, fallback `SF Mono`, `Consolas`)

JetBrains Mono is set to **tabular figures** for every money column. The mono is also used in cells showing UIN, IDs, codes.

### Type scale (modular, 1.250 ratio anchored at 14px body)

| Token            | Px / Line     | Weight   | Tracking  | Use                                  |
|------------------|---------------|----------|-----------|--------------------------------------|
| `display-xl`     | 56 / 60       | 600      | −0.02em   | Hero KPI on Home                     |
| `display-lg`     | 40 / 48       | 600      | −0.02em   | Big numbers (rare)                   |
| `head-1`         | 28 / 36       | 600      | −0.01em   | Page titles                          |
| `head-2`         | 20 / 28       | 600      | −0.005em  | Card titles                          |
| `head-3`         | 16 / 24       | 600      | normal    | Section titles                       |
| `body-lg`        | 16 / 24       | 400      | normal    | Reading body                         |
| `body`           | 14 / 20       | 400      | normal    | Default UI                           |
| `body-sm`        | 13 / 18       | 400      | normal    | Table cell, sidebar                  |
| `caption`        | 12 / 16       | 500      | 0.01em    | Labels, helper text                  |
| `micro`          | 11 / 14       | 600      | 0.04em    | Status pills, kbd, badges (UPPER)    |
| `num-lg`         | 28 / 32       | 600 mono | tabular   | KPI big number                       |
| `num`            | 14 / 20       | 500 mono | tabular   | Money in table cells                 |
| `num-sm`         | 13 / 18       | 500 mono | tabular   | Money small contexts                 |

## P4.3 Spacing

4-px base. Tokens: `space-1 (4) · 2 (8) · 3 (12) · 4 (16) · 5 (24) · 6 (32) · 7 (48) · 8 (64) · 9 (96)`.

Layout rules:

- Vertical rhythm in dense tables: row 36px (compact) / 52px (comfortable) / 28px (mini).
- Card padding: `24px` outer, `16px` between sections.
- Drawer width: `480px` (single column) / `640px` (with diff).
- Side rail: `240px` expanded, `64px` collapsed.

## P4.4 Elevation (5 tiers)

| Tier | Shadow                                                  | Use                                |
|------|---------------------------------------------------------|------------------------------------|
| 0    | none                                                    | Flat surfaces                      |
| 1    | `0 1px 2px rgba(15,23,42,.04)`                          | Cards on canvas                    |
| 2    | `0 4px 8px rgba(15,23,42,.06), 0 1px 2px rgba(.,.04)`   | Floating toolbar                   |
| 3    | `0 12px 24px rgba(15,23,42,.08), 0 2px 4px rgba(.,.04)` | Drawer, dropdown                   |
| 4    | `0 24px 48px rgba(15,23,42,.12), 0 4px 8px rgba(.,.05)` | Modal, command palette             |
| 5    | `0 32px 64px rgba(15,23,42,.16)`                        | Tooltip on top of modal (rare)     |

Borders before shadows on critical surfaces — light theme reads better with hairlines (`1px slate-3`) than with thick shadows.

## P4.5 Iconography

**Lucide** is committed. 1.5px stroke. 16/20/24 sizes. We pin specific icons to specific actions to avoid drift:

| Action         | Icon                |
|----------------|---------------------|
| Add            | `plus`              |
| Edit           | `pencil`            |
| Save           | `check`             |
| Delete         | `trash-2`           |
| Duplicate      | `copy`              |
| Publish        | `rocket`            |
| Approve        | `check-circle-2`    |
| Reject         | `x-circle`          |
| Diff           | `git-compare`       |
| Version        | `git-branch`        |
| Audit          | `clipboard-list`    |
| Rules          | `gavel`             |
| Cover          | `shield-check`      |
| Rate           | `calculator`        |
| Discount       | `tag`               |
| UW             | `stethoscope`       |
| Quote          | `file-text`         |
| Import         | `upload-cloud`      |
| Snapshot       | `camera`            |
| Help           | `life-buoy`         |
| Filter         | `sliders-horizontal`|

## P4.6 Status pills — committed colour mapping

Status pills are mini-tokens. The same plan → status mapping must be used everywhere (Catalog row, Configurator header, Audit log).

| Status            | Pill bg         | Pill text       | Dot      | When                                   |
|-------------------|-----------------|-----------------|----------|----------------------------------------|
| `Draft`           | `slate-1`       | `slate-8`       | `slate-5`| Created, never published               |
| `In review`       | `warning-50`    | `warning-700`   | `warning-500` | Maker submitted, checker pending |
| `Scheduled`       | `info-50`       | `info-700`      | `info-500`    | Approved, future effectiveFrom   |
| `Live`            | `success-50`    | `success-700`   | `success-500` | Now within effective window       |
| `Retired`         | `slate-2`       | `slate-7`       | `slate-5`     | Past effectiveTo                  |
| `Rejected`        | `danger-50`     | `danger-700`    | `danger-500`  | Checker rejected                  |

Pills:

```
● Live          ◐ In review        ▶ Scheduled        ○ Draft        ▣ Retired      ✕ Rejected
```

## P4.7 Indian money formatting

Indian numbering: `₹1,23,45,678.90`. Tokens: `formatINR(paise: Long, fraction: Int = 2)`.

Rules:

1. **Always show `₹` prefix.** Never use `Rs.`.
2. **Always tabular figures, mono font.**
3. **Two decimals for line items**; whole rupees for chart axes; lakh/crore notation only on chart axes and KPI cards (`₹2.41 Cr`, `₹1.21 L`).
4. **Negatives**: parentheses *and* `slate-7` colour: `(₹2,400.00)` for cost-line subtotals; never use a leading minus and never colour the digits.
5. **Right-aligned** in tables (the only right-aligned column in Aegis tables).
6. **Trailing-zero discipline**: paise are always shown when the cell is a *transactional* amount (line items, totals). Paise can be omitted on chart axes and KPI cards.

### Lakh/Crore breakpoints

| Range                                  | Notation              | Example                |
|----------------------------------------|-----------------------|------------------------|
| `0 – 99,999`                           | full digits           | `₹78,450`              |
| `1,00,000 – 99,99,999`                 | `₹X.YY L`             | `₹1.21 L`              |
| `1,00,00,000+`                         | `₹X.YY Cr`            | `₹2.41 Cr`             |

Hovering a compact form reveals the full `₹` figure in a tooltip.

## P4.8 Charts — the catalogue and the rules

We commit ONE chart per intent. Mixing is forbidden.

| Intent                                             | Chart type                | Notes                                              |
|----------------------------------------------------|---------------------------|----------------------------------------------------|
| KPI trend (single series, ≤ 30 points)             | **Sparkline**             | 80×24, no axes, baseline at 0                      |
| KPI trend with delta callout                       | **Area (filled)**         | 1 series, 1 gradient, hover dot                    |
| Comparison across discrete categories              | **Bar (horizontal)**      | Sorted desc, mono numbers right of bars            |
| Composition (parts of whole, ≤ 6 parts)            | **Donut**                 | Center label = total, segments labelled outside    |
| Composition (parts of whole, > 6 parts)            | **Stacked bar (single)**  | Single 320×24 bar, legend below                    |
| Time-series of compositions                        | **Stacked area**          | ≤ 6 categories; "Other" bucket if more             |
| Distribution / matrix                              | **Heatmap**               | Rate Table heatmap; quantile colour scale          |
| Flow / origin breakdown                            | **Sankey**                | Premium-flow on Home (see P4.9)                    |
| Geographic                                         | **Choropleth (India)**    | Zone-wise premium                                  |
| Correlation                                        | **Scatter w/ regression** | Loss-ratio vs age band                             |

### Forbidden

- 3D anything.
- Pie charts > 4 slices (use donut + composition rule).
- Dual y-axis.
- Stacked bars with > 6 segments without an "Other" bucket.

### Chart palette (categorical)

Six categorical hues, picked to be friendly to red-green colour blindness:

```
chart-1   #4F46E5  Indigo (brand)
chart-2   #06B6D4  Cyan
chart-3   #F59E0B  Amber
chart-4   #84CC16  Lime
chart-5   #EC4899  Pink
chart-6   #6B7280  Slate
```

For sequential (heatmap, choropleth): a single-hue ramp on Indigo (`indigo-50 → indigo-700`).

For diverging (Δ heatmap on rate diff): `danger-500 ← slate-2 → success-500`.

## P4.9 Hero data viz — the Premium-Flow Sankey on Home

The single most beautiful object on the Home dashboard. Inspired by Stripe's "where did this revenue come from" page.

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Premium flow · last 30d · ₹6.42 Cr                                       │
│  ────────────────────────────────────────────────────────────────────     │
│                                                                            │
│   PLAN                ADD-ONS / UW          DISCOUNTS         FINAL        │
│                                                                            │
│   Flagship ███████░ ─┐                                                     │
│                      ├─► Base ████████████ ┐                              │
│   Senior   ██████░░ ─┤                     ├─► Subtotal ███████████ ┐    │
│                      ├─► +Add-ons ███     ─┤                        │    │
│   Global   ████░░░░ ─┤                     │                        │    │
│                      ├─► +UW load ░       ─┤                        │    │
│   POSP     ██░░░░░░ ─┘                     │                        │    │
│                                            ├─► −Discounts ░░░       │    │
│                                            │                        ├─►  │
│                                            └─► +GST 18% ████        │    │
│                                                                     │    │
│                                                       ₹6.42 Cr  ◀───┘    │
│                                                                            │
│  Hover any band to see exact ₹.                                            │
└───────────────────────────────────────────────────────────────────────────┘
```

The Sankey makes the *math* of the engine visible to a non-technical exec. It is also the most efficient way to communicate the audit's §1.1 finding — *18% GST never applied* — viscerally; the GST band is highlighted in the success-700 colour to remind us it now exists.

## P4.10 Motion

Default `150ms ease-out`. Drawers / sheets slide-in `250ms cubic-bezier(.4,0,.2,1)`. Toasts `200ms`. Modals `180ms`. Skeleton shimmer cycle `1.4s`. All transitions respect `prefers-reduced-motion`.

## P4.11 Density modes — applied across the app

A global toggle in the bottom-right of the workspace footer:

```
   [comfortable ●━━━━━○ compact ○━━━━━○ mini]
```

Density propagates to tables, forms, lists. Persisted in user prefs.

## P4.12 Components — the library

The full component inventory the dashboard needs (each is a `@Composable` in Compose Multiplatform). All shipped with stories in a `:design-system:catalog` module.

- **Layout**: `AegisShell`, `AegisRail`, `AegisTopBar`, `AegisStatusBar`, `AegisWorkspace`
- **Surfaces**: `AegisCard`, `AegisCardSection`, `AegisDrawer`, `AegisSheet`, `AegisModal`, `AegisToast`, `AegisCallout`
- **Inputs**: `AegisTextField`, `AegisNumberField`, `AegisMoneyField` (₹ Indian formatting), `AegisDateField`, `AegisSelect`, `AegisMultiSelect`, `AegisCombobox`, `AegisToggle`, `AegisCheckbox`, `AegisRadio`, `AegisFileDrop`, `AegisSlider`
- **Actions**: `AegisButton` (primary/secondary/ghost/danger), `AegisIconButton`, `AegisLink`, `AegisMenu`, `AegisDropdown`, `AegisSplitButton`
- **Display**: `AegisBadge`, `AegisStatusPill`, `AegisKbd`, `AegisAvatar`, `AegisChip`, `AegisProgressBar`, `AegisMoneyDelta`
- **Data**: `AegisTable`, `AegisTableCell`, `AegisColumnHeader`, `AegisRow`, `AegisEmptyState`, `AegisSkeleton`, `AegisInlineEdit`
- **Charts**: `AegisSparkline`, `AegisArea`, `AegisBar`, `AegisDonut`, `AegisStackedBar`, `AegisStackedArea`, `AegisHeatmap`, `AegisSankey`, `AegisChoropleth`, `AegisScatter`
- **Domain**: `AgeBandHeatmap`, `CoverChip`, `CoverParamInput`, `UinBadge`, `EffectiveDateTimeline`, `ApprovalRibbon`, `RiskScoreBar`, `PremiumBreakdownCard`

## P4.13 Accessibility — WCAG AAA targets

- Contrast ≥ 7:1 for body text (`slate-8` on `slate-0` measures 9.7:1 ✓).
- Focus ring 2px `indigo-500` outer + 2px `indigo-100` halo on every interactive element.
- All interactive elements have visible focus on `Tab`.
- All status pills convey state with **shape** *and* **colour** (dot, glyph) — never colour alone.
- All money/numbers use mono tabular figures so visual scanning is reliable.
- ARIA labels on every icon-only button.
- Skip-link to main content.
- Logical heading order (`h1 → h2 → h3`) on every screen.

## P4.14 What's still missing (for Pass 5)

- Bulk operations and large-table virtualisation
- A/B test rate-changes harness
- Rollback flow
- Multi-user collaboration cursors
- What-if simulator (population-level)
- Localisation
- Embedded help / product tour
- Telemetry overlay

---

# Pass 5 — Polish, Edge Cases, Scale, Collaboration, Onboarding

> **What I'm fixing from Pass 4.** Pass 4 made the surface beautiful and consistent. Pass 5 makes it bulletproof at scale, useful when many people use it at once, learnable for a brand-new analyst, and reversible when something goes wrong. We add: bulk operations, virtualisation, A/B rate harness, rollback, multi-user cursors, what-if population simulator, localisation, embedded help, telemetry overlay, error recovery.

## P5.1 Bulk operations

When ≥1 row is selected, the table toolbar rises into a **bulk action bar**:

```
┌─────────────────────────────────────────────────────────────────────┐
│  3 selected · ⌘A to select all 12,841                                │
│  [Set status ▾] [Bulk edit ▾] [Export CSV] [Archive] [⌫ Delete]      │
└─────────────────────────────────────────────────────────────────────┘
```

Bulk operations:

| Surface              | Bulk actions                                                           |
|----------------------|------------------------------------------------------------------------|
| Products             | Set status (live/retired), assign owner, export, archive               |
| Plan Configurator    | (single-record, no bulk inside)                                        |
| Cover Catalog        | Set category, set accumulation base, mark `default`                    |
| Rate Tables          | Bulk approve scheduled versions, export to Excel, archive              |
| Discounts            | Set cap, enable/disable, attach promo window                           |
| Rules                | Enable / disable, change severity, attach pack                         |
| Quotes               | Email, export, archive, mark as referenced                             |
| UW Queue             | Bulk assign, bulk refer to CMO, bulk approve standard, export          |
| Audit log            | Export, redact (PII), tag for IRDAI                                    |

Confirmation modal on destructive bulk:

```
┌─────────────────────────────────────────────────────────────────────┐
│  ⚠ Archive 12,841 quotes?                                            │
│  ──────────────                                                      │
│  These will move to "Archived" and be hidden from searches.          │
│  They can be restored within 30 days.                                │
│                                                                      │
│  Confirm by typing "ARCHIVE 12841"                                  │
│  [                                                ]                  │
│                                                                      │
│  [Cancel]                                  [Archive 12841 ▸]        │
└─────────────────────────────────────────────────────────────────────┘
```

(Confirmation-string-required pattern from Vercel / GitHub.)

## P5.2 Large-table virtualisation

Compose `LazyColumn` with item-size memoisation handles up to 1M rows. Header is always sticky. The footer indicates `Loaded 500 / 12,841 · scroll for more`. For tables with `> 10,000` rows the filter row is *always* visible.

Pagination is **infinite scroll**, not pages — but ⌘G "Go to row N" exists for power users.

## P5.3 A/B test rate-changes harness

Before approving a rate-table version, an actuary can run it against a **test bench** of canonical quotes (e.g., 100 quotes representing the production mix) to see the population-level delta:

```
┌─────────────────────────────────────────────────────────────────────────┐
│  A/B test · rt_flagship v2026.Q3 vs v2026.Q2                           │
│  ──────────────────────────────────────────────────────────────────    │
│  Test bench  [Q1 2026 sample · 100 quotes ▾]   [Add custom bench]      │
│                                                                         │
│  Aggregate                                                              │
│    Total premium impact   +4.78 %    (₹6.32 Cr → ₹6.62 Cr)              │
│    Quotes priced up       82                                            │
│    Quotes priced down     12                                            │
│    Quotes unchanged       6                                             │
│                                                                         │
│  Distribution                                                           │
│    Histogram of per-quote Δ %                                          │
│       ▁▁▁▁▂▂▃▃▅▆▇▇▆▅▄▃▂▁▁▁▁                                            │
│      -2%               +5%               +14%                          │
│                                                                         │
│  Outliers                                                               │
│    Q-test-91 · 1A · ₹1Cr · age 64    +12.4 %                            │
│    Q-test-58 · 2A · ₹75L · age 58    +10.8 %                            │
│    Q-test-22 · 1A · ₹3L · age 7      −1.0 %                             │
│                                                                         │
│  [Open quote-level diff ▸]                                             │
│  [Approve anyway ▸]  (requires CMO sign-off if any Δ > 10 %)           │
└─────────────────────────────────────────────────────────────────────────┘
```

## P5.4 Rollback

Every published change is a frozen snapshot. Snapshots live for 30 days minimum, longer for the last 4 quarters' approved versions (regulatory retention).

```
┌─────────────────────────────────────────────────────────────────────┐
│  Snapshots · 142                                                     │
│  ───────────────                                                     │
│  When               Surface       Entity            By      Action  │
│  2026-05-20 09:42   Rate Table    rt_flagship Q3    Anil    [Roll  ◂]│
│  2026-05-15 14:21   Plan          senior            Vivek   [Roll  ◂]│
│  …                                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

Rolling back triggers a new approval flow (rollback also needs 4-eyes).

## P5.5 Multi-user collaboration — Figma-style cursors

Two product managers can be in the same Plan Configurator at once:

```
[ Avatar A is at field "Eligibility · max age"  ✎ 65 ]
[ Avatar B is at field "Covers · Maternity"       ]
```

- Live cursors appear in the right rail.
- Field-level locks: when one user is typing into a field, the other sees a soft outline + small avatar marker on that field. Writes are conflict-free where possible (CRDT for text), pessimistic-lock where not (rate-table cell edits).
- Conflict resolution: when a save conflicts, the user gets a "merge" view (Linear-style — your version | their version | combined).

## P5.6 What-if simulator (population-level)

A standalone surface under Insight → Simulator. Slide age, SI, zone, family-type distributions and see population-level premium impact in real time.

```
┌─────────────────────────────────────────────────────────────────────────┐
│  What-if simulator                                                      │
│  ──────────────────                                                     │
│  Base population   [Q1 2026 actuals ▾]                                  │
│                                                                          │
│  Adjustments                                                             │
│    Average age          [── 32 ──● 38]                                  │
│    SI distribution      [Skew toward ₹50L ──●─]                         │
│    Zone mix             [Z1 25% · Z2 50% · Z3 20% · PI 5%]              │
│    Family-type mix      [2A2C ↑ 5 % ─●─]                                │
│                                                                          │
│  Projected outcome                                                       │
│    Total premium        ₹6.42 Cr → ₹7.18 Cr   +11.8 %                   │
│    Avg ticket size      ₹35,248 → ₹38,440     +9.1 %                    │
│    Quote count          18,212 → 18,694       +2.6 %                    │
│                                                                          │
│  Plan-mix waterfall    [stacked bar chart of plan contribution]          │
│  [Save as scenario]    [Export PDF]                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

This is the *single* most-loved feature by the head of products (audit §7.10 calls out reporting; this turns reporting into *forecasting*).

## P5.7 Localisation

UI strings are key-based: `i18n("home.kpi.premium_booked")`. The dashboard supports `en-IN` first; `hi-IN` (Hindi), `bn-IN` (Bengali), `mr-IN` (Marathi), `te-IN` (Telugu), `ta-IN` (Tamil), `gu-IN` (Gujarati) ship next.

Money formatting is locale-agnostic (always Indian grouping, always `₹`). Dates follow ISO 8601 with locale-aware month names.

Numerals are Western (0–9). (The buyonline customer flow can use Devanagari numerals; the *business dashboard* uses Western.)

## P5.8 Embedded help & onboarding

### P5.8.1 Product tour

90-second tour on first login, can be replayed from `?` menu. 6 slides:

1. The shell — left rail, command palette, status bar.
2. The Home dashboard — KPIs and Sankey.
3. Editing a plan — autosave, 4-eyes, publish.
4. Importing Excel — wizard.
5. Approving a rate change — diff, A/B test.
6. Getting help — `?`, command palette, docs.

### P5.8.2 Empty-state guidance

Every empty state has a *micro-essay* explaining the surface plus the primary CTA. No empty state is just "No data".

### P5.8.3 Tooltips

- 200ms hover delay.
- 320px max width.
- Show keyboard shortcut at end of tooltip in `AegisKbd` style.
- Tooltips for domain jargon (UIN, NCB, R-base, Master Circular) link to the **Aegis Glossary** drawer.

### P5.8.4 Aegis Glossary drawer

`?` then `g` opens a drawer:

```
Glossary
─────────
UIN          IRDAI's 4-digit Unique Identification Number for an approved product.
NCB          No-Claim Bonus — premium discount for renewing without a claim.
R23 base     The accumulation base used by Flagship 2A2C Z2; see Cover Catalog.
Master 2024  IRDAI's June 2024 Master Circular on Health Insurance.
Free-look    The 15-day post-issuance window in which a customer can cancel.
…
```

## P5.9 Error recovery

Every error has a recovery path:

| Error                              | Recovery                                                  |
|------------------------------------|-----------------------------------------------------------|
| Save failed (network)              | Inline retry; offline queue; retry succeeds → toast       |
| Save conflict                      | Open three-way merge; user picks                          |
| Engine error                       | Banner with error code, "Copy error context" button       |
| Invalid Excel sheet                | Inspector showing exact cell, expected vs actual          |
| Approval rejected                  | Reviewer's comment shown; "Open" reopens for edit         |
| Rule blocked at quote time         | Click rule code to see the rule definition                |
| Permission denied                  | "Request access" CTA pings the admin                      |
| Pricing engine slow                | After 1.2s, "Calculating…" skeleton replaces preview      |
| Quote pre-empted by rate change    | Banner: "This quote was priced on v2026.Q2. v2026.Q3 is now live. Re-price?" |

## P5.10 Telemetry & analytics overlay

Internal-only overlay (`⌘⇧T`) shows:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Telemetry · this page                                          [Close ✕]│
│ ─────────────────────────                                               │
│ Time on page          2m 41s                                            │
│ Recomposes (Compose)  127                                               │
│ Frames dropped        0                                                 │
│ Engine calls          14   p50 89ms · p99 412ms                         │
│ Net calls             6    p50 110ms · p99 380ms                        │
│ Render budget         173ms / 200ms ✓                                   │
│                                                                          │
│ Events fired                                                            │
│   plan.edit.start      ×1                                               │
│   plan.field.change    ×9                                               │
│   plan.preview.refresh ×9                                               │
│   plan.save.success    ×9                                               │
│                                                                          │
│ Rules triggered                                                         │
│   rule.senior_copay    ×1   (warn)                                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

Product team uses this in user-testing sessions. It also informs the perf budget assertion (Pass 1 promise: ≤200ms perceived load).

## P5.11 Performance budget

| Metric                              | Budget         | Strategy                                  |
|-------------------------------------|----------------|-------------------------------------------|
| First contentful workspace          | ≤ 200 ms       | Skeletons everywhere; data lazy-loaded    |
| Time-to-interactive                 | ≤ 600 ms       | Defer heavy charts via Compose `produceState` |
| Table render (1k rows)              | ≤ 30 ms        | `LazyColumn` with stable keys             |
| Heatmap render (15×13)              | ≤ 16 ms        | Pre-bucketed colour computation           |
| Engine quote                        | ≤ 80 ms p50    | Memoisation, parallel year compute        |
| Autosave round-trip                 | ≤ 250 ms p95   | Debounced + optimistic write              |
| Cmd-K open                          | ≤ 50 ms        | In-memory index, fuzzy search             |

## P5.12 Edge cases catalogue

Documented at design level so engineering doesn't discover them in QA:

- A rate-table version is approved but its effectiveFrom is *yesterday* (back-dated). → Block at submit time with an explanation; require CMO override.
- A plan has `effectiveTo < effectiveFrom`. → Inline error before save.
- Two plans share a UIN. → Unique-constraint error at save; surface the other plan.
- An import wipes 50% of cells. → Diff drawer flags red on "rows removed > 20%"; require typed confirmation.
- A user is editing a plan that another user just retired. → Banner: "This plan was retired by Anil 2 min ago. Continue in read-only?"
- A rule references a deleted cover. → Rule auto-disabled with a banner; cover can be restored.
- A discount cap exceeds the plan-level cap (30%). → Inline error.
- The pricing engine returns `isValid = false` with a validation error. → Surface in the quote preview panel.
- DB connection lost mid-edit. → Offline mode banner; edits queued.
- Permission revoked mid-session. → Soft kick to read-only with banner.
- A user with non-INR locale tries to enter `1,000.00`. → AegisMoneyField parses both `1,00,000` and `100000` and shows the Indian-formatted result on blur.

## P5.13 What "world-class" looks like, summarised

A non-engineer business user can:

1. Find any plan/quote/cover/rule in ≤ 3 keystrokes via `⌘K`.
2. Edit a plan, see live quote preview, and never accidentally publish to production.
3. Run a rate change through diff → A/B test → 4-eyes → schedule → live with full audit.
4. Import an Excel workbook with dry-run, diff, rollback within 30 days.
5. Triage UW queue with `J/K` keyboard at 1 case per 15 seconds.
6. Export every report in IRDAI-friendly format.
7. Recover from every error without losing work.
8. Onboard a new analyst with a 90-second tour and a glossary.
9. Use the system in Hindi or any of 6 regional languages.
10. Trust that every change is logged and reversible.

---

# Final consolidated plan (10-minute scan)

A single distillation a stakeholder can read in 10 minutes and understand the entire vision.

## Vision

**Aegis** is the business-user control plane for an Indian health insurance platform. Built on Compose Multiplatform, light theme. It replaces today's read-only single-window calculator with a **13-surface operations cockpit** that brings IRDAI rigor, actuarial discipline, and Linear-level UX craft to product managers, actuaries, underwriters, and ops leads.

## Surface inventory (13)

| # | Surface                  | One-line purpose                                                          |
|---|--------------------------|---------------------------------------------------------------------------|
| 1 | Home Dashboard           | Daily KPIs, Sankey of premium flow, approvals waiting on me               |
| 2 | Product Catalog          | Registry of approved products with UIN, version, status                   |
| 3 | Plan Configurator        | 7-tab deep editor with autosave, live quote preview, 4-eyes publish       |
| 4 | Cover Catalog Manager    | 54-cover library with accumulation-base inspector + Excel-parity tests    |
| 5 | Rate Table Manager       | Versioned heatmap with diff, A/B test, approval, effective-date timeline  |
| 6 | Discount Manager         | Discount rules, caps, mutual-exclusions, promo windows                    |
| 7 | Business Rules Editor    | Visual When/Then builder for 47 rules, severity, telemetry per rule       |
| 8 | Excel Import Studio      | 5-step wizard: upload → parse → diff → confirm → rollback snapshot        |
| 9 | Quote Explorer           | Search/compare/drill quotes; per-cover yearly grid                        |
| 10| Underwriting Queue       | Risk-scored inbox with structured PED/ICD, action drawer                  |
| 11| Reports & Analytics      | Premium/plan-mix/zone-mix/claim/lapse/commission, IRDAI-format export     |
| 12| Audit & Governance       | Every change logged; 4-eyes approval; immutable snapshots                 |
| 13| Settings                 | Users, RBAC, GST, IRDAI artefacts (CIS/T&C/etc.), integrations            |

Plus four new Operations surfaces driven by the audit (renewal, portability, endorsement, claims) — same Aegis-table grammar.

## Visual system

- **Type**: Inter (UI) + JetBrains Mono (numbers, tabular figures).
- **Colour**: Slate 12-step + Indigo brand + 5 semantic ramps (success/warning/danger/info/premium). PruRed retired from the dashboard (kept in the customer buyonline flow).
- **Spacing**: 4-px base, 4/8/12/16/24/32/48/64/96.
- **Elevation**: 5 tiers with 1px hairline borders.
- **Icons**: Lucide, 1.5px stroke, 16/20/24, pinned to actions.
- **Money**: `₹` always, Indian grouping (`₹1,23,45,678.90`), mono tabular, right-aligned in tables, polarity-coloured deltas.
- **Status pills**: Draft / In Review / Scheduled / Live / Retired / Rejected — committed colour mapping used everywhere.
- **Density**: comfortable / compact / mini toggle, persisted.
- **Motion**: 150ms default, 250ms drawers, `prefers-reduced-motion` respected.

## Interaction model

- **Tables** are the dominant UI: sticky header, frozen first column, sort/filter/group/pin, multi-select, inline edit, virtualised, right-click context menus.
- **Inline edit ≤ 2 fields. Drawer ≤ 8 fields. Sheet for deep edits. Modal only for confirmations.**
- **Autosave** with diff banner; **publish** is the explicit promotion to live.
- **Keyboard everywhere** — `⌘K` palette, `g+letter` navigation, `J/K` row scroll, `E` inline edit, `?` help, full Linear-grade shortcut sheet.
- **Seven canonical states** on every screen: empty (first run), empty (filtered), loading, loading-more, populated, error, partial.

## Insurance-domain depth

- **IRDAI Registry** with UIN, prospectus, CIS, T&C, sales illustration, HDF, NA-Q.
- **Effective-date timeline** on every plan / rate-table / cover / discount; quotes reproducible via snapshot reference.
- **4-eyes workflow** (maker → checker) on every consequential change with attached evidence, pre-flight checks, and reviewer drawer.
- **allowedCoverIds matrix editor** that flags Master Circular 2024 gaps (AYUSH, Mental Health, HIV/AIDS).
- **Accumulation-base inspector** that shows the exact Excel formula (e.g., `=SUM(X23:X40)`), covers included/excluded, and pinned golden tests.
- **47-rule Business Rules pack** for the validation deficits the audit lists (GST, discount cap, senior co-pay, mutual exclusions, age/SI/zone bounds, UW referrals, IRDAI mandates).
- **Structured UW form** with ICD-10, severity, BMI auto-compute, risk score, and decision audit.
- **Lifecycle features** (Renewal, Portability, Endorsement, Claims, Free-look) modelled as first-class Operations surfaces.

## Scale, collaboration, polish

- **Bulk operations** with typed-confirmation for destructive actions.
- **Virtualised tables** up to 1M rows with infinite scroll + "go to row N".
- **A/B test harness** against canonical 100-quote bench before approving rate changes.
- **Rollback** with 30-day snapshot retention, 4-eyes required.
- **Multi-user cursors** (Figma-style), CRDT for text, pessimistic-lock for rates, three-way merge for conflicts.
- **What-if population simulator** that slides age/SI/zone/family-type and forecasts premium impact.
- **Localisation** in en-IN + 6 regional Indian languages; money/dates locale-safe.
- **Embedded help**: product tour, empty-state micro-essays, tooltips with shortcuts, Aegis Glossary.
- **Telemetry overlay** (`⌘⇧T`) for product / perf debugging.
- **Performance budget** ≤ 200ms perceived workspace load.

## How this maps to the audit

| Audit finding (DIM-6 / DIM-7 / DIM-1 selected)                       | Aegis answer                                                  |
|----------------------------------------------------------------------|---------------------------------------------------------------|
| §6.4 — rate config read-only, no plan duplication, no versioning     | Plan Configurator + Rate Table Manager with versions + diff + 4-eyes |
| §6.4 — no min<max validation, stale empty-state copy                  | Eligibility tab inline validation; data-driven empty states   |
| §6.5 — no dry-run, no diff, no rollback on import                     | Excel Import Studio 5-step wizard + Snapshots                 |
| §6.2 — no save/load/compare quote, no PDF, no agent attribution      | Quote Explorer + compare mode + per-quote drawer              |
| §6.3 — 40+ covers, no search, MX rules only fire on Calculate        | Cover Catalog Manager + Covers tab live MX warnings           |
| §7.1 — no UIN, no POSP code, no policy dates                          | IRDAI Registry + Settings + Configurator Overview tab         |
| §7.2 — no Prospectus/CIS/T&C/SI/HDF/NA-Q, no Free-look                | IRDAI Registry attachments + 47-rule pack                     |
| §7.3 — UW form is one boolean, no BMI, no risk score                  | Structured UW form + Risk Score Bar + Auto-refer rules        |
| §7.4 — no Renewal/Portability/Endorsement/Claim/Free-look             | First-class Operations surfaces (same Aegis-table grammar)    |
| §7.5 — no NCB, no claim history                                       | Renewals surface with NCB column; Claims surface              |
| §7.8 — Master Circular 2024 gaps (AYUSH/Mental Health/HIV)            | Cover-matrix flags + Rules pack (rules 28, 29, 30)            |
| §7.10 — no production report / no MIS / no commission ledger          | Reports & Analytics with IRDAI export                         |
| §1.1 — 18% GST never applied                                          | Plan default GST 18%; Sankey on Home explicitly shows GST band |
| §1.3 — COVER_ACCUM_BASES fragile, no tests                            | Accumulation-base inspector + pinned golden fixtures          |
| §1.9 — plan-specific rules not enforced                               | Business Rules Editor + Rule packs (Senior/POSP/Global)        |
| §1.11 — no calculatedAt/engineVersion/rateTableVersion                | Quote-reproducibility via snapshot reference on every quote   |

## Build & ship order (engineering hint)

The audit will not be fixed in one release. Suggested order:

1. **Design system** (`AegisShell`, table, drawer, sheet, money field, status pill, ⌘K palette).
2. **Home Dashboard + Product Catalog** (read-only first, then editable).
3. **Plan Configurator** (Overview, Eligibility tabs first; Covers, Rates, Rules, History next).
4. **Cover Catalog Manager + Accumulation-base inspector** (pulls audit §1.3 into design).
5. **Rate Table Manager** with versioning + diff (closes audit §6.4).
6. **Excel Import Studio** wizard (closes audit §6.5).
7. **Quote Explorer + Compare** (closes audit §6.2).
8. **Business Rules Editor + 47-rule pack** (closes audit §1.8/§1.9).
9. **UW Queue + structured form** (closes audit §7.3).
10. **Audit & Governance + 4-eyes** (closes audit §3.8/§7.x).
11. **Reports & Analytics + IRDAI export** (closes audit §7.10).
12. **Renewals / Portability / Endorsements / Claims** (closes audit §7.4).
13. **Polish: bulk ops, A/B harness, rollback, cursors, simulator, localisation, help, telemetry.**

Each tranche is itself shippable: the dashboard is incrementally useful, not big-bang.

## North-star screen

If a stakeholder ever asks *"show me one screen that proves this"*, show the **Rate Table Manager with the diff drawer open and the A/B test result panel beside it**. That single composition demonstrates:

- Heatmap aesthetic
- Versioning + effective dates
- Diff with quantitative deltas
- 4-eyes workflow ribbon
- Population-level A/B simulator
- Linear-grade keyboard / drawer affordances
- The audit's §1, §6, §7 demands answered in one frame.

That is *Aegis*.

---

# Appendix A — Surface deep-dives that didn't get full attention in passes 1-5

Several surfaces deserve more wireframes than the passes had room for. Treat this appendix as the equal-rigor companion: every appendix item is "Pass-2-quality" minimum.

## A.1 Discount Manager — full spec

The current engine treats `selectedDiscounts` as a flat list with a `30%` cap that audit §1.4 says is bypassed by cover-pass discounts. Aegis fixes this with a first-class management surface.

**List view:**

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ Discounts · 12       [+ New discount]                                          │
│ Filters: Status ▾  Category ▾  Effective ▾                                     │
├────────────────────────────────────────────────────────────────────────────────┤
│ Code                   Name                  Type    Rate   Cap    Active      │
│ disc_family            Family Floater        Pct     5%     30%    ● Live      │
│ disc_loyalty           Loyalty               Pct     5%     30%    ● Live      │
│ disc_employee          Employee              Pct     10%    30%    ● Live      │
│ disc_commission_lieu   Commission-in-lieu    Pct     10%    30%    ● Live      │
│ disc_gmc               GMC bulk              Pct     7.5%   30%    ● Live      │
│ disc_long_term         Long-term tenure      Pct     ↗      30%    ● Live      │
│ disc_online            Online                Pct     2%     30%    ◐ Review    │
│ smart_select           Smart Select (cover)  Cover   ↗      —      ● Live      │
│ per_claim_deductible   Per-claim Ded.(cover) Cover   ↗      —      ● Live      │
│ aggregate_deductible   Aggregate Ded.(cover) Cover   ↗      —      ● Live      │
│ co_pay                 Co-pay (cover)        Cover   ↗      —      ● Live      │
│ promo_independence_24  Independence Day      Promo   3%     30%    ▶ Sched.    │
└────────────────────────────────────────────────────────────────────────────────┘
```

**Inspector for a single discount** (`disc_employee` for example):

```
┌─────────────────────────────────────────────────────────────────────────┐
│ disc_employee · Employee discount                       [Edit]  [✕]    │
│ ───────────────────────────────────────────────────────────────────    │
│ Category            Discount (regular)                                  │
│ Computation         Percentage of (basePremium + add-ons)               │
│ Rate                10.0 %                                              │
│ Cap (per-discount)  10.0 %                                              │
│ Counts toward       Capped pool (subject to plan 30% cap)               │
│ Eligibility         Customer is employee of group → CompanyId required  │
│ Mutual exclusion    disc_commission_lieu                                │
│ Plans               All except POSP                                     │
│ Effective from      2024-04-01                                          │
│ Effective to        —                                                   │
│ ───────────────────────────────────────────────────────────────────    │
│ Promo window        n/a                                                 │
│ Usage (last 30d)    1,142 quotes (8.6 % of volume)                      │
│ Impact              −₹38.4 L saved by customers                         │
│ ───────────────────────────────────────────────────────────────────    │
│ History  →  v3 by Ria on 2026-03-01 (was 7.5%, raised to 10%)           │
└─────────────────────────────────────────────────────────────────────────┘
```

**Key behaviour:**

- The "Counts toward" field surfaces audit §1.4 explicitly. The four `cover`-typed entries (`smart_select`, `per_claim_deductible`, `aggregate_deductible`, `co_pay`) **must** be moved into the capped pool — UI calls this out with a `⚠ Audit DIM-1 §1.4` chip next to any cover-discount that isn't capped.
- Promo windows have first-class start/end dates and a small banner appears on Home when a promo activates or expires.
- Mutual-exclusion field is a multi-select; the rule auto-generates a Business Rule (rule #9 in the 47-rule pack) and shows it as a linked chip.

## A.2 Settings — full spec

Settings is not one page; it's an indexed section. The IA:

```
Settings
├─ Organization
│   ├─ Profile (insurer name, IRDAI reg #, claim-settlement ratio, network hospitals)
│   ├─ DPO contact (DPDP Act 2023 mandatory)
│   ├─ Grievance ombudsman pointer
│   ├─ Financial year & fiscal calendar
│   └─ Logo / brand
├─ Users & roles
│   ├─ Users
│   ├─ Roles (RBAC)
│   ├─ Permissions matrix
│   ├─ SSO / SAML
│   └─ Sessions
├─ IRDAI Registry  (see P3.1)
├─ Compliance
│   ├─ DPDP register (data-flow log)
│   ├─ Free-look policy
│   ├─ Audit retention (default 7 years)
│   └─ Aadhaar / PAN masking rules
├─ Engine
│   ├─ Engine version (read-only)
│   ├─ Default GST rate (18%)
│   ├─ Default discount cap (30%)
│   ├─ Money precision (paise / BigDecimal)
│   └─ Feature flags
├─ Integrations
│   ├─ Payment gateway (Razorpay/PayU)
│   ├─ SMS / OTP (Gupshup/MSG91)
│   ├─ Email (Mailgun/SES)
│   ├─ KYC (DigiLocker, NSDL, UIDAI)
│   ├─ Bank verification (Penny-drop)
│   └─ Hospital network (network ROC / API)
├─ Snapshots & rollback
└─ Webhooks & API keys
```

**Users & roles — RBAC matrix:**

The roles built-in:

| Role            | Description                                                |
|-----------------|------------------------------------------------------------|
| `viewer`        | Read-only across all surfaces                              |
| `product_mgr`   | Edit Plans (Draft only); cannot publish                    |
| `actuary`       | Edit Rate Tables (Draft only); cannot publish              |
| `pricing_committee` | Approve/reject rate-table changes (4-eyes checker)     |
| `underwriter`   | Triage UW queue, write UW decisions                        |
| `cmo`           | Override UW; approve high-risk cases                       |
| `ops_lead`      | Edit Discounts, Rules; cannot edit Plans                   |
| `compliance`    | Read all, plus access to Audit & DPDP register             |
| `admin`         | All of the above plus Settings                             |

**Permission matrix view**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Permission matrix                                                          │
│                                                                            │
│                  viewer  pm    actuary  pc    uw    cmo   ops   comp  admin│
│ Plan.read        ✓       ✓     ✓        ✓     ✓     ✓     ✓     ✓     ✓   │
│ Plan.edit        —       ✓     —        —     —     —     —     —     ✓   │
│ Plan.publish     —       —     —        ✓     —     —     —     —     ✓   │
│ Rate.read        ✓       ✓     ✓        ✓     ✓     ✓     ✓     ✓     ✓   │
│ Rate.edit        —       —     ✓        —     —     —     —     —     ✓   │
│ Rate.publish     —       —     —        ✓     —     —     —     —     ✓   │
│ UW.triage        —       —     —        —     ✓     ✓     —     —     ✓   │
│ UW.override      —       —     —        —     —     ✓     —     —     ✓   │
│ Discount.edit    —       —     —        —     —     —     ✓     —     ✓   │
│ Rules.edit       —       —     —        —     —     —     ✓     —     ✓   │
│ Audit.read       ✓       ✓     ✓        ✓     ✓     ✓     ✓     ✓     ✓   │
│ Audit.export     —       —     —        —     —     —     —     ✓     ✓   │
│ Settings.edit    —       —     —        —     —     —     —     —     ✓   │
│ Snapshot.restore —       —     —        ✓     —     —     —     —     ✓   │
└────────────────────────────────────────────────────────────────────────────┘
```

The matrix is editable inline by an admin. Custom roles can be created.

**Sessions** — list of active sessions with IP, device, last activity. "Force log out" available per session.

## A.3 Reports & Analytics — full spec of every report

Each report is its own routed page under `/reports/<id>`. The list page:

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Reports                                                                  │
│ Filters: Frequency ▾  Mandatory ▾  Format ▾                              │
├──────────────────────────────────────────────────────────────────────────┤
│ Report                                  Frequency   Last run   Status   │
│ R-001 Premium booked (IRDAI MIS)        Daily       09:00 ✓    Pinned   │
│ R-002 Plan mix                          Daily       09:00 ✓             │
│ R-003 Zone mix                          Weekly      Mon 08:00 ✓         │
│ R-004 UW backlog                        Daily       09:00 ✓             │
│ R-005 Claim incurred ratio (CIR)        Monthly     1st 06:00 ✓         │
│ R-006 Claim settlement ratio (CSR)      Monthly     1st 06:00 ✓         │
│ R-007 Lapse + revival                   Monthly     1st 06:00 ✓         │
│ R-008 Agent commission ledger           Monthly     1st 06:00 ✓         │
│ R-009 Renewal pipeline                  Daily       09:00 ✓             │
│ R-010 Persistency 13/25/37/49           Quarterly   1-Apr ✓     IRDAI   │
│ R-011 Solvency & ALM data dump          Quarterly   1-Apr ✓     IRDAI   │
│ R-012 Free-look returns                 Monthly     1st 06:00           │
│ R-013 Grievance log                     Monthly     1st 06:00           │
│ R-014 Portability inflow / outflow      Quarterly   1-Apr             │
│ R-015 NCB liability                     Quarterly   1-Apr             │
└──────────────────────────────────────────────────────────────────────────┘
```

### Report R-001 detail — Premium booked

```
┌──────────────────────────────────────────────────────────────────────────┐
│ R-001 · Premium booked                          [Run ⌘R] [Schedule] [✕]  │
│ ─────────────────────────────────                                        │
│ Range  [last 30 days ▾]   Group by [Plan ▾]                              │
│                                                                          │
│ KPI strip                                                                │
│   ₹6.42 Cr                Quotes 18,212        Avg ticket ₹35,248         │
│   ▲ 8.4 % vs prev 30d     ▲ 3.2 %               ▲ 4.8 %                  │
│                                                                          │
│ Trend (area chart) ─ daily premium booked                                │
│   ─── ▲▲▲ ─── ▲▲▲▲ ──── ▲▲▲▲▲▲ ─── ▲▲ ─── ▲▲▲▲ ─── ▲▲▲▲ ─── ▲▲▲▲▲ ──    │
│                                                                          │
│ Breakdown table                                                          │
│   Plan          Quotes   Premium     GST       Total      Δ vs prev      │
│   Flagship      6,212    ₹3.18 Cr    ₹57.2 L  ₹3.75 Cr   +9 % ▲          │
│   Senior        3,418    ₹1.21 Cr    ₹21.7 L  ₹1.42 Cr   +6 % ▲          │
│   Global        2,104    ₹84.5 L     ₹15.2 L  ₹99.8 L    +11 % ▲         │
│   Sub-Std       1,011    ₹38.2 L     ₹6.8 L   ₹45.1 L    +4 % ▲          │
│   POSP          5,467    ₹71.4 L     ₹12.8 L  ₹84.3 L    +14 % ▲         │
│                                                                          │
│ [Export CSV] [Export PDF] [Export to IRDAI XML]                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Report R-010 detail — Persistency (regulatory)

Persistency is computed at 13/25/37/49 months. IRDAI publishes the formula; the report must match it byte-for-byte:

```
┌──────────────────────────────────────────────────────────────────────────┐
│ R-010 · Persistency  (IRDAI Annual Report Annexure VI-A)                 │
│ ───────────────────────────────────────────────────────                  │
│                                                                          │
│ Cohort  FY 2024-25 Q1 issuances                                          │
│                                                                          │
│ Month 13     Month 25     Month 37     Month 49                          │
│ ────────     ────────     ────────     ────────                          │
│  87.4 %       72.1 %       61.3 %       54.8 %                           │
│                                                                          │
│  IRDAI target 80 %     70 %     60 %     50 %                            │
│  Status        ✓ above   ✓ above   ✓ above  ✓ above                       │
│                                                                          │
│ Formula reference: IRDAI Annexure VI-A definitions, applied with         │
│ Premium-weighted methodology.                                            │
│                                                                          │
│ [Export to IRDAI XML]   [Lock & file for FY 2024-25]                     │
└──────────────────────────────────────────────────────────────────────────┘
```

## A.4 Audit & Governance — full spec

The audit log is the most regulator-loved surface. Every event:

```
audit_event {
    id              ULID,
    actor           User,
    actor_role      Role,
    surface         "PlanConfigurator" | "RateTableManager" | ...,
    entity_type     "Plan" | "RateTable" | "Cover" | "Discount" | "Rule" | "Quote" | "UWCase",
    entity_id       String,
    entity_version  Int?,                       // version before change
    new_version     Int?,                       // version after change
    action          "create"|"edit"|"submit"|"approve"|"reject"|"publish"|"retire"|"rollback"|"login"|"export",
    field_changes   List<FieldChange>?,         // for edits — old/new value per field
    reason          String?,                    // free-text user reason
    evidence_files  List<UploadedFile>?,         // attached PDFs
    request_ip      String,
    request_ua      String,
    timestamp       Instant,
    correlation_id  String?,                    // for multi-event flows like a publish chain
}
```

**View:**

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Audit log · 8,123 events                                          [Export CSV]   │
│ Filters: Actor ▾  Surface ▾  Action ▾  Range ▾  Entity ▾                         │
├──────────────────────────────────────────────────────────────────────────────────┤
│ When                Actor   Role         Surface      Entity            Action   │
│ 05-20 09:42:11.122  Ria     pm           Rate Table   rt_flagship Q3    ✎ edit   │
│                                          → 142 cells changed (open diff)         │
│ 05-20 09:42:09.001  Ria     pm           Rate Table   rt_flagship Q3    + create │
│                                          → new draft from Q2                     │
│ 05-20 09:15:02.844  Anil    actuary      Cover        HOME_CARE         ✎ edit   │
│                                          → "description" field                   │
│ 05-20 08:55:33.012  Vivek   admin        Plan         senior            ▶ publish│
│                                          → v3 → live; effective 2026-04-01       │
│ 05-19 23:11:08.501  system  -            Engine       memo: 'GST 18%'   ⚙ start  │
│                                          → engine v0.9.4 deployed                │
│ …                                                                                │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Drill-in** (clicking a row reveals the full event + diff if applicable):

```
┌──────────────────────────────────────────────────────────────────────┐
│ Event 01H94…XQ8K                                          [Close ✕]  │
│ ───────────────────────────────                                      │
│ Actor               Ria Mehta (ria@pru.com)                          │
│ Role at time        product_mgr                                      │
│ Surface             Plan Configurator                                │
│ Entity              senior · v2 → v3                                 │
│ Action              edit                                             │
│ Reason              "Senior co-pay raised from 18% → 20% per CMO memo" │
│ Evidence            cmo_memo_2026-05-10.pdf                          │
│ IP / UA             10.0.4.18 · Aegis 1.4.2 · macOS                  │
│ Correlation         publish_chain_2026-05-12_senior_v3               │
│ ─────────────────                                                    │
│ Field changes (1)                                                    │
│   rules.senior_copay.min                                             │
│       was   0.18                                                     │
│       now   0.20                                                     │
└──────────────────────────────────────────────────────────────────────┘
```

**Tamper-evidence:** the log is append-only with a hash chain (each event includes the hash of the previous event). Tampering can be detected at any time. Verified by `/audit/verify` cron daily.

## A.5 The 22 buyonline screens, *NOT* in scope here

To be explicit: this redesign is the **business dashboard** for *internal* staff. The 22 customer-facing buyonline screens (Landing → OTP → … → Satisfaction) stay in their own app and keep PruRed branding. Aegis and BuyOnline can talk to the same engine but their UI systems are intentionally distinct: BuyOnline is brand-led; Aegis is information-led.

A *single* integration point matters: when a customer-facing buyonline quote requires UW referral, it surfaces in the Aegis **UW Queue**. Aegis writes the UW decision; the buyonline app picks it up.

---

# Appendix B — Composable APIs (Compose Multiplatform sketch)

These are the headline API shapes for the Aegis design system. They are intentionally close to Material-3 idioms so Compose veterans recognise them, but renamed/scoped to keep the system coherent.

## B.1 Theme & tokens

```kotlin
@Immutable
data class AegisColors(
    val slate0: Color, val slate1: Color, val slate2: Color, val slate3: Color,
    val slate4: Color, val slate5: Color, val slate6: Color, val slate7: Color,
    val slate8: Color, val slate9: Color, val slate10: Color, val slate11: Color,
    val indigo50: Color, val indigo100: Color, val indigo500: Color, val indigo600: Color, val indigo700: Color,
    val success50: Color, val success100: Color, val success500: Color, val success700: Color,
    val warning50: Color, val warning100: Color, val warning500: Color, val warning700: Color,
    val danger50:  Color, val danger100:  Color, val danger500:  Color, val danger700:  Color,
    val info50:    Color, val info100:    Color, val info500:    Color, val info700:    Color,
    val premium50: Color, val premium100: Color, val premium500: Color, val premium700: Color,
)

@Immutable
data class AegisTypography(
    val displayXl: TextStyle, val displayLg: TextStyle,
    val head1: TextStyle, val head2: TextStyle, val head3: TextStyle,
    val bodyLg: TextStyle, val body: TextStyle, val bodySm: TextStyle,
    val caption: TextStyle, val micro: TextStyle,
    val numLg: TextStyle, val num: TextStyle, val numSm: TextStyle,
)

@Immutable
data class AegisSpacing(
    val s1: Dp, val s2: Dp, val s3: Dp, val s4: Dp,
    val s5: Dp, val s6: Dp, val s7: Dp, val s8: Dp, val s9: Dp,
)

@Immutable
data class AegisElevation(val tier0: Modifier, val tier1: Modifier, val tier2: Modifier, val tier3: Modifier, val tier4: Modifier, val tier5: Modifier)

val LocalAegisColors      = staticCompositionLocalOf<AegisColors> { error("AegisTheme not provided") }
val LocalAegisTypography  = staticCompositionLocalOf<AegisTypography> { error("AegisTheme not provided") }
val LocalAegisSpacing     = staticCompositionLocalOf<AegisSpacing> { error("AegisTheme not provided") }
val LocalAegisDensity     = staticCompositionLocalOf { Density.Comfortable }

@Composable
fun AegisTheme(density: Density = Density.Comfortable, content: @Composable () -> Unit) { … }
```

## B.2 Shell

```kotlin
@Composable
fun AegisShell(
    railState: RailState,
    topBar: @Composable () -> Unit,
    statusBar: @Composable () -> Unit,
    drawer: (@Composable () -> Unit)? = null,
    sheet: (@Composable () -> Unit)? = null,
    modal: (@Composable () -> Unit)? = null,
    palette: PaletteState,
    content: @Composable () -> Unit,
)
```

The shell handles ⌘K (palette), drawer/sheet/modal lifecycle, focus management, and global keyboard shortcuts. Pages render inside `content`.

## B.3 Table

```kotlin
@Composable
fun <T> AegisTable(
    data: List<T>,
    columns: List<AegisColumn<T>>,
    rowKey: (T) -> Any,
    selection: SelectionState<T> = rememberSelection(),
    sort: SortState = rememberSort(),
    filter: FilterState<T> = rememberFilter(),
    grouping: GroupingState<T>? = null,
    density: Density = LocalAegisDensity.current,
    rowActions: (@Composable (T) -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    onRowEdit: ((T) -> Unit)? = null,
    onBulkAction: ((BulkAction, Set<T>) -> Unit)? = null,
    emptyState: @Composable () -> Unit = { AegisEmptyState(...) },
    loadingState: @Composable () -> Unit = { AegisSkeleton(rows = 8) },
)

data class AegisColumn<T>(
    val id: String,
    val header: String,
    val width: ColumnWidth = ColumnWidth.Auto,
    val alignment: Alignment.Horizontal = Alignment.Start,
    val pinned: ColumnPin = ColumnPin.None,
    val sortable: Boolean = true,
    val filterable: Boolean = true,
    val editable: ((T) -> Boolean)? = null,
    val render: @Composable (T) -> Unit,
    val edit: (@Composable (T, onCommit: (T) -> Unit) -> Unit)? = null,
)
```

## B.4 Money field

```kotlin
@Composable
fun AegisMoneyField(
    value: Money,                 // paise: Long
    onValueChange: (Money) -> Unit,
    label: String,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    showFraction: Boolean = true, // paise shown
    locale: Locale = Locale("en", "IN"),
)
```

Parsing tolerates both `1,00,000` and `100000` and `1L`. Always renders `₹` and tabular figures on blur.

## B.5 Status pill

```kotlin
enum class AegisStatus { Draft, InReview, Scheduled, Live, Retired, Rejected }

@Composable
fun AegisStatusPill(status: AegisStatus, compact: Boolean = false)
```

The pill's colours come from `LocalAegisColors`. It always renders a leading shape (`●`/`◐`/`▶`/`○`/`▣`/`✕`) so colour is never the only carrier.

## B.6 Effective-date timeline

```kotlin
@Composable
fun EffectiveDateTimeline(
    items: List<VersionMarker>,    // each: id, label, effectiveFrom, effectiveTo, status
    now: Instant = Clock.System.now(),
    onClick: ((VersionMarker) -> Unit)? = null,
)
```

Renders a horizontal axis with the past on the left, "now" as a vertical line, scheduled markers to the right. Hover reveals dates and version IDs.

## B.7 Heatmap

```kotlin
@Composable
fun AegisHeatmap(
    rows: List<String>,            // labels
    cols: List<String>,            // labels
    values: List<List<Double?>>,   // [row][col]
    scale: HeatmapScale = HeatmapScale.Sequential(),  // Sequential | Diverging(midpoint = 0.0)
    formatCell: (Double) -> String = { "%.0f".format(it) },
    onCellClick: ((row: Int, col: Int, v: Double?) -> Unit)? = null,
    onCellHover: ((row: Int, col: Int, v: Double?) -> Unit)? = null,
)
```

Used by Rate Table Manager (15×13 sequential) and by the rate-diff drawer (diverging on Δ).

## B.8 Sankey

```kotlin
@Composable
fun AegisSankey(
    nodes: List<SankeyNode>,
    flows: List<SankeyFlow>,         // (fromIndex, toIndex, value)
    formatValue: (Double) -> String,
    onFlowHover: ((SankeyFlow) -> Unit)? = null,
    height: Dp = 320.dp,
)
```

The Home dashboard premium-flow.

---

# Appendix C — Accessibility test plan (WCAG AAA)

A line-by-line test checklist. The dashboard is shipped only when *every* item passes.

1. **Contrast** — `slate-8` on `slate-0`: 9.7:1 ✓ (AAA needs ≥ 7:1).
2. **Contrast** — `indigo-500` button text (white) on `indigo-500` bg: ≥ 4.5:1 (large text AA). Verified by token tests.
3. **Focus visible** — every interactive control shows a 2px `indigo-500` outer ring on `:focus-visible`. Verified by Compose UI tests asserting `FocusInteractionSource` styling.
4. **Keyboard reachable** — every primary action is reachable by Tab. Visual focus order matches reading order.
5. **Skip-link** — first focusable element on every page is `Skip to main content`.
6. **Heading structure** — exactly one `h1` per page, logical nesting.
7. **ARIA** — icon-only buttons have `contentDescription`; tables have `Modifier.semantics { role = Role.Table }` plus column headers as `Role.ColumnHeader`.
8. **Reduced motion** — `LocalAccessibilityManager.reduceMotion == true` disables 250ms drawer slide; replaced with instant fade.
9. **Form errors** — every error message is announced to screen readers via `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.
10. **Money** — every money cell exposes a long-form contentDescription (`"One lakh twenty-one thousand five hundred rupees"`).
11. **Status pills** — every pill exposes its status as text via contentDescription (not just colour + shape).
12. **Tooltips** — appear on focus, not only on hover.
13. **Modals** — focus trap; Esc closes; restored focus on close.
14. **Drawer** — same as modal.
15. **Tables** — row navigation announces row index / N.
16. **Toasts** — announced; auto-dismiss long enough (≥ 5s) for "Undo" to be discovered.
17. **Charts** — every chart has a tabular fallback view (toggle in chart's overflow menu) and per-segment ARIA labels.
18. **Forms** — every input has a programmatic label (Material `TextField` label slot is mandatory, never placeholder-only).
19. **Density** — does not affect contrast (verified by snapshot tests at each density level).
20. **High-contrast mode** — when OS reports high-contrast, hairline borders thicken from 1px to 2px and `slate-3` is swapped for `slate-5`.

---

# Appendix D — Localisation (i18n) implementation contract

1. **Key registry.** Every UI string is keyed: `i18n("rate_table.diff.summary.title")`.
2. **Locale list.** `en-IN` (canonical) → `hi-IN` → `bn-IN` → `mr-IN` → `te-IN` → `ta-IN` → `gu-IN`. Marathi / Bengali first because of policyholder demographic concentration.
3. **Formatting.**
   - Currency: always Indian grouping, always `₹`. The `AegisMoneyField` is locale-agnostic for the *business* dashboard. (Customer buyonline can localise digit script.)
   - Date: ISO 8601 storage. Display: `2026-05-20` in `en-IN`; `२० मई २०२६` only in customer surfaces, never here.
   - Plurals: ICU MessageFormat used (`"{count, plural, one {1 quote} other {# quotes}}"`).
4. **Translation memory.** Translations live in `:design-system:i18n/<locale>.json`. Pulled into a Compose `LocaleContext` at the shell.
5. **String length.** Hindi / Tamil expand by 30%+ vs English; layouts use `Modifier.weight` not fixed widths.
6. **Right-to-left.** Not required for these locales but the shell supports `LocalLayoutDirection` so future Urdu support is one switch away.
7. **Translation review.** Compliance-sensitive strings (UIN, GST, free-look, ombudsman) are locked and not user-translatable.

---

# Appendix E — Onboarding & help — content sketch

## E.1 First-run product tour

90 seconds. Six steps. Each step pins a real on-page element with a callout.

1. **The shell.** "On the left is your map. On top is search and your profile. The bottom strip tells you what's live."
2. **The Home dashboard.** "Every morning, this is your one-look briefing. KPIs at the top, premium flow in the middle, your approvals at the bottom."
3. **Editing a plan.** "Plans autosave as you type. Nothing goes live until you Publish, and Publishing always asks a second pair of eyes."
4. **Importing Excel.** "Drag a workbook. We'll diff it against today's rates before anything changes. If anything goes wrong, you can roll back for 30 days."
5. **Approving a rate change.** "Open the diff, run the 100-quote test bench, then approve or reject. The customer sees the new rate from the effective date you choose, not before."
6. **Help is always one keystroke away.** "Press `?` any time. Press `⌘K` to search the entire app — plans, quotes, covers, even glossary terms."

## E.2 Empty-state micro-essays (sampler)

- **Products empty**: "You don't have any approved products yet. Most teams start by importing their IRDAI-approved workbook. Each product needs a UIN; you'll be asked for one before you can publish."
- **Rate Tables empty**: "Rate tables live in versions. Import an Excel workbook to create your first version. Future versions will diff cleanly against this one, so nothing ever ships without review."
- **UW Queue empty**: "Nothing in your queue right now. Cases land here automatically when a customer triggers a referral rule (e.g., age > 60 + PED, BMI > 30, or SI > ₹50L)."
- **Audit log empty**: "The audit log fills up the moment anyone makes a change. Until then, all you'll see here are system events like engine deployments."

## E.3 Aegis Glossary entries (sampler)

- **UIN.** "IRDAI's 4-digit Unique Identification Number for an approved retail health insurance product. Mandatory to publish. Issued in the format `1145H79V02` where the trailing `V02` is the revision."
- **NCB.** "No-Claim Bonus. A premium discount or sum-insured uplift on renewal if the policyholder didn't claim in the prior year. PHI offers a cumulative 50% NCB."
- **Free-look.** "The 15-day window after policy issuance during which a customer can cancel for a near-full refund (less stamp duty + pro-rata risk premium). Mandated by IRDAI."
- **Master Circular 2024.** "IRDAI's June 2024 Master Circular on Health Insurance. Mandates AYUSH parity, mental-health parity, HIV/AIDS coverage, and standardisation of pre/post-hospitalisation."
- **Persistency.** "The percentage of policies still in force at 13/25/37/49 months from issuance. A regulator-published metric; ours target 80/70/60/50."
- **Accumulation base.** "An identifier (e.g., `R23`) that names a set of cover columns whose rates are summed before applying the plan's discount cap. Excel originally encoded these as named ranges; we surface them as first-class objects."

## E.4 Help search index

Every help article carries: a title, 2-line summary, full markdown body, related links, "Was this useful? 👍 / 👎". Surfaced through ⌘K under the "Help" section. Articles cluster around the same 13 surfaces.

---

# Appendix F — Engineering hand-off — module sketch

We propose four new Gradle modules; the existing `:shared`, `:server`, `:desktop`, `:buyonline` stay. Nothing is renamed.

```
:design-system          Pure Compose Multiplatform UI kit. Stories in :design-system:catalog.
:design-system:catalog  Story-book app for visual regression + manual review.
:dashboard              The Aegis app itself. Depends on :design-system, :shared.
:dashboard:test         Compose UI tests + golden-image regression for charts.
```

Existing modules:

- `:shared` is unchanged and is the engine source of truth (audit §1). The dashboard never re-implements pricing; it only reads from `PricingEngine` via a thin `DashboardRepository` interface.
- `:server` exposes new REST endpoints for the dashboard (`GET /api/admin/plans`, `POST /api/admin/plans/{id}/publish`, etc.).
- `:desktop` retains its calculator screen but the new dashboard *replaces* the configurator screen by linking out to `:dashboard`.
- `:buyonline` is unchanged.

**Repository contracts** (excerpt):

```kotlin
interface PlanRepository {
    suspend fun list(filter: PlanFilter): PagedList<PlanSummary>
    suspend fun get(id: String): PlanDetail
    suspend fun createDraft(from: PlanId? = null): PlanDetail
    suspend fun saveDraft(draft: PlanDraft): PlanDetail
    suspend fun submitForReview(id: PlanId, reviewer: UserId, reason: String, evidence: List<FileRef>): Approval
    suspend fun approve(approvalId: ApprovalId, comment: String?): Approval
    suspend fun reject(approvalId: ApprovalId, comment: String): Approval
    suspend fun rollback(id: PlanId, toVersion: Int, reason: String): Approval
}

interface RateTableRepository { /* analogous */ }
interface CoverCatalogRepository { /* analogous */ }
interface DiscountRepository { /* analogous */ }
interface RuleRepository { /* analogous */ }

interface QuoteRepository {
    suspend fun search(filter: QuoteFilter): PagedList<QuoteSummary>
    suspend fun get(id: QuoteId): QuoteDetail
    suspend fun comparePair(a: QuoteId, b: QuoteId): QuoteComparison
}

interface UnderwritingRepository { /* analogous */ }
interface AuditRepository {
    suspend fun stream(filter: AuditFilter): Flow<AuditEvent>
    suspend fun verifyHashChain(window: ClosedRange<Instant>): HashChainVerification
}
```

Each repository has a `Local*` (read from `shared`) and a `Remote*` (HTTP) implementation. The dashboard prefers `Remote*` when the server is reachable and falls back to `Local*` for read-only views — consistent with how the desktop calculator already handles offline mode.

**Pricing-engine integration:**

```kotlin
class PricingPreviewCoordinator(
    private val engine: PricingEngine,
    private val dataProvider: RateDataProvider,
) {
    private val previewState = MutableStateFlow(PreviewState.idle())

    fun observe(): StateFlow<PreviewState> = previewState

    fun update(request: QuoteRequest) {
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(150) // debounce keystrokes
            previewState.value = PreviewState.loading
            runCatching { engine.calculate(request, dataProvider) }
                .onSuccess  { previewState.value = PreviewState.success(it) }
                .onFailure  { previewState.value = PreviewState.error(it) }
        }
    }
}
```

The coordinator is what powers the Plan Configurator's right-rail live preview. It is the *only* place where the dashboard ever calls the engine. Centralising this point means the dashboard cannot ever drift from the engine's math.

---

# Appendix G — Frequently-cited competitor benchmarks (one-paragraph each)

- **Linear.** The gold standard for SaaS keyboard UX (`⌘K`, `g h`, `J/K`, instant filters), drawer-first edits, polished motion, density toggle, and a "command-palette mindset" that makes a power user 4× faster. Aegis copies the *interaction model* unapologetically.
- **Stripe Dashboard.** The gold standard for financial UI density: tabular figures, money columns, sparkline KPIs, sober colour palette, "developer-grade" rigor. Aegis copies Stripe's *data philosophy*: prefer numbers over icons, prefer precision over decoration.
- **Airtable / Retool.** The gold standard for editable tables. Aegis copies the *Aegis-table grammar*: column resize/reorder/pin/freeze, inline edit, group-by, multi-select with bulk action bar.
- **Notion.** The gold standard for the *empty state with a tutor* and "everything is inline-editable" mental model. Aegis copies the *autosave-with-soft-publish* idea.
- **Figma.** The gold standard for multi-user cursors and CRDT-backed collaboration. Aegis copies the *live cursor + field-level lock* approach.
- **Vercel.** The gold standard for "deployments" semantics — Aegis treats *publish* as a deployment, with diffs, schedule, rollback, and a clean read-only frozen snapshot.
- **Salesforce Lightning.** The gold standard for *approval processes* and *audit trails* in enterprise B2B. Aegis copies the *maker-checker workflow* with reviewer assignment + evidence attachment.
- **Pipefy.** The gold standard for *visual rule / process builders* approachable to non-engineers. Aegis copies the *When/Then/Else block builder* in the Business Rules Editor.
- **Mixpanel / Stripe Sigma.** The gold standard for *self-service analytics*. Aegis's Reports section borrows the "every report is also a recipe — saveable, scheduleable, exportable" pattern.

---

# Closing note

The current desktop calculator is a one-window read-only tool. The buyonline app is a sales funnel. Neither speaks to the operator who runs the business. **Aegis is that missing third surface.** It is the bridge between the actuary's Excel reality and the customer's quote experience; the audit's 1,356 findings collapse, in large part, into the question *"who edits the system?"* and Aegis is the answer.

Five passes, four appendices, one north-star screen. Light theme. Indian-rupee-native. IRDAI-compliant. Compose-Multiplatform-implementable. Best in the world for the audience it serves.

— Plan complete (through Pass 5).

---

# Pass 6 — Implementation strategy (enhancement round)

> **What I'm fixing from Pass 5.** Pass 5 closed the *design* of Aegis. What it did not say is *how this ships*. Pass 6 closes that gap: it answers "what's the smallest thing we can put in front of users this week, and how does it grow into the full vision without a re-write?" This is the bridge between the design document and the first PR.

## 6.1 — Ship order

The 13 surfaces do not all ship together. The recommended order:

```
Ship-1 (Week 1)  : Aegis foundation (theme tokens + AegisShell + 8 core
                   components). No surfaces yet. Validates the design system.
Ship-2 (Week 2)  : Home Dashboard (5 KPI tiles, recent activity list, hero
                   Sankey as static placeholder). Read-only. No 4-eyes yet.
Ship-3 (Week 2)  : Quote Explorer (existing data, just a better lens).
Ship-4 (Week 3)  : Plan Configurator — READ MODE only. Replaces the existing
                   ConfiguratorScreen visually but no editing yet.
Ship-5 (Week 3)  : Cover Catalog Manager — read mode. Surface 50+ covers.
Ship-6 (Week 4)  : Plan Configurator EDIT MODE + first taste of maker-checker.
                   Requires Phase 2 audit_event table.
Ship-7 (Week 5)  : Rate Table Manager + diff drawer. Requires effective-date
                   timeline component (new, reusable in Discount Manager).
Ship-8 (Week 6)  : Excel Import Studio (dry-run, diff, rollback).
Ship-9 (Week 6)  : Discount Manager + Business Rules Editor.
Ship-10 (Week 7) : UW Queue. Requires structured PED/CI capture (Phase 2 of
                   buyonline; couples buyonline and Aegis here).
Ship-11 (Week 8) : Reports & Analytics. Needs warehouse decision.
Ship-12 (Week 8) : Audit & Governance browser (hash-chain inspector).
Ship-13 (Week 9) : Settings (users, roles, GST rate, integrations).
```

Every week ends with one or two new surfaces in users' hands. Ship-1 through Ship-13 is the executable version of Pass 5's "everything at once" framing.

## 6.2 — Compose module decomposition

Aegis lives in a new module so it can be reused by both desktop AND a future Compose-for-Web surface:

```
:shared            (KMP) — domain, engine, validators, money  ◀── unchanged
:shared-ui         (KMP commonMain + jvmMain + wasmJsMain)
                   ├── theme/      AegisColors, AegisTypography, AegisShapes,
                   │               AegisSpacing, AegisElevations, AegisMotion
                   ├── components/ AegisShell, AegisTable<T>, AegisStatusPill,
                   │               AegisMoneyField, AegisDateField,
                   │               AegisDrawer, AegisSheet, AegisModal,
                   │               AegisCommandPalette, AegisToast,
                   │               AegisEmptyState, AegisSkeleton,
                   │               AegisCallout, AegisBadge, AegisButton,
                   │               AegisInput, AegisSelect, AegisCombobox,
                   │               AegisCheckbox, AegisRadio, AegisSwitch,
                   │               AegisTabs, AegisBreadcrumbs,
                   │               AegisSparkline, AegisSankey, AegisHeatmap,
                   │               EffectiveDateTimeline, AegisDiffViewer,
                   │               AegisKbd, AegisTooltip
                   ├── viz/        chart catalogue (line/area/bar/sankey/…)
                   ├── motion/     spring presets, prefers-reduced-motion guard
                   └── tokens/     ColorTokens, FontTokens, SpacingTokens,
                                   ElevationTokens, MotionTokens
:aegis             (JVM Compose Desktop) — the 13 surfaces. Depends on
                   :shared-ui and :shared. Replaces the existing :desktop
                   eventually; until then they coexist with a switch in
                   App.kt (`if (FeatureFlags.aegis) AegisRoot() else
                   LegacyDesktopRoot()`).
:desktop           (JVM Compose Desktop) — UNCHANGED until Ship-4 onwards
                   gradually deprecates its screens.
:buyonline         (KMP Compose) — keeps its own visual language because it's
                   customer-facing, NOT business-facing. The customer journey
                   should NOT look like a database admin tool. But it imports
                   `:shared-ui` for primitives (AegisMoneyField, AegisDateField,
                   accessibility helpers) where useful.
```

Why a separate `:aegis` module rather than putting screens into `:desktop`? Because (a) it lets the existing desktop screens keep working while Aegis is built, (b) it forces a clean dependency boundary between business surfaces and customer surfaces, (c) it lets the dashboard ship later via Compose-for-Web if/when business users want a browser experience.

## 6.3 — Foundation week — concrete deliverables

These ship in **Week 1** to unblock everything else.

### Theme tokens (`:shared-ui/theme/`)

```kotlin
object AegisColors {
    // Slate ramp (1=darkest text, 12=lightest background) — used for surfaces,
    // borders, text. NEVER hardcode "#hhhhhh" anywhere outside this file.
    val slate1  = Color(0xFF0B0E13);   val slate2  = Color(0xFF1A1F26)
    val slate3  = Color(0xFF2A323C);   val slate4  = Color(0xFF3A4350)
    val slate5  = Color(0xFF505968);   val slate6  = Color(0xFF6C7787)
    val slate7  = Color(0xFF8893A1);   val slate8  = Color(0xFFA8B0BC)
    val slate9  = Color(0xFFCFD3DA);   val slate10 = Color(0xFFE3E6EB)
    val slate11 = Color(0xFFF1F2F5);   val slate12 = Color(0xFFFAFBFC)

    // Brand accent — Indigo. NOT PruRed; PruRed is too saturated for a dense
    // business UI, and we want a calm "trust" hue. PruRed stays on customer
    // surfaces (buyonline) where it belongs.
    val indigo50  = Color(0xFFEFF1FF);  val indigo100 = Color(0xFFDEE3FF)
    val indigo300 = Color(0xFF8B96FF);  val indigo500 = Color(0xFF4F5CFF)
    val indigo700 = Color(0xFF333DCC);  val indigo900 = Color(0xFF1F2480)

    // Semantic ramps
    val success50 = Color(0xFFE9F8EF);  val success500 = Color(0xFF1FA34A); val success700 = Color(0xFF12783A)
    val warn50    = Color(0xFFFFF5E0);  val warn500    = Color(0xFFE19500); val warn700    = Color(0xFFA56C00)
    val danger50  = Color(0xFFFDECEE);  val danger500  = Color(0xFFD43141); val danger700  = Color(0xFFA1212F)
    val info50    = Color(0xFFE5F4FE);  val info500    = Color(0xFF1E84D8); val info700    = Color(0xFF14619E)
}
```

Spacing scale `4/8/12/16/24/32/48/64`. Radii `4/8/12/20/full`. Elevations `0/1/2/8/16`. Motion: 150ms ease-out default; honour `prefers-reduced-motion`.

### `AegisShell` skeleton (single composable that frames every surface)

```kotlin
@Composable
fun AegisShell(
    activeSurface: AegisSurface,
    onSurfaceChange: (AegisSurface) -> Unit,
    user: AegisUser,
    content: @Composable () -> Unit
) {
    Row(Modifier.fillMaxSize().background(AegisColors.slate12)) {
        AegisSideNav(activeSurface, onSurfaceChange)
        Column(Modifier.weight(1f)) {
            AegisTopBar(user)
            Box(Modifier.weight(1f).padding(AegisSpacing.s4)) { content() }
            AegisToastHost()
        }
    }
}
```

This is the only frame the 13 surfaces ever render inside. Adding a new surface is a matter of adding an `AegisSurface` enum entry and a content composable.

## 6.4 — Migration from existing `:desktop` to `:aegis`

The current `ConfiguratorScreen`, `CalculatorScreen`, `ImportScreen` are *not* discarded — they continue to ship until their Aegis replacements reach feature-parity. Strategy:

| Existing screen | Aegis replacement | Bridge |
|---|---|---|
| `desktop/.../calculator/CalculatorScreen.kt` | Stays. It's an actuary's working tool, not a business dashboard. Gets a polish pass (Aegis components inside its existing layout). | Component-by-component refactor; no IA change. |
| `desktop/.../configurator/ConfiguratorScreen.kt` | `:aegis/.../PlanConfigurator.kt` | Ship-4 (read mode); Ship-6 (edit mode); old screen retires when Ship-6 lands. |
| `desktop/.../importscreen/ImportScreen.kt` | `:aegis/.../ExcelImportStudio.kt` | Ship-8 replaces; old screen retires. |

No big-bang migration. Feature flags gate Aegis surfaces (`FeatureFlags.aegisHome`, `.aegisPlanConfigurator`…) so partial rollout is safe.

## 6.5 — Performance budget — how we measure "page load < 200ms perceived"

- **First Meaningful Paint < 200ms** for every surface — instrumented via a `TraceFirstPaint` LaunchedEffect that fires once the first row composes. Logged to `AegisTelemetry`; debug overlay shows the number.
- **List virtualisation threshold = 200 rows** — anything bigger MUST use `LazyColumn` with stable keys. A detekt custom rule catches `Column { items.forEach { … } }` on lists.
- **No allocations in tight loops** — Money operations on the value class; AegisTable column-pinning uses `derivedStateOf` not raw lambdas.
- **Recomposition counters** in dev mode — `Modifier.recompositionHighlight()` outlines re-composed nodes; reviewed weekly.

CI parses telemetry from test runs and fails the build if any render exceeds the budget on the reference machine.

## 6.6 — Telemetry overlay (`⌘⇧T`)

Pass 5 mentions a telemetry overlay for the product team. Implementation: a `TelemetryOverlay` composable bound to `⌘⇧T` in `AegisShell`. Shows the last 50 events (surface · action · ms). Available to anyone with the `dev` flag in `AegisUser`; product managers see a richer version with funnel + drop-off counters.

## 6.7 — Component gallery (`:aegis-gallery`)

A runnable app that renders every component in every state (default / hover / pressed / focused / disabled / loading / error / empty). Exists to:

1. let designers review components without booting the full dashboard,
2. give engineering a single place to add visual-regression screenshots,
3. catch theme regressions when tokens change (CI runs the gallery and diffs PNGs against the baseline).

## 6.8 — Test strategy for screens

| Layer | What we test | Tool |
|---|---|---|
| Tokens & theme | Hex values, contrast ratios, type-scale assertions | JUnit |
| Pure composables | Render once, snapshot the layout (golden PNG) | Compose UI test + Roborazzi/equivalent |
| Stateful composables | State transitions, derived state, onClick handlers | `composeTestRule.setContent { … }` |
| Surfaces (integration) | End-to-end flow on a deterministic data fixture | `ComposeUiTest` driven by `FakeAegisRepo` |
| Visual regression | PNG diff of every gallery page | CI job + manual review |

No surface ships without at least: 1 composable golden test + 1 keyboard navigation test + 1 accessibility test.

## 6.9 — Design token versioning + backwards compatibility

Tokens are a **public API** within the codebase. Renaming `AegisColors.slate11` to `AegisColors.bgMuted` is a breaking change.

- Tokens follow internal semver; minor bumps add, major bumps rename/remove.
- Every token rename has a `@Deprecated` shim for one major release before removal.
- A `:aegis-token-export` task emits the tokens as JSON so design tooling (Figma plugin) can sync.

## 6.10 — Internationalisation architecture

Pass 5 listed 7 locales. Implementation:

- `:aegis/src/commonMain/resources/i18n/{en,hi,ta,te,kn,mr,bn}.json`
- Single `Strings.kt` object with `Strings.dashboard.title()` resolved against `LocalLocale.current`.
- Number formatter respects locale (Hindi uses Western numerals + Indian grouping; Tamil supports Tamil numerals when requested; default is Indian grouping with Western numerals).
- LTR only; RTL is unnecessary for the 7 chosen locales.
- Font loading: each locale pre-declares its primary font (Inter for Latin; Noto Sans Devanagari, Tamil, Telugu, Kannada, etc. for the scripts). Lazy-loaded on first surface render to keep cold start fast.

## 6.11 — Accessibility audit tooling

CI runs an automated A11y audit on every gallery page asserting:

- Every interactive node has a non-empty `contentDescription`.
- Tab order matches reading order.
- Focus indicators have ≥3:1 contrast against the background.
- No text uses colour alone to convey meaning (`StatusPill` has both colour AND icon).
- Touch targets are ≥44dp.
- Hex contrast ratio for every text-on-background pair is ≥7:1 (WCAG AAA).

Failed audits block merge.

## 6.12 — Observability of Aegis itself

Aegis is an internal tool that is *also* the most-critical operational surface — when it breaks, the business cannot edit plans. So Aegis itself is observable:

- Every navigation event → telemetry
- Every "Publish Live" action → audit_event (server-side)
- Every error toast → Sentry/Bugsnag with screen + state snapshot
- Every save-failed → autosave-conflict telemetry → resolution surface
- Slow render budget breach → telemetry

## 6.13 — Risk register for Aegis implementation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Designer/engineer disagree on a token after Ship-1 | High | Medium | Token versioning + design review gate |
| Maker-checker flow needs DB transaction semantics we don't yet have | Medium | High | Spike in Week 1; falls back to optimistic locking if needed |
| Excel Import Studio rollback semantics differ from PostgreSQL backup model | Medium | High | Use Postgres `pg_dump` snapshots per import; build the diff viewer first |
| Compose Desktop performance below 60fps on Linux | Medium | Medium | Run the gallery on every supported OS in CI |
| WASM target lags Compose Desktop for some components | High | Low | Aegis is desktop-first; web is a *future* not a *day-one* target |
| Real-time multi-user collaboration is harder than Figma makes it look | High | Medium | Ship pessimistic locking first (Ship-6); CRDT in a later ship |
| Translation review bottleneck | Medium | Medium | Buffer of 6 working days per locale before launch |
| Theme tokens churn during Ship-1 invalidate downstream surfaces | High at first, dropping | Low individually | Single design-review gate before Ship-2 starts |

## 6.14 — Definition of done for Aegis v1

Aegis v1 is "done" when, on the reference machine:

- All 13 surfaces ship and pass the Pass-6 test matrix.
- A non-engineer business user can complete the *Top-10 Aegis user journeys* (see Pass 1) end-to-end without engineer help.
- Every action that mutates state writes an `audit_event` row.
- Maker-checker is enforced on rate-table publish, plan publish, discount publish, business-rule publish.
- 95th-percentile screen render < 200ms; 99th < 400ms.
- A11y audit passes WCAG AAA on every screen.
- Telemetry overlay is wired and reviewed by product weekly.
- Localised to en + hi at minimum (5 regional locales can land post-v1).
- Docker container of the dashboard binary builds + runs on `linux/amd64` and `darwin/arm64`.

---

# Pass 7 — User-testing protocol + rollout choreography (enhancement round 2)

> **What I'm fixing from Pass 6.** Pass 6 closed the *engineering build order*. What it does not say is *how we know users will love this*. Pass 7 closes that gap with concrete journey storyboards, real-user testing protocols, rollout choreography, and the success metrics we'll judge Aegis by.

## 7.1 — Five canonical user journeys (storyboards)

Every Aegis design decision must be defensible against these five real-life moments.

### Journey A — "It's Tuesday at 11am and we need to launch PHI Flagship 3 by Friday."

**Persona:** Vidya, Product Lead, 6 yrs at PRU Health. Comfortable with Excel, terrified of SQL. Currently does this work by emailing the actuary and waiting 3 days.

```
T+0:00  Vidya opens Aegis on Monday. ⌘K → "create plan" → press ⏎.
        Plan Configurator opens in Draft mode with a stencil for Flagship variants.
T+0:30  She enters Plan Name, picks PlanType=DOMESTIC_FLAGSHIP, copies SI grid
        from Flagship 2 via the "Copy from existing plan" action.
T+2:00  Switches to Coverage tab; the allowedCoverIds matrix is pre-checked
        based on the Flagship template. She unchecks 3 covers (per actuary's
        spec sheet she has in the next tab over) and checks 2 new ones.
T+3:30  Rate Configuration tab: clicks "Use Flagship 2 rates × 1.1" preview
        button; the right-rail live preview re-runs the engine and shows the
        new premium for a 35yo / ₹10L / Zone 1 reference quote.
T+4:00  Eligibility tab: sets minAge=18, maxAge=70. Geography tab: Domestic.
T+5:30  Hits "Save Draft". Status pill flips to DRAFT.
T+6:00  Right-rail "Send for review" → autocomplete picks Ananya (actuary).
        Reviewer drawer opens for Vidya to attach evidence (3 PDFs from the
        File and Use submission, the spec sheet, the chief actuary's email).
T+7:00  Status flips IN_REVIEW. Ananya gets a Slack ping + Aegis inbox item.
T+1 day Ananya reviews Coverage + Rate Config tabs, clicks "Approve". Status
        flips APPROVED. A second Slack ping fires to CTO for the "Live" step.
T+1.5d  CTO clicks "Publish Live → scheduled for Friday 9am". Status flips
        APPROVED → LIVE (effective Friday 9am). audit_event row records all
        five steps with hash-chain entries.
T+Fri 9am  Plan goes live. Buy-online customers can quote for Flagship 3.
```

**What Aegis must do well for this journey:**
- ⌘K must be instant and accurate (Linear-level palette).
- Copy-from-existing must produce a plausible starting point in < 1 second.
- The reference-quote live preview must run in < 200ms (per Pass 6 §6.5).
- Send-for-review must require evidence (attach at least one document).
- Maker-checker enforces that Vidya cannot self-approve.
- Audit_event captures the chain so legal can prove the process months later.
- "Scheduled publish" honours a future effective-date timeline component.

### Journey B — "Rate table needs a 6% hike across PHI Senior."

**Persona:** Arjun, Chief Actuary, 15 yrs in life + health. Excel native; suspicious of any tool that hides math.

```
T+0:00  Arjun opens Rate Table Manager. Filters to PHI Senior. Clicks
        "New version → start from Live v3.2".
T+0:30  Diff drawer opens showing v3.3 (Draft) vs v3.2 (Live), side-by-side.
        Every cell editable; cells that change colour-shift to amber.
T+1:00  He enters a 6% increase formula in the "Bulk action → multiply
        selection by 1.06" command. The grid recalculates; ~600 cells turn
        amber.
T+1:30  He spot-checks 5 cells against his own Excel; matches to the paise.
T+2:00  Clicks "Run impact simulation". Aegis re-runs 50 reference quotes
        spanning age × SI × zone × family-type combinations through the
        pricing engine using the new draft rates. Result: a small table
        showing min/median/max premium delta vs v3.2.
T+2:30  Median delta is 6.0% as expected. Max is 7.1% (the elderly tier band
        boundary causes a step-up). He confirms this is consistent with the
        sub-standard loading rule.
T+3:00  Clicks "Send for review" → Vidya (Product) for product-line sign-off.
        Vidya signs off. Audit row.
T+1 day CTO publishes → effective the 1st of next month. Old policies on
        v3.2 stay on v3.2 (rate-table version is stamped into every quote
        at calc time, so renewals respect the version they were issued at —
        per the audit's "rateTableVersion in QuoteResult" addition).
```

**What Aegis must do well for this journey:**
- Bulk-action commands must be reversible.
- Cell-level diff must be exact (no FP fuzz).
- Impact simulation must run against the real engine, not a synthetic shortcut.
- The 50-reference-quote benchmark is configurable and saved as a "rate-test pack".
- Rate-table versioning is immutable — once Live, only future versions can change it.

### Journey C — "Excel import wrecked the rate table at 5 pm Friday."

**Persona:** Manish, Ops engineer, the human in the loop when something goes wrong.

```
T+0:00  Pager: "/api/import/upload returned 500; rate calculations now
        returning ₹0 for 6 plans". Manish opens Aegis → Excel Import Studio.
T+0:30  The most recent import shows "FAILED — sheet 'PHI_Flagship3' missing
        Age Band column". Pre-import diff was never reviewed (impatient ops
        person clicked Commit without dry-run).
T+1:00  Manish clicks "Rollback to previous snapshot" → confirms via typed
        Plan ID match (typed-confirmation guard from Pass 5 §scale).
        Snapshot is restored from `rate_table_snapshots` table; ~3 seconds.
T+1:30  Re-runs the 10 golden engine tests against the restored rate table;
        all green. Buy-online traffic resumes normal numbers.
T+2:00  Files an incident ticket. Postmortem includes: (a) dry-run should be
        mandatory not optional; (b) typed-confirmation should require a
        reason; (c) per-import slack notification for #engineering.
T+1 week Postmortem action items land in the Excel Import Studio: dry-run is
         now mandatory; commit requires an attached spec PDF.
```

**What Aegis must do well for this journey:**
- Rollback must be ONE click + typed confirm.
- Snapshots must be automatic — no operator decides whether to snapshot.
- Recovery must be < 5 minutes from page to resolution.
- The dry-run/diff/commit/rollback flow must be visually distinct and impossible to short-circuit accidentally.

### Journey D — "A new business user joins next Monday."

**Persona:** Rohan, freshly hired Product Manager. Has never seen this codebase or domain.

```
T-1 day Eng admin creates Rohan's Aegis account → Role: Product (read-only on
        everything except Plan Configurator drafts).
T+0min  Rohan logs in. Aegis shows a personalised dashboard: "Welcome,
        Rohan! Here's a 5-minute tour."
T+5min  Tour walks through Home → Plan Configurator → Cover Catalog →
        Reports. Each stop has a 1-paragraph "why this surface exists" essay
        (Aegis Glossary).
T+15min Rohan opens an existing plan to read it. Every field has a tooltip
        explaining what it does. The "i" icon next to "Smart Select" opens
        a 2-paragraph explanation including the cover's accumulation base
        and the Excel row it maps to.
T+1hr   Rohan attempts a Draft change to a plan. Aegis blocks Live publish
        but allows Draft + Send-for-review. He sends to Vidya for review.
T+Day 5 Rohan is productive without engineering hand-holding.
```

**What Aegis must do well for this journey:**
- First-run tour is mandatory but skippable; once dismissed, ⌘? re-launches it.
- Every field has a tooltip; every status pill has a legend.
- Glossary is searchable: ⌘K → "what is accumulation base".
- Permissions degrade gracefully: read-only users see the publish button as `disabled` with hover text "You need Product Lead role to publish".
- "Empty state with a tutor" pattern (Notion-inspired) on every surface.

### Journey E — "It's renewal season; we need to publish 14 plan refreshes in one week."

**Persona:** Vidya again, but at scale.

```
M+0:00  Vidya opens Aegis. Bulk-action mode: ⌘B.
M+0:10  Selects all 14 plans → "Apply seasonal endorsement template" → opens
        the endorsement template editor.
M+0:30  Edits: increase SI grid by 25%; bump max-discount-cap to 35%; add 3
        new add-on covers (from Cover Catalog) to every plan.
M+1:00  Preview shows the diff per plan. Aegis warns: "3 plans (PHI Senior,
        PHI Sub-Standard, PHI POSP) cannot accept some of these add-ons due
        to Business Rule R-14". Vidya excludes them; diff updates.
M+2:00  Sends for review → batch review drawer opens for Ananya. She reviews
        once for all 14 plans; signs off as a single batch (audit_event
        captures the batch + each individual plan).
M+1 day Batch live; renewal-season plans published. Buy-online + agents see
        the new options immediately.
```

**What Aegis must do well for this journey:**
- Bulk-actions are typed-confirmation gated.
- Cross-plan rule violations are surfaced before review, not at publish.
- Batch review is a first-class action, not 14 individual reviews.
- Audit_event captures both the batch and each individual artefact.

## 7.2 — Real-user testing protocol

Before each Ship-N goes live to the business team, the surface is validated by:

| Stage | Method | Pass criterion |
|---|---|---|
| 1. Internal eng dogfood | 5 engineers click through the gallery + the surface for 30 minutes each | No bug filed of severity > 2; no usability rant in #aegis-dogfood |
| 2. Product team review | 3 PMs walk a scripted scenario | Average task-completion time within 110% of design target; 0 "I don't understand what this does" moments |
| 3. Designer review | Senior product designer audits visual/motion/spacing | Pass / Fail with no medium issues outstanding |
| 4. Actuary review (for rate-affecting surfaces) | Chief Actuary signs off | Numbers match Excel to the paise |
| 5. Legal review (for PII/audit surfaces) | Legal confirms DPDP fields rendered correctly | No PII visible in plaintext; consent affordances correct |
| 6. Accessibility audit | Automated WCAG AAA test + manual screen-reader walk | All interactive elements navigable by keyboard + screen reader |
| 7. Real business user shadowing | 2 actual business users complete journey A or B with eng watching | < 1 question per minute; < 5 minutes per surface |

Each stage has a gate. No skipping. The cumulative effort is ~3 working days per surface — that's why Pass 6's ship cadence has 1-2 surfaces per week, not 13 in a hurry.

## 7.3 — Rollout choreography

| Surface | Cohort 1 (Week N) | Cohort 2 (Week N+1) | Cohort 3 (Week N+2) |
|---|---|---|---|
| Home Dashboard | 3 PMs | + Product team | + Whole business |
| Quote Explorer | 3 PMs | + Sales/agent team | + Whole business |
| Plan Configurator (read) | 3 PMs + Chief Actuary | + Product team | + Whole business |
| Plan Configurator (edit) | Chief Actuary + 1 PM | + Product team | + Whole business |
| Rate Table Manager | Chief Actuary only | + Actuarial team | + Product team |
| Excel Import Studio | Ops + 1 PM | + Product team | + General |
| Discount Manager | Product team | + Sales | + General |
| Business Rules Editor | Chief Actuary + Senior PM | + Product team | + General |
| UW Queue | UW team only | + Senior PM | + General |
| Reports & Analytics | Eng leads | + Product + Finance | + General |
| Audit & Governance | Internal Audit + Sec | + Eng leads | + Limited general |
| Settings | Eng admin only | + selected admins | + Customisable |

Every cohort uses a feature flag. Cohort 1 typically gets 24h to file blockers; if none, cohort 2 follows. Phase-3 cohort follows once cohort 2 has 1 week of clean operation. Surfaces never go GA without all three cohort phases passing.

## 7.4 — Adoption + success metrics

Aegis succeeds when:

| Metric | Baseline (today, no Aegis) | Target (Aegis v1) |
|---|---|---|
| Time from "new product idea" to "live plan" | 14 days | 3 days |
| Time from "rate change ask" to "new rate live" | 7 days | 1 day |
| % of plan changes that pass actuary review first time | ~40% | ≥ 90% |
| % of imports that require rollback | ~15% | < 1% |
| Engineering tickets per week for "tweak this rate" / "this plan field is wrong" | ~12 | < 2 |
| Time spent by Chief Actuary on routine rate maintenance | 12 h/week | < 3 h/week |
| Business-user satisfaction (quarterly survey, 1-5) | 2.1 | ≥ 4.2 |
| % of business decisions logged + auditable | ~20% (emails) | 100% (audit_event) |
| Onboarding time for new PM | 4 weeks | 1 week |
| % of regulatory audits passed first time | varies | 100% |

These are tracked from Day 1 of Phase 4. Quarterly business reviews lead with these numbers.

## 7.5 — Communication plan

Internal launch comms for every Ship-N:

- **Pre-launch (T-3 days)**: Slack post in #aegis-launches + email digest with screenshots + a 60s Loom of the surface.
- **Launch (T+0)**: Slack post + ⌘K palette pre-loaded with "What's new in Aegis" entry that opens an in-app changelog.
- **Post-launch (T+7 days)**: Adoption metrics shared in #engineering; bugs/feedback triaged.
- **Quarterly**: Aegis Town Hall — 45-min demo + Q&A with the business team. Designer + Eng Lead + Chief Actuary co-host.

## 7.6 — What Aegis is NOT (boundaries that prevent scope-creep)

- Aegis is **not a CRM**. Customer details belong in the customer-facing journey and in a future Salesforce/HubSpot integration.
- Aegis is **not a claims-management system**. Claim adjudication is a separate platform; Aegis only surfaces claim status at the policy level.
- Aegis is **not a financial-reporting tool**. Reports & Analytics shows operational metrics; financial GL stays in Tally / Zoho / Oracle.
- Aegis is **not a help desk**. Customer support agents use Zendesk / Freshdesk; Aegis links out to those tools when needed.
- Aegis is **not a marketing platform**. Campaigns, leads, and conversion funnels belong in Mixpanel / Amplitude + a CDP.

This boundary is enforced by code review: any PR adding a surface that crosses one of these lines is rejected and the work re-routed to the appropriate tool.

## 7.7 — Definition-of-done for Aegis v1.0 (full check)

Aegis v1.0 ships when:

- All 13 surfaces have completed the 7-stage user-testing protocol (§7.2).
- The 5 canonical user journeys (§7.1) are completable end-to-end by a real business user in the cohort-1 sample.
- 95th-percentile metrics (§7.4) are within target on staging for 2 consecutive weeks.
- Audit_event hash-chain has been verified clean for 30 consecutive days.
- 0 P0/P1 bugs open against any surface.
- All operational-excellence checklists (`AUDIT_REPORT.md` §10.5) pass.
- A non-engineer business user has independently completed Journey A or B without intervention.
- The Chief Actuary signs off in writing that Aegis can replace their Excel workflow.
- The internal-audit team signs off on the maker-checker enforcement.
- All localised content for en + hi is published; ta, te, kn, mr, bn translations have a 30-day eta.

---

# Pass 8 — AI-assist features (enhancement round 3)

> **What I'm fixing from Pass 7.** Passes 1-7 describe what a *better* dashboard looks like. Pass 8 describes what a **best-in-the-world** dashboard does that competitors cannot. The answer is *embedded intelligence* — Aegis quietly helps business users do their job correctly. Not chatbots; not autonomous agents that change rates on their own. Quiet, defensible, undo-able assists. Linear has the AI-assisted issue editor; Stripe Sigma has the "explain this number" tooltip; Notion AI has the inline "improve this sentence". Aegis needs its insurance-domain equivalents.

## 8.1 — Design constraints

Before listing features, lock down what AI-assist must NEVER do in this domain:

1. **Never change a number autonomously.** Suggest, predict, explain — never execute.
2. **Never hide its reasoning.** Every suggestion has a "why?" link that shows the inputs.
3. **Never invent a rate.** All predictions are derived from the actual engine output on synthetic inputs, NOT from a separately-trained model that could drift.
4. **Never bypass maker-checker.** AI suggestions still flow through Draft → In Review → Approved → Live like human-authored changes.
5. **Never use customer PII as a training signal.** All assist features train on plan-, cover-, and rate-level data (not customer level).
6. **Always be off-by-default per user.** Pass 8 is opt-in; the user toggles it in Settings.
7. **Never confidence-wash.** A suggestion either has > 90% confidence in our test pack or it isn't shown.

## 8.2 — The 12 AI-assist features

### F-1 — "Explain this number" (everywhere)

Right-click any displayed money figure → "Explain this number". A drawer slides in showing the cover IDs that contributed, the accumulation base used, age-band and zone factors, capped discounts, GST line item, and the rate-table version stamped. Implementation: every QuoteResult already carries a full breakdown; the "explain" drawer is a renderer.

### F-2 — Smart rate-change impact prediction (Rate Table Manager)

When the actuary edits a rate cell in Draft mode, Aegis re-runs the engine on a 50-quote reference pack and shows, in real-time: median premium delta, worst-case delta, plans most affected (top 5), and any new Business-Rule violations (highlighted red). Save is disabled until violations clear.

### F-3 — Cross-plan rule violation predictor (Plan Configurator)

When the user edits Plan A in Draft mode, Aegis checks whether the change creates a conflict on Plan A or with cross-plan Business Rules (R-14, R-20, etc.). Conflicts appear as inline callouts on the relevant tab.

### F-4 — Anomaly detection on the Home Dashboard

KPI tiles get a "▲ anomaly" badge when a metric deviates > 2σ from its 30-day median. Quotes today up 3σ → likely a campaign; conversion rate down 2σ → silent product breakage; UW backlog up 4σ → SLA breach imminent. Anomalies computed from audit_event + quotes; no separate ML model.

### F-5 — Suggested cover bundling (Cover Catalog)

When the user looks at Cover X, Aegis surfaces "Plans that include X also include …" — top 5 most-co-occurring covers across the 14 plans. Descriptive analytics, not prediction.

### F-6 — "Why was this quote rejected?" (Quote Explorer)

For any quote with `isValid = false`, the drawer shows a structured explanation mapping each error to: the business rule triggered, the input field at fault, a suggested fix.

### F-7 — Bulk-edit anomaly preview (Bulk operations)

When the user starts a bulk action across N plans, Aegis preflights against each and shows which would fail validation. User de-selects those before committing.

### F-8 — Smart UIN binding (Plan Configurator)

For a new plan, Aegis suggests a UIN format and registry slot based on planType + geographyScope + underwritingCategory. Still requires explicit UIN-registry approval before going Live.

### F-9 — Natural-language ⌘K commands (Command Palette)

⌘K → type "increase Senior rates by 6 percent effective 1 June" → Aegis parses intent and opens the Rate Table Manager pre-loaded with filter PHI_Senior + new draft + bulk-action × 1.06 + effective date 1 June 2026. Nothing happens until the user hits Save Draft + Send for Review. Parser uses a small intent-rule engine (no LLM in v1).

### F-10 — Renewal premium surprise prediction (Aegis Home)

Aegis Home shows "12 renewals due in 30 days where premium will increase > 15% vs prior year". Each is a CTA card → Renewal page → pre-message the customer with the age-band step-up explanation.

### F-11 — Suggested copy in Empty States (everywhere)

Empty states use Notion-style "tutor" copy. "No quotes yet" → "Try creating one in the Quote Calculator (Ctrl+1) — quotes show up here within seconds."

### F-12 — "Diff me" between any two artefacts (Plan / Rate Table / Discount)

Select any two versions of any artefact (or two plans, two rate tables, two business rules) → see a structured diff with semantic labels ("Rate raised by 6%", "Cover added: maternity"). The closest Aegis gets to a Git-for-product-managers experience.

## 8.3 — Implementation plan

Each feature ships behind a feature flag; none are required for Aegis v1.

| Feature | Ship plan | Dev-days | Depends on |
|---|---|---|---|
| F-1 Explain this number | v1.0 (parallel to surfaces) | 2 | Existing QuoteResult |
| F-2 Smart rate impact | v1.1 | 4 | Rate Table Manager (Ship 7) |
| F-3 Rule violation predictor | v1.1 | 3 | Business Rules Editor (Ship 9) |
| F-4 Anomaly detection | v1.1 | 4 | audit_event stream (Phase 2) |
| F-5 Cover bundling | v1.0 (cheap) | 1 | Cover Catalog (Ship 5) |
| F-6 Quote rejection explainer | v1.0 (cheap) | 1 | Quote Explorer (Ship 3) |
| F-7 Bulk-edit preview | v1.2 | 3 | Bulk operations + Pass 5 §scale |
| F-8 Smart UIN binding | v1.2 | 2 | UIN Registry (Phase 7) |
| F-9 Natural-language ⌘K | v1.3 | 5 | Command Palette + RateTableManager |
| F-10 Renewal surprise | v1.3 | 4 | Phase 5 Renewal flow |
| F-11 Empty-state copy | v1.0 (every surface ships with it) | 0.5 per surface | None |
| F-12 Diff me | v1.4 | 6 | Version snapshots everywhere |
| **TOTAL** | | **~36 dev-days** | |

## 8.4 — What "best in the world" actually means

After Pass 1-8, Aegis differentiates from Stripe / Linear / Salesforce in three ways:

1. **Domain depth.** Stripe doesn't know what an accumulation base is; Salesforce doesn't know what a UIN is. Aegis was built native to Indian PHI.
2. **Engineering rigour.** Maker-checker, hash-chained audit log, rate-table snapshots, golden tests on the engine, idempotency keys. Real banks ship less rigour than this.
3. **Invisible help.** F-1 through F-12 are quiet, defensible, undo-able. They make the operator faster without taking the wheel.

That is what we mean when we say "best in the world for the audience it serves."

---

# Eight passes, complete

Pass 1 surfaces. Pass 2 behaviour. Pass 3 domain. Pass 4 aesthetics. Pass 5 scale. Pass 6 build order. Pass 7 adoption. Pass 8 embedded intelligence. Every decision has a journey to justify it, an SLO to measure it, an incident playbook to recover it, a cohort to roll it out to, and a quiet AI assist to make it faster.

Ship-1 in the morning. F-1, F-5, F-6, F-11 ride along; everything else lands in measured steps.

---

# Pass 9 — Failure modes & resilience playbook (enhancement round 4)

> **What I'm fixing from Pass 8.** Passes 1-8 describe the *happy path*. Pass 9 describes what happens when things break — because everything breaks eventually, and the dashboard's reputation is set by how it handles the bad days. "Best in the world" includes "best when degraded."

## 9.1 — Failure-mode taxonomy

Six categories. Every Aegis surface must explicitly handle each one or document why it doesn't apply.

| # | Category | Examples | Aegis must |
|---|---|---|---|
| F-N | **Network failure** | Server down, slow connection, DNS hiccup | Surface degraded state; never appear "frozen" |
| F-D | **Data conflict** | Two reviewers editing the same draft; stale local copy | Detect; warn; merge or block; never lose work |
| F-V | **Validation failure** | Required field missing; rule violated | Block the action; explain why; cursor to the offending field |
| F-A | **Authorisation failure** | User lacks role; session expired; revoked token | Re-authenticate gracefully; preserve unsaved draft |
| F-E | **External-service failure** | KMS unreachable; SMS gateway down; payment GW timeout | Circuit-breaker; explicit message; fallback path where safe |
| F-C | **Catastrophic failure** | DB down; corrupt audit chain; engine error | Fail safe; never silently succeed; alert ops + page on-call |

## 9.2 — Per-surface failure-mode matrix

For every surface, what does each category look like and how does Aegis respond?

### Home Dashboard
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-N: API timeout | KPI tile shows "—" | Show last-known-good with timestamp; "Retrying…" indicator; auto-retry exponential backoff |
| F-N: Partial degrade | One KPI fails, others ok | Tile shows error chip; tooltip "Could not fetch (4xx); details" link |
| F-A: Token expired | Whole shell needs re-auth | Modal "Your session expired; sign in to continue"; draft work preserved |
| F-E: anomaly source down | Anomaly badges absent | Silently degrade; tooltip explains feature toggled off |

### Quote Explorer
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-N: Slow list | Hang | Skeleton rows + Cancel button + "Showing cached results (last 5 min)" |
| F-V: Filter syntax error | User types weird filter | Inline red border; helper text "Filter must match: planId=..., age:30-40, si:>5L" |
| F-D: Quote was deleted by another user | 404 on drill-down | Drawer shows "This quote no longer exists. Refresh list?" |

### Plan Configurator (Edit Mode)
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-D: Two makers editing same plan | Concurrent save | Pessimistic lock acquired by first opener; second sees "Locked by Vidya (since 11:03 am). Force-take edit?" requires reason + audit_event |
| F-D: Stale local copy | Server has v3, client has v2 | Save blocked; diff drawer opens showing what changed; user merges manually |
| F-V: Rule violation on save | Highlight tab; block save | Show inline callout + scroll to violation; Save button disabled with tooltip |
| F-A: Maker tries to self-approve | UI shouldn't show button | Defence-in-depth: server returns 403 with `IDENTICAL_ACTOR_REJECTED`; toast "Approval requires a different user" |
| F-E: audit_event service down | Cannot record change | **Block the save** + show banner "Audit service degraded — your change is queued, not yet committed"; retry behind the scenes; alert ops |
| F-C: Engine returns error on live preview | Right-rail shows red | Preserve the user's draft input; show "Live preview unavailable; your draft is saved locally and on the server. The engine team has been alerted." |

### Rate Table Manager
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-D: Two actuaries edit same cell | Last-write-wins is unacceptable for money | Cell-level pessimistic lock; second editor sees "Cell locked"; force-unlock requires reason + audit_event |
| F-N: Reference-pack impact preview times out | Right-rail empty | Cancel + retry + manual run button; never auto-publish with stale preview |
| F-V: Negative rate / non-numeric input | Cell shows red | Block save; revert on Esc |
| F-C: Engine disagrees with prior version's stamped rateTableVersion | Severe | Refuse to publish; alert ops; force-rollback to prior |

### Excel Import Studio
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-V: Excel file malformed | Parse fails | Show parsed-vs-failed summary; "Download error report" CSV; do NOT touch existing data |
| F-V: Dry-run shows changes that violate plan rules | Show in red | Block commit; require user to acknowledge each violation row |
| F-E: Storage failed during snapshot creation | Cannot ensure rollback | Block commit; alert ops; explain "Pre-import snapshot failed; not safe to proceed" |
| F-C: Mid-commit failure (partial write) | Half the table updated | Automatic rollback from snapshot; surface incident banner; audit_event with full context |

### Excel Import Studio (continued)
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-D: User commits while another import is in flight | Conflict | Block second commit; "An import is already running by Manish (started 11:05). Wait or take over (force-stop)?" |

### Discount Manager / Business Rules Editor
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-V: Rule creates an infinite mutual-exclusion loop | Static check | Block save; show the cycle visually |
| F-D: Two editors saved conflicting rule | Conflict | Diff drawer; manual merge |
| F-C: Rule engine compilation fails | Cannot apply | Block publish; preserve draft; ops alerted |

### UW Queue
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-A: Underwriter without role tries to approve | 403 | Toast "Requires UW role"; route to access-request flow |
| F-V: Decision missing rationale | Block submit | Inline error |
| F-E: Risk-score service down | Score shows "?" | Allow manual entry with banner "Auto-score unavailable; using manual input" |

### Reports & Analytics
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-N: Warehouse query timeout | Report empty | Show "Query exceeded 30s; reduce date range or refine filter" + Last-known-good snapshot link |
| F-V: Report parameters out of range | Block | Inline error |
| F-E: Warehouse unreachable | All reports degraded | Banner "Reports unavailable; ETA on Status Page"; degrade gracefully |

### Audit & Governance
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-C: Hash-chain integrity broken | Catastrophic | RED banner on every Aegis screen ("Audit integrity check failed at row #N — investigate immediately"); auto-page sec + CTO; block all maker-checker actions until resolved |
| F-N: Audit query slow | Loading spinner | Streaming results with paging |

### Settings
| Failure | Symptom | Aegis behaviour |
|---|---|---|
| F-A: Non-admin tries to add user | UI hides button; server 403 | Toast + access-request CTA |
| F-V: GST rate < 0 or > 0.5 | Block | Inline error with policy reference |
| F-E: Integration credentials invalid | Test-connection fails | Surface error inline; never save invalid creds |

## 9.3 — Cross-surface resilience patterns

These are the recurring patterns; encode them once in `:shared-ui` so every surface inherits them.

### Pattern A — "Optimistic UI + reconciliation"
For non-critical, fast operations (toggle a status, archive a draft), show the new state immediately while the server call is in flight. If it fails, revert + toast. **Forbidden** for money fields, rate cells, rule changes, or anything subject to maker-checker.

### Pattern B — "Pessimistic lock with force-take"
For maker-checker editable artefacts (Plan / Rate Table / Discount / Rule), acquire a server-side lock on Edit-mode entry. Other openers see a banner; force-take requires a typed reason + audit_event row.

### Pattern C — "Stale-data warning"
Every list view records the timestamp it was fetched. If the user navigates back > 5 minutes later, banner: "Data is 5 min old. Refresh now?"

### Pattern D — "Circuit breaker with fallback message"
Every external integration (KMS, SMS, payment, KYC, hospital catalogue) is wrapped in a circuit breaker. Open circuit ⇒ explicit user-facing message naming the impaired feature, NEVER a silent failure.

### Pattern E — "Audit-on-block"
When a save is blocked due to rule violation OR external-service failure, write an audit_event row recording the *attempted* state. Postmortems can replay what users tried to do.

### Pattern F — "Severable disable"
A surface should never go entirely dark because one dependency is degraded. Disable only the affected actions; keep the rest navigable. (E.g., if the engine is down, Quote Explorer still lists prior quotes — just can't compute new ones.)

### Pattern G — "Time-bounded retry"
All retries are exponential with jitter, max 3 attempts, max 30s total. Never auto-retry mutations (POST/PUT/DELETE); always require explicit user action after first failure.

### Pattern H — "Preserve work always"
Aegis must never lose user input. Drafts auto-save every 10s + on field-blur. Even on session expiry, drafts persist via the server's session-scoped Draft table.

### Pattern I — "Surface latency"
Each tile / card / drawer shows the time-to-result in dev mode (telemetry overlay). Surfaces over budget get a "🐢 slow" badge for the engineer; users never see this.

### Pattern J — "Status Page integration"
The Aegis shell pings `/status` every 30s. If a vendor or sub-system is degraded, a header banner appears: "SMS delivery is degraded — OTP-required actions may take longer. ETA: 12 min."

## 9.4 — Chaos drills (semi-annual)

Aegis must survive each of these exercises in staging before going GA:

1. **DB primary failure** — read replica promoted; Aegis surfaces show "read-only mode" banner; edits block; recovery < 5 min.
2. **Engine 5xx** — every Aegis surface that calls the engine degrades to "compute unavailable" banner; the Audit browser, Cover Catalog, and Plan Configurator (read mode) still work.
3. **audit_event service down** — every mutation is blocked with the queued-for-audit banner; Aegis does not silently bypass.
4. **KMS unreachable** — encrypted reads degrade to a masked view; encrypted writes block.
5. **Two users force-take the same lock simultaneously** — deterministic winner; loser sees a clear "Your edit was cancelled by …" banner with a CTA to re-open.
6. **5x traffic spike** — auto-scaling triggers; p95 latency stays within SLO; no surface goes blank.
7. **Audit-chain tamper test** — sec team mutates a row directly in DB; the verifyChain endpoint flags the break within 60s; the red banner appears on Aegis within the next page load.

Each drill is sign-off gated by the SRE lead + CTO.

## 9.5 — Banners hierarchy (visual)

Aegis has 4 banner severities. Strict hierarchy so users learn the system at a glance.

| Severity | Background | Icon | Text | Examples |
|---|---|---|---|---|
| Info | indigo50 | ℹ | indigo700 | "Data is 5 min old"; "Feature flag X enabled" |
| Success | success50 | ✓ | success700 | "Saved"; "Rate table v3.3 is live" |
| Warn | warn50 | ⚠ | warn700 | "SMS gateway degraded — OTP delivery slower"; "Stale draft" |
| Danger | danger50 | ⛔ | danger700 | "Audit chain integrity broken"; "Save failed; your draft is preserved" |

Banners stack at the top of the surface, newest first, max 3 visible. Click a banner to dismiss (info/success) or open the corresponding action (warn/danger).

## 9.6 — How Aegis tells users about itself

Behind a `dev` flag, the Aegis Shell exposes a "What's running" inspector (⌘⇧I) showing:

- Connected services (server / engine / KMS / SMS / payment GW / KYC / etc.)
- Each service's last successful call timestamp
- Circuit breaker state for each
- Recent telemetry events (last 50)
- Current user's session expiry
- Build version + commit SHA + rate-table version

For non-dev users this surface is hidden. But internally it is the on-call's first stop during an incident.

## 9.7 — Definition of resilient (what "good degradation" looks like)

Aegis is **resilient** when:

- A user can identify a degraded state within 5 seconds of opening any surface.
- No save action ever appears to succeed when it actually failed.
- No money figure ever appears stale without a timestamp + age indicator.
- No surface goes entirely blank because one sub-system is down.
- Every "blocked" action has a clearly-worded reason and a next step.
- Every catastrophic failure auto-pages on-call and surfaces a banner.
- Every action that *could* trigger a conflict shows that risk before the user commits.
- Recovery from any single-vendor outage requires at most 1 click from the user.

## 9.8 — The hardest screens to get right

These are the 5 surfaces where failure modes are most costly. They get extra design + testing investment.

1. **Plan Configurator Edit Mode** (Ship-6) — concurrent edits, maker-checker, audit, validation. The hub of business risk.
2. **Rate Table Manager** (Ship-7) — money cells. Must never appear to save when it didn't.
3. **Excel Import Studio** (Ship-8) — the rollback path is the single most tested code in Aegis.
4. **Audit & Governance** (Ship-12) — the chain integrity dashboard is where sec lives during an incident.
5. **Settings → Integrations** (Ship-13) — wrong API key here breaks every downstream service.

These five get an explicit failure-mode review by a senior engineer + sec lead before they ship to cohort 1.

---

# Nine passes, complete

Pass 1 surfaces. Pass 2 behaviour. Pass 3 domain. Pass 4 aesthetics. Pass 5 scale. Pass 6 build order. Pass 7 adoption. Pass 8 intelligence. Pass 9 failure modes.

The plan is a complete spec of an internal product that has to keep a regulated insurance business running. Ship-1 in the morning. Every failure mode catalogued. Every banner accounted for. Every drill scheduled.

