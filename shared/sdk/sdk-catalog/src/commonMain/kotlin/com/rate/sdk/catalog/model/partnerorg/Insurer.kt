package com.rate.sdk.catalog.model.partnerorg

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Line of business an [Insurer] is licensed/registered for under IRDAI. */
@Serializable
enum class InsurerType { LIFE, GENERAL, HEALTH, REINSURER }

/**
 * A risk carrier (insurer / reinsurer) the group-insurance programme places business with.
 *
 * Masters the IRDAI registration identity and a primary contact so quotes, policies and claims
 * can be attributed to the underwriting carrier. Carriers are shared across product lines
 * (RETAIL + GROUP), so this is a GLOBAL config master, not productLine-scoped.
 */
@Serializable
data class Insurer(
    @SerialName("_id") override val id: String = newId(),
    /** Legal / trading name of the carrier (display + uniqueness key). */
    @SerialName("name") val name: String,
    /** IRDAI registration number stamped on the certificate of registration. */
    @SerialName("irdaiRegNo") val irdaiRegNo: String = "",
    /** Line of business this carrier underwrites. */
    @SerialName("type") val type: InsurerType = InsurerType.HEALTH,
    /** Primary relationship contact at the carrier. */
    @SerialName("contactPerson") val contactPerson: String = "",
    /** Contact email for placements / endorsements. */
    @SerialName("email") val email: String = "",
    /** Contact phone (E.164 or 10-digit Indian mobile). */
    @SerialName("phone") val phone: String = "",
    /** Free-text address / branch the relationship is anchored to. */
    @SerialName("address") val address: String = "",
    /** Carrier financial-strength / claims-paying rating note (e.g. "CRISIL AAA"). */
    @SerialName("rating") val rating: String = "",
    /** Whether new business can currently be placed with this carrier. */
    @SerialName("active") val active: Boolean = true,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity {
    companion object {
        fun defaults(): List<Insurer> = listOf(
            Insurer(
                name = "HDFC ERGO General Insurance",
                irdaiRegNo = "146",
                type = InsurerType.GENERAL,
                contactPerson = "Group Health Desk",
                email = "grouphealth@hdfcergo.com",
                phone = "1800-2700-700",
                address = "1st Floor, HDFC House, Churchgate, Mumbai 400020",
                rating = "ICRA iAAA",
            ),
            Insurer(
                name = "Star Health and Allied Insurance",
                irdaiRegNo = "129",
                type = InsurerType.HEALTH,
                contactPerson = "Corporate Solutions",
                email = "corp.support@starhealth.in",
                phone = "044-6900-6900",
                address = "Balaji Complex, No.1 New Tank Street, Valluvar Kottam, Chennai 600034",
                rating = "AA- (CARE)",
            ),
            Insurer(
                name = "ICICI Lombard General Insurance",
                irdaiRegNo = "115",
                type = InsurerType.GENERAL,
                contactPerson = "Employee Benefits Team",
                email = "ebservice@icicilombard.com",
                phone = "1800-2666",
                address = "ICICI Lombard House, 414 Veer Savarkar Marg, Prabhadevi, Mumbai 400025",
                rating = "AAA (CRISIL)",
            ),
            Insurer(
                name = "Niva Bupa Health Insurance",
                irdaiRegNo = "145",
                type = InsurerType.HEALTH,
                contactPerson = "Group Servicing",
                email = "groupsupport@nivabupa.com",
                phone = "1860-500-8888",
                address = "C-98, First Floor, Lajpat Nagar, New Delhi 110024",
                rating = "AAA (CARE)",
            ),
            Insurer(
                name = "GIC Re (General Insurance Corporation of India)",
                irdaiRegNo = "112",
                type = InsurerType.REINSURER,
                contactPerson = "Treaty Reinsurance Desk",
                email = "info@gicofindia.com",
                phone = "022-2286-7000",
                address = "Suraksha, 170 J Tata Road, Churchgate, Mumbai 400020",
                rating = "AAA (CARE) / A- (AM Best)",
            ),
            Insurer(
                name = "Max Life Insurance",
                irdaiRegNo = "104",
                type = InsurerType.LIFE,
                contactPerson = "Group Term Life Desk",
                email = "group.support@maxlifeinsurance.com",
                phone = "1860-120-5577",
                address = "Max House, 3rd Floor, Okhla Industrial Area Phase III, New Delhi 110020",
                rating = "AAA (CRISIL)",
            ),
        )
    }
}
