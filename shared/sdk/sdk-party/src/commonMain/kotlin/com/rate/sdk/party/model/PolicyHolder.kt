package com.rate.sdk.party.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sex / gender as carried on the proposer & members. */
@Serializable
enum class Sex(val code: String) {
    MALE("M"), FEMALE("F"), OTHER("O");

    companion object {
        fun fromCode(code: String): Sex =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OTHER
    }
}

/** Reusable Indian postal address (proposer, employer, communication). */
@Serializable
data class Address(
    @SerialName("line1") val line1: String = "",
    @SerialName("line2") val line2: String = "",
    @SerialName("city") val city: String = "",
    @SerialName("state") val state: String = "",
    @SerialName("pincode") val pincode: String = "",
    @SerialName("country") val country: String = "India",
)

/** Settlement bank account (proposer-level; reused by claims & refunds). */
@Serializable
data class BankAccount(
    @SerialName("accountHolderName") val accountHolderName: String = "",
    @SerialName("accountNumber") val accountNumber: String = "",
    @SerialName("ifsc") val ifsc: String = "",
    @SerialName("bankName") val bankName: String = "",
    @SerialName("branch") val branch: String = "",
)

/** KYC method + collected identifiers for a party (CKYC / EKYC / manual upload). */
@Serializable
enum class KycMethod { C_KYC, E_KYC, MANUAL }

@Serializable
enum class KycStatus { PENDING, VERIFIED, REJECTED }

@Serializable
data class KycInfo(
    @SerialName("method") val method: KycMethod = KycMethod.C_KYC,
    @SerialName("status") val status: KycStatus = KycStatus.PENDING,
    @SerialName("pan") val pan: String? = null,
    @SerialName("aadhaarLast4") val aadhaarLast4: String? = null,
    @SerialName("ckycNumber") val ckycNumber: String? = null,
    @SerialName("verifiedAt") val verifiedAt: Instant? = null,
)

/**
 * A policy-holding party. Sealed across the two lines of business: a [RetailProposer]
 * (an individual buying a retail floater/individual policy) and a [GroupEmployer]
 * (the corporate sponsor of a group policy whose lives come from a [com.rate.sdk.party.group.Census]).
 *
 * Both are admin-/operator-CRUD `ConfigEntity`s (operators create & edit them from the
 * console, customers create [RetailProposer]s through the buy-online journey) so they
 * share the standard envelope and persist through [com.rate.sdk.party.repository.PartyRepository].
 *
 * Polymorphic by `_class` discriminator (see `AppJson.classDiscriminator`).
 */
@Serializable
sealed class PolicyHolder : ConfigEntity {
    abstract val productLine: ProductLine
    /** Primary contact mobile, indexed for `getByMobile`. */
    abstract val mobile: String
    abstract val email: String?
    /** Communication / KYC address. */
    abstract val address: Address
}

/** Retail individual proposer — the buy-online customer / retail console subject. */
@Serializable
@SerialName("RetailProposer")
data class RetailProposer(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("mobile") override val mobile: String,
    @SerialName("email") override val email: String? = null,
    @SerialName("pan") val pan: String? = null,
    @SerialName("dob") val dob: LocalDate? = null,
    @SerialName("gender") val gender: Sex = Sex.MALE,
    @SerialName("address") override val address: Address = Address(),
    @SerialName("bankAccount") val bankAccount: BankAccount? = null,
    @SerialName("kyc") val kyc: KycInfo = KycInfo(),
    /** Optional reference to the floater members ([PartyMember]) belonging to this proposer. */
    @SerialName("memberRefs") val memberRefs: List<String> = emptyList(),
    @SerialName("productLine") override val productLine: ProductLine = ProductLine.RETAIL,
    // ── ConfigEntity envelope ────────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : PolicyHolder()

/** Group corporate sponsor — owns a census of insured lives. */
@Serializable
@SerialName("GroupEmployer")
data class GroupEmployer(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("companyName") val companyName: String,
    @SerialName("gstin") val gstin: String? = null,
    @SerialName("pan") val pan: String? = null,
    /** NIC-style industry code; drives `GroupRateDataProvider.industryLoading`. */
    @SerialName("industryCode") val industryCode: String = "",
    @SerialName("mobile") override val mobile: String,
    @SerialName("email") override val email: String? = null,
    @SerialName("contactPerson") val contactPerson: String = "",
    @SerialName("address") override val address: Address = Address(),
    @SerialName("registeredOfficeAddress") val registeredOfficeAddress: Address = Address(),
    @SerialName("estimatedLives") val estimatedLives: Int = 0,
    @SerialName("productLine") override val productLine: ProductLine = ProductLine.GROUP,
    // ── ConfigEntity envelope ────────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : PolicyHolder()
