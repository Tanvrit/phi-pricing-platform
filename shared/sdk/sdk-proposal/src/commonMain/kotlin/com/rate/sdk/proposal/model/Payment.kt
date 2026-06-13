package com.rate.sdk.proposal.model

import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Settlement bank account captured on a proposal — used for premium debit (mandate /
 * standing instruction) and for refunds during free-look cancellation.
 *
 * Mirrors sdk-party's `BankAccount` shape but lives here as a proposal-scoped value object
 * (the proposal snapshots the bank details at submit time, decoupled from the proposer's
 * evolving party record). The four NPCI-format fields are validated by [com.rate.sdk.proposal.handler.ProposalHandler]
 * via the core `Validators` (account number 9-18 digits, IFSC 11-char).
 */
@Serializable
data class BankDetails(
    @SerialName("accountHolderName") val accountHolderName: String = "",
    @SerialName("accountNumber") val accountNumber: String = "",
    @SerialName("ifsc") val ifsc: String = "",
    @SerialName("bankName") val bankName: String = "",
    @SerialName("branch") val branch: String = "",
)

/** Lifecycle of the premium payment for a proposal. */
@Serializable
enum class PaymentStatus {
    /** No payment attempted yet. */
    PENDING,

    /** Aggregator handoff initiated; awaiting callback. */
    INITIATED,

    /** Payment confirmed (gateway success callback). */
    PAID,

    /** Payment attempt failed / abandoned. */
    FAILED,

    /** Refunded (e.g. free-look cancellation). */
    REFUNDED,
}

/** Premium payment instrument the customer chose. */
@Serializable
enum class PaymentMethod {
    NETBANKING, UPI, CARD, MANDATE, OFFLINE,
}

/**
 * Payment snapshot on a proposal. The monetary [amount] is [Money] (proposal-time figure;
 * the engine accumulates in Double, conversion to Money happens at proposal assembly — see
 * [com.rate.sdk.proposal.handler.ProposalHandler]). [bankDetails] carries the settlement
 * account; [gatewayRef] is the aggregator's transaction id once a real gateway is wired
 * (Phase-2 — the monolith stored none).
 */
@Serializable
data class PaymentInfo(
    @SerialName("status") val status: PaymentStatus = PaymentStatus.PENDING,
    @SerialName("method") val method: PaymentMethod? = null,
    @SerialName("amount") val amount: Money = Money.ZERO,
    @SerialName("bankDetails") val bankDetails: BankDetails? = null,
    /** Aggregator/gateway transaction reference once payment is captured. */
    @SerialName("gatewayRef") val gatewayRef: String? = null,
    @SerialName("paidAt") val paidAt: Instant? = null,
) {
    val isPaid: Boolean get() = status == PaymentStatus.PAID
}
