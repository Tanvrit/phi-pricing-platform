package com.rate.persistence.base

/**
 * Single source of truth for MongoDB collection names. Centralised so index bootstrap,
 * repositories and the rate-table cache never drift on a string literal.
 *
 * Naming convention: lowerCamel singular-ish nouns matching the entity (e.g. `plans`,
 * `covers`). Rate rows are split one collection per kind (the engine indexes each on its
 * own lookup tuple). The audit log + idempotency + sequence counters are operational
 * collections.
 */
object CollectionNames {
    // ── Rating anchor + saved quotes ─────────────────────────────────────────
    const val PLANS = "plans"
    const val QUOTES = "quotes"

    // ── RETAIL catalog (admin-CRUD ConfigEntity) ─────────────────────────────
    const val PRODUCTS = "products"
    const val SECTIONS = "sections"
    const val COVERS = "covers"
    const val CRITICAL_ILLNESS_LISTS = "criticalIllnessLists"
    const val ANNEXURES = "annexures"
    const val ADD_ONS = "addOns"
    const val TENURES = "tenures"
    const val PINCODE_ZONES = "pincodeZones"

    // ── GROUP catalog (admin-CRUD ConfigEntity) ──────────────────────────────
    const val GROUP_PRODUCT_CONFIGS = "groupProductConfigs"
    const val GROUP_GRADES = "groupGrades"
    const val BENEFIT_SCHEDULES = "benefitSchedules"
    const val WAITING_PERIODS = "waitingPeriods"
    const val ELIGIBILITY_CRITERIA = "eligibilityCriteria"
    const val PPD_PTD_TABLES = "ppdPtdTables"
    const val DAY_CARE_PROCEDURES = "dayCareProcedures"
    const val CONSUMABLES_LISTS = "consumablesLists"
    const val HEALTH_CHECKUP_PACKAGES = "healthCheckupPackages"
    const val CHRONIC_OPD_GRIDS = "chronicOpdGrids"
    // Structured entities split out of the GROUP EE annexure (surgical sublimit table,
    // adult vaccination list, medical-device list) — see AnnexureCatalogRepositories PORTs.
    const val SURGICAL_SUBLIMITS = "surgicalSublimits"
    const val VACCINATION_CATALOGS = "vaccinationCatalogs"
    const val MEDICAL_DEVICE_CATALOGS = "medicalDeviceCatalogs"

    // ── GLOBAL catalog (no productLine; shared RETAIL/GROUP) ──────────────────
    const val VENDORS = "vendors"

    // ── Party (transactional + admin-CRUD) ───────────────────────────────────
    const val PARTIES = "parties"
    const val PARTY_MEMBERS = "partyMembers"
    const val CENSUSES = "censuses"

    // ── Proposal / buy-online ────────────────────────────────────────────────
    const val PROPOSALS = "proposals"
    const val SESSIONS = "buyOnlineSessions"

    // ── Policy servicing ─────────────────────────────────────────────────────
    const val POLICIES = "policies"
    const val CLAIMS = "claims"

    // ── Immutable rate rows (one collection per kind) ────────────────────────
    const val BASE_RATES = "baseRates"
    const val COVER_RATES = "coverRates"
    const val MEMBER_LEVEL_RATES = "memberLevelRates"
    const val DISCOUNT_RATES = "discountRates"
    const val INSTALMENT_CONFIG = "instalmentConfig"
    const val COVER_AVAILABILITY = "coverAvailability"
    const val RATE_META = "rateMeta"

    // ── Identity / access (console users + login security log) ────────────────
    const val USERS = "users"
    const val LOGIN_AUDITS = "loginAudits"

    // ── Operational ──────────────────────────────────────────────────────────
    const val AUDIT_EVENTS = "auditEvents"
    const val IDEMPOTENCY = "idempotency"
    const val SEQ_COUNTERS = "seqCounters"
    const val OTP = "otp"
}
