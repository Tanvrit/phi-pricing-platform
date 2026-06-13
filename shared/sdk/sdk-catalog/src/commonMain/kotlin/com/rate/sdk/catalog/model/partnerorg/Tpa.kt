package com.rate.sdk.catalog.model.partnerorg

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Third-Party Administrator (TPA) that services group health policies — cashless authorisation,
 * claims adjudication and member servicing. Mastered with its IRDAI registration identity and a
 * primary contact so policies can be routed to the servicing TPA.
 */
@Serializable
data class Tpa(
    @SerialName("_id") override val id: String = newId(),
    /** Registered name of the TPA. */
    @SerialName("name") val name: String,
    /** IRDAI TPA registration / licence number. */
    @SerialName("irdaiRegNo") val irdaiRegNo: String = "",
    /** Primary servicing contact. */
    @SerialName("contactPerson") val contactPerson: String = "",
    /** Servicing / escalation email. */
    @SerialName("email") val email: String = "",
    /** 24x7 helpline / contact phone. */
    @SerialName("phone") val phone: String = "",
    /** 24x7 cashless authorisation helpline (toll-free). */
    @SerialName("tollFree") val tollFree: String = "",
    /** Head-office / servicing address. */
    @SerialName("address") val address: String = "",
    /** Whether this TPA is currently empanelled for servicing. */
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
        fun defaults(): List<Tpa> = listOf(
            Tpa(
                name = "Medi Assist Insurance TPA",
                irdaiRegNo = "008",
                contactPerson = "Corporate Servicing",
                email = "corporate@mediassist.in",
                phone = "080-6919-0000",
                tollFree = "1800-425-9449",
                address = "Tower D, IBC Knowledge Park, Bannerghatta Road, Bengaluru 560029",
            ),
            Tpa(
                name = "Paramount Health Services & Insurance TPA",
                irdaiRegNo = "006",
                contactPerson = "Group Claims Desk",
                email = "customercare@paramounttpa.com",
                phone = "022-6662-0808",
                tollFree = "1800-22-3344",
                address = "Plot No. A-442, Road No. 28, MIDC Industrial Area, Wagle Estate, Thane 400604",
            ),
            Tpa(
                name = "Health India Insurance TPA Services",
                irdaiRegNo = "022",
                contactPerson = "Claims Servicing",
                email = "support@healthindiatpa.com",
                phone = "022-6151-7000",
                tollFree = "1800-220-200",
                address = "Building No. 1, Sai Commercial Complex, Govandi East, Mumbai 400088",
            ),
            Tpa(
                name = "MDIndia Health Insurance TPA",
                irdaiRegNo = "011",
                contactPerson = "Corporate Helpdesk",
                email = "info@mdindia.com",
                phone = "020-3068-5050",
                tollFree = "1800-233-1166",
                address = "S. No. 46/1, E-Space, A-2 Building, Pune Nagar Road, Vadgaon Sheri, Pune 411014",
            ),
            Tpa(
                name = "Vidal Health Insurance TPA",
                irdaiRegNo = "016",
                contactPerson = "Group Servicing",
                email = "customercare@vidalhealth.com",
                phone = "080-4626-7000",
                tollFree = "1860-425-0251",
                address = "First Floor, Tower 2, SJR I-Park, EPIP Zone, Whitefield, Bengaluru 560066",
            ),
        )
    }
}
