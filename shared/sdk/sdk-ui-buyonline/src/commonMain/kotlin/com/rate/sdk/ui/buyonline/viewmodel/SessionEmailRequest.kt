package com.rate.sdk.ui.buyonline.viewmodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for the "email me the resume link" affordance on the ResumeBanner.
 *
 * RELOCATED from the monolith's `BuyOnlineApiClient.SessionEmailRequest`. The
 * `/api/buy-online/session/email` route isn't part of sdk-proposal's
 * [com.rate.sdk.proposal.network.BuyOnlineApi] surface (it's a Phase-1 mock that
 * only audit-records), so [BuyOnlineApp] POSTs it directly via the
 * core-network `TanvritClient`. Field names preserved for wire parity.
 */
@Serializable
data class SessionEmailRequest(
    @SerialName("email") val email: String,
    @SerialName("url") val url: String,
)
