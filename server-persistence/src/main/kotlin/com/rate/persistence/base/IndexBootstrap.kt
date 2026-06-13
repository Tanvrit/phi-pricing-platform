package com.rate.persistence.base

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.util.concurrent.TimeUnit

/**
 * Idempotent index creation. `createIndex` is a no-op when an equivalent index already
 * exists, so this is safe to run on every boot.
 *
 * Covers:
 *  - the engine's rate-row lookup tuples (compound, one per rate kind) so a per-call rate
 *    resolution is index-served when the cache is bypassed;
 *  - unique business keys (idempotency `_id` is unique by construction; audit `seq`;
 *    proposal `proposalNumber`);
 *  - TTL indexes for ephemeral collections (otp, buy-online sessions, idempotency) so the
 *    server enforces expiry rather than the application;
 *  - servicing lookups (`mobile`, `holderId`, `policyId`, `employerPartyRef`, …);
 *  - `quotes.createdAt: -1` for the recent-quotes dashboard.
 */
object IndexBootstrap {

    /** TTL (seconds) applied to the buy-online session snapshots — 30 days, matching the
     *  monolith's "resume link valid for 30 days". */
    const val SESSION_TTL_SECONDS: Long = 30L * 24 * 60 * 60

    /** TTL (seconds) applied to OTP documents — short-lived. */
    const val OTP_TTL_SECONDS: Long = 15L * 60

    suspend fun ensureIndexes(db: MongoDatabase) {
        // ── Rate-row lookup tuples (compound) ────────────────────────────────
        db.getCollection<Document>(CollectionNames.BASE_RATES).createIndex(
            Indexes.ascending("version", "planId", "familyType", "zone", "ageBandMin", "sumInsured"),
        )
        db.getCollection<Document>(CollectionNames.COVER_RATES).createIndex(
            Indexes.ascending(
                "version", "coverId", "param1", "param2", "ageBandMin", "sumInsured", "planOrTenureKey",
            ),
        )
        db.getCollection<Document>(CollectionNames.MEMBER_LEVEL_RATES).createIndex(
            Indexes.ascending("version", "coverId", "ageBandMin", "param1", "sumInsured"),
        )
        db.getCollection<Document>(CollectionNames.DISCOUNT_RATES).createIndex(
            Indexes.ascending("version", "discountId", "paramKey"),
        )
        db.getCollection<Document>(CollectionNames.INSTALMENT_CONFIG).createIndex(
            Indexes.ascending("version", "policyTenure", "paymentTenure", "paymentMode"),
        )
        db.getCollection<Document>(CollectionNames.COVER_AVAILABILITY).createIndex(
            Indexes.ascending("version", "planId", "coverId"),
        )
        // RateMeta: one active version per product line + version history.
        db.getCollection<Document>(CollectionNames.RATE_META).createIndex(
            Indexes.ascending("productLine", "active"),
        )
        db.getCollection<Document>(CollectionNames.RATE_META).createIndex(
            Indexes.ascending("productLine", "version"),
        )
        db.getCollection<Document>(CollectionNames.RATE_META).createIndex(
            Indexes.ascending("sourceFileSha256"),
        )

        // ── Catalog: covers/benefits looked up by section + product line ──────
        db.getCollection<Document>(CollectionNames.COVERS).createIndex(
            Indexes.ascending("sectionRef", "productLine"),
        )
        db.getCollection<Document>(CollectionNames.COVERS).createIndex(Indexes.ascending("code"))
        db.getCollection<Document>(CollectionNames.BENEFIT_SCHEDULES).createIndex(
            Indexes.ascending("productLine"),
        )
        db.getCollection<Document>(CollectionNames.PINCODE_ZONES).createIndex(
            Indexes.ascending("prefix"),
        )

        // ── Party servicing lookups ──────────────────────────────────────────
        db.getCollection<Document>(CollectionNames.PARTIES).createIndex(Indexes.ascending("mobile"))
        db.getCollection<Document>(CollectionNames.PARTIES).createIndex(Indexes.ascending("pan"))
        db.getCollection<Document>(CollectionNames.PARTY_MEMBERS).createIndex(
            Indexes.ascending("proposerPartyRef"),
        )
        db.getCollection<Document>(CollectionNames.CENSUSES).createIndex(
            Indexes.descending("employerPartyRef", "updatedAt"),
        )

        // ── Proposal: unique number + resume lookups ─────────────────────────
        db.getCollection<Document>(CollectionNames.PROPOSALS).createIndex(
            Indexes.ascending("proposalNumber"),
            IndexOptions().unique(true),
        )
        db.getCollection<Document>(CollectionNames.PROPOSALS).createIndex(Indexes.ascending("mobile"))

        // ── Policy / claim servicing ─────────────────────────────────────────
        db.getCollection<Document>(CollectionNames.POLICIES).createIndex(Indexes.ascending("holderId"))
        db.getCollection<Document>(CollectionNames.POLICIES).createIndex(Indexes.ascending("holderMobile"))
        db.getCollection<Document>(CollectionNames.POLICIES).createIndex(
            Indexes.ascending("status", "expiresAt"),
        )
        db.getCollection<Document>(CollectionNames.CLAIMS).createIndex(Indexes.ascending("policyId"))

        // ── Quotes: recent-first dashboard ───────────────────────────────────
        db.getCollection<Document>(CollectionNames.QUOTES).createIndex(Indexes.descending("createdAt"))

        // ── Audit: unique seq (no chain fork) ────────────────────────────────
        db.getCollection<Document>(CollectionNames.AUDIT_EVENTS).createIndex(
            Indexes.ascending("seq"),
            IndexOptions().unique(true),
        )

        // ── Identity / access ────────────────────────────────────────────────
        // Console users: one row per login email, matched case-insensitively. A case-insensitive
        // collation (strength 2) makes the unique index reject `Owner@…` vs `owner@…` duplicates and
        // serves UserRepositoryImpl.findByEmail's anchored case-insensitive lookup.
        db.getCollection<Document>(CollectionNames.USERS).createIndex(
            Indexes.ascending("email"),
            IndexOptions().unique(true).collation(
                com.mongodb.client.model.Collation.builder()
                    .locale("en")
                    .collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY)
                    .build(),
            ),
        )
        // Login-audit security log: drill-down by email, newest-first.
        db.getCollection<Document>(CollectionNames.LOGIN_AUDITS).createIndex(
            Indexes.descending("email", "at"),
        )
        db.getCollection<Document>(CollectionNames.LOGIN_AUDITS).createIndex(Indexes.descending("at"))

        // ── TTL: idempotency / session / otp ─────────────────────────────────
        // Idempotency expires on its own `expiresAt` BSON date (driver/codec stores Instant
        // fields as BSON Date), expireAfterSeconds(0) = expire AT the stored instant.
        db.getCollection<Document>(CollectionNames.IDEMPOTENCY).createIndex(
            Indexes.ascending("expiresAt"),
            IndexOptions().expireAfter(0L, TimeUnit.SECONDS),
        )
        // Buy-online sessions expire 30 days after last write (the resume window). Uses a
        // dedicated `_ttlAt` BSON-Date field stamped by SessionRepositoryImpl on every save.
        db.getCollection<Document>(CollectionNames.SESSIONS).createIndex(
            Indexes.ascending("_ttlAt"),
            IndexOptions().expireAfter(SESSION_TTL_SECONDS, TimeUnit.SECONDS),
        )
        db.getCollection<Document>(CollectionNames.SESSIONS).createIndex(Indexes.ascending("mobile"))
        db.getCollection<Document>(CollectionNames.OTP).createIndex(
            Indexes.ascending("_ttlAt"),
            IndexOptions().expireAfter(OTP_TTL_SECONDS, TimeUnit.SECONDS),
        )
    }
}
