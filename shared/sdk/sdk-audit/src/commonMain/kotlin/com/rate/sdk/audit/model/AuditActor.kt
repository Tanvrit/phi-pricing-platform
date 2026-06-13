package com.rate.sdk.audit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Identifies who performed an audited action. Populated from JWT claims on the server
 * side (subject + role) and echoed back on the wire so operator surfaces can render
 * "who did what". A null subject means an unauthenticated / system-internal write.
 *
 * The fields participate in the hash chain (see [com.rate.sdk.audit.handler.AuditCanonicalizer]),
 * so they MUST serialize identically on JVM, wasmJs and iOS — which is why this is a
 * plain @Serializable record of nullable strings, not a richer typed identity.
 */
@Serializable
data class AuditActor(
    @SerialName("subject") val subject: String? = null,
    @SerialName("role") val role: String? = null,
    /** Correlation id of the request/operation that produced the event, when known. */
    @SerialName("requestId") val requestId: String? = null,
) {
    companion object {
        /** Stand-in actor for routes that have not yet wired real auth claims. */
        fun unknown(): AuditActor = AuditActor(subject = "unknown")

        /** Background / scheduler-driven writes (e.g. periodic chain verification). */
        fun system(component: String = "system"): AuditActor =
            AuditActor(subject = "system.$component", role = "system")
    }
}
