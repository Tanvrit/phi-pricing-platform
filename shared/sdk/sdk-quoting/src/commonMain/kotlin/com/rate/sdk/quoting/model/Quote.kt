package com.rate.sdk.quoting.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import com.rate.core.money.toMoney
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle status of a saved quote. A quote is a *transactional* record (not admin-CRUD
 * config), so it implements [BaseDataClass] rather than ConfigEntity — there is no
 * draft/publish, only its journey from a priced calculation to a converted proposal.
 */
@Serializable
enum class QuoteStatus {
    /** Priced and saved; still editable / re-quotable. */
    DRAFT,

    /** Shared with the customer (a SharedQuoteView link / email). */
    SHARED,

    /** Converted into a proposal (sdk-proposal picked it up). */
    CONVERTED,

    /** Past its validity window without conversion. */
    EXPIRED,
}

/**
 * A saved quote — the transactional envelope that wraps the rating inputs ([request]) and
 * the engine's priced output ([result]) together with quoting metadata. This is what gets
 * persisted via the core `QuoteRepository`, listed on dashboards, shared with customers and
 * later converted to a proposal.
 *
 * Sealed over [ProductLine]: [RetailQuote] for individual/family-floater PHI quotes and
 * [GroupQuote] for Employer-Employee / GHI quotes (which additionally carry the resolved
 * group benefit schedule + total lives). Both share the [BaseDataClass] envelope so generic
 * persistence and listing treat them uniformly.
 *
 * The monetary headline ([totalIncludingGstMoney]) is derived from the `QuoteResult`'s
 * Double — conversion to [Money] happens only here, at assembly/display, never inside the
 * engine (which accumulates in Double for Excel parity).
 */
@Serializable
sealed class Quote : BaseDataClass {
    abstract val productLine: ProductLine

    /** The rating inputs that produced [result]. */
    abstract val request: QuoteRequest

    /** The engine's priced output (Double throughout, per the rating contract). */
    abstract val result: QuoteResult

    abstract val status: QuoteStatus

    /** Optional owning party (proposer / employer) once identity is captured. */
    abstract val partyRef: String?

    /** Optional human-readable quote number for sharing / CIS. */
    abstract val quoteNumber: String?

    /** Engine-version + rate-table snapshot the price was computed against (for reproducibility). */
    val engineVersion: String get() = result.engineVersion
    val rateTableVersion: String get() = result.rateTableVersion

    /** Money headline — derived once from the engine's Double total-including-GST. */
    val totalIncludingGstMoney: Money get() = result.totalIncludingGst.toMoney()

    /** Whether the engine flagged the request as ratable. */
    val isValid: Boolean get() = result.isValid
}

/**
 * RETAIL (individual / family-floater) quote.
 */
@Serializable
@SerialName("RetailQuote")
data class RetailQuote(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("request") override val request: QuoteRequest,
    @SerialName("result") override val result: QuoteResult,
    @SerialName("status") override val status: QuoteStatus = QuoteStatus.DRAFT,
    @SerialName("partyRef") override val partyRef: String? = null,
    @SerialName("quoteNumber") override val quoteNumber: String? = null,
    /** Customer-facing display name captured at quote time (denormalised for sharing). */
    @SerialName("customerName") val customerName: String? = null,
    /** Validity window — after this the quote is treated as [QuoteStatus.EXPIRED]. */
    @SerialName("validUntil") val validUntil: Instant? = null,
    // ── BaseDataClass envelope ─────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : Quote() {
    override val productLine: ProductLine get() = ProductLine.RETAIL
}

/**
 * GROUP (Employer-Employee / GHI) quote. Carries the group-specific roll-up — total lives
 * rated and the resolved benefit-schedule ids per grade — alongside the shared envelope.
 */
@Serializable
@SerialName("GroupQuote")
data class GroupQuote(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("request") override val request: QuoteRequest,
    @SerialName("result") override val result: QuoteResult,
    @SerialName("status") override val status: QuoteStatus = QuoteStatus.DRAFT,
    @SerialName("partyRef") override val partyRef: String? = null,
    @SerialName("quoteNumber") override val quoteNumber: String? = null,
    /** Owning group product config id (sdk-catalog GroupProductConfig). */
    @SerialName("groupProductRef") val groupProductRef: String? = null,
    /** Employer party (sdk-party GroupEmployer) this quote belongs to. */
    @SerialName("employerName") val employerName: String? = null,
    /** Total lives rated across all grades (drives group size discount). */
    @SerialName("totalLives") val totalLives: Int = 0,
    /** Resolved benefit-schedule ids per grade (sdk-catalog BenefitSchedule). */
    @SerialName("benefitScheduleRefs") val benefitScheduleRefs: List<String> = emptyList(),
    @SerialName("validUntil") val validUntil: Instant? = null,
    // ── BaseDataClass envelope ─────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : Quote() {
    override val productLine: ProductLine get() = ProductLine.GROUP
}
