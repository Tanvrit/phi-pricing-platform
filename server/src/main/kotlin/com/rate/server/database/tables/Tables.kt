package com.rate.server.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PlansTable : Table("plans") {
    val id                   = varchar("id", 100)
    val name                 = varchar("name", 200)
    val planType             = varchar("plan_type", 50)
    val underwritingCategory = varchar("underwriting_category", 20).default("STANDARD")
    val geographyScope       = varchar("geography_scope", 50).default("DOMESTIC")
    val coPaymentTable       = varchar("copayment_table", 20).default("OMNIBUS")
    val description          = text("description").default("")
    val availableSumInsureds = text("available_sum_insureds")
    val availableZones       = text("available_zones")
    val availableFamilyTypes = text("available_family_types")
    val maxDiscountCap       = decimal("max_discount_cap", 5, 4).default(java.math.BigDecimal("0.30"))
    val rateTableId          = varchar("rate_table_id", 100).default("")
    val minAge               = integer("min_age").default(5)
    val maxAge               = integer("max_age").default(99)
    val isActive             = bool("is_active").default(true)
    val lifecycle            = varchar("lifecycle", 20).default("LIVE")
    override val primaryKey  = PrimaryKey(id)
}

object BaseRatesTable : Table("base_rates") {
    val id            = long("id").autoIncrement()
    val planId        = varchar("plan_id", 100)
    val familyType    = varchar("family_type", 20)
    val zone          = varchar("zone", 30)
    val ageBandMin    = integer("age_band_min")
    val sumInsured    = long("sum_insured")
    val annualPremium = decimal("annual_premium", 14, 2)
    override val primaryKey = PrimaryKey(id)
    init { index(false, planId, familyType, zone, ageBandMin, sumInsured) }
}

object CoverRateLookupTable : Table("cover_rate_lookup") {
    val id        = long("id").autoIncrement()
    val coverId   = varchar("cover_id", 100)
    val param1Key = varchar("param1_key", 200).nullable()
    val param2Key = varchar("param2_key", 200).nullable()
    val ageBandMin = integer("age_band_min").nullable()
    val sumInsured = long("sum_insured").nullable()
    val planId    = varchar("plan_id", 100).nullable()
    val rate      = decimal("rate", 16, 8)
    override val primaryKey = PrimaryKey(id)
    init { index(false, coverId, param1Key, param2Key, ageBandMin, sumInsured, planId) }
}

object MemberLevelRatesTable : Table("member_level_rates") {
    val id        = long("id").autoIncrement()
    val coverId   = varchar("cover_id", 100)
    val ageBandMin = integer("age_band_min").nullable()
    val param1Key = varchar("param1_key", 200).nullable()
    val rate      = decimal("rate", 16, 8)
    override val primaryKey = PrimaryKey(id)
    init { index(false, coverId, ageBandMin, param1Key) }
}

object InstalmentConfigTable : Table("instalment_config") {
    val id             = long("id").autoIncrement()
    val policyTenure   = varchar("policy_tenure", 20)
    val paymentTenure  = varchar("payment_tenure", 20)
    val paymentMode    = varchar("payment_mode", 30)
    val instalmentCount = integer("instalment_count")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(policyTenure, paymentTenure, paymentMode) }
}

object DiscountRatesTable : Table("discount_rates") {
    val id       = varchar("id", 100)
    val name     = varchar("name", 200)
    val paramKey = varchar("param_key", 100).nullable()
    val rate     = decimal("rate", 10, 6)
    override val primaryKey = PrimaryKey(id)
}

object CoverAvailabilityTable : Table("cover_availability") {
    val planId  = varchar("plan_id", 100)
    val coverId = varchar("cover_id", 100)
    override val primaryKey = PrimaryKey(planId, coverId)
}

object QuotesTable : Table("quotes") {
    val id               = varchar("id", 50)
    val createdAt        = timestamp("created_at")
    val planId           = varchar("plan_id", 100)
    val primaryAge       = integer("primary_age")
    val sumInsured       = long("sum_insured")
    val familyType       = varchar("family_type", 20)
    val zone             = varchar("zone", 30)
    val tenure           = varchar("tenure", 20)
    val paymentMode      = varchar("payment_mode", 30)
    val requestJson      = text("request_json")
    val resultJson       = text("result_json")
    val basePremium      = decimal("base_premium", 14, 2)
    val finalPremium     = decimal("final_premium", 14, 2)
    val instalmentPremium = decimal("instalment_premium", 14, 2)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Append-only audit ledger. Every state-changing route writes here, and a hash-chain
 * links each row to its predecessor so tampering with any historical row breaks the
 * chain at that point. Verified via `AuditEventService.verifyChain()`.
 */
object AuditEventTable : Table("audit_event") {
    val id            = long("id").autoIncrement()
    val eventAt       = timestamp("event_at")
    val actorSubject  = varchar("actor_subject", 200).nullable()
    val actorRole     = varchar("actor_role", 50).nullable()
    val action        = varchar("action", 100)
    val resourceType  = varchar("resource_type", 100)
    val resourceId    = varchar("resource_id", 200).nullable()
    val payloadJson   = text("payload_json").nullable()
    val requestId     = varchar("request_id", 100).nullable()
    val prevHash      = varchar("prev_hash", 64).nullable()
    val thisHash      = varchar("this_hash", 64)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Save+resume snapshot for the customer buyonline journey. Keyed by an opaque
 * hex session id the client puts in `?session=` URLs; rows are upserted on every
 * meaningful state change (navigate, tier/SI/tenure/addOns).
 */
object BuyOnlineSessionTable : Table("buyonline_session") {
    val sessionId = varchar("session_id", 64)
    val stateJson = text("state_json")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(sessionId)
}

/**
 * 24h replay cache for POST routes. Client sends `Idempotency-Key`; if the same key
 * arrives again, the cached response is returned verbatim. `request_hash` lets us
 * detect a key being reused for a *different* request body and reject it.
 */
object IdempotencyKeyTable : Table("idempotency_key") {
    val key            = varchar("key", 100)
    val route          = varchar("route", 200)
    val requestHash    = varchar("request_hash", 64)
    val responseStatus = integer("response_status")
    val responseBody   = text("response_body").nullable()
    val createdAt      = timestamp("created_at")
    override val primaryKey = PrimaryKey(key)
}
