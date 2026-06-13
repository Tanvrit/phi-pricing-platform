package com.rate.aegis

import com.rate.sdk.ui.operator.OperatorRole

/**
 * Aegis is a single Compose Multiplatform app whose surface depends on the role
 * of whoever opens it. JVM (desktop) defaults to BUSINESS; WASM (web) defaults
 * to CUSTOMER.
 *
 *   CUSTOMER → the public buy-online customer journey (sdk-ui-buyonline).
 *   BUSINESS → the operator console (sdk-ui-operator, operational surfaces).
 *   ADMIN    → the admin console (sdk-ui-operator, + config-CRUD + ingestion).
 *
 * This enum is the app-shell's own role token; [AegisRoot] maps BUSINESS/ADMIN
 * onto the operator module's [OperatorRole]. CUSTOMER never reaches the operator
 * console — it short-circuits to the buy-online app.
 */
enum class AegisRole {
    CUSTOMER,
    BUSINESS,
    ADMIN,
    OWNER;

    companion object {
        /**
         * Parse a persisted / supplied role string (case-insensitive), falling
         * back to [fallback] for an unknown or corrupt value so a stale settings
         * file or a bad `?role=` query never crashes the launcher.
         */
        fun parse(raw: String?, fallback: AegisRole): AegisRole =
            raw?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
                ?: fallback
    }
}

/**
 * Map the app-shell [AegisRole] onto the operator module's [OperatorRole]. Only
 * BUSINESS/ADMIN are valid operator roles; CUSTOMER never reaches the operator
 * console (it renders the buy-online journey instead), but we map it to
 * [OperatorRole.CUSTOMER] for completeness / defensive call sites.
 */
fun AegisRole.toOperatorRole(): OperatorRole = when (this) {
    AegisRole.CUSTOMER -> OperatorRole.CUSTOMER
    AegisRole.BUSINESS -> OperatorRole.BUSINESS
    AegisRole.ADMIN    -> OperatorRole.ADMIN
    AegisRole.OWNER    -> OperatorRole.OWNER
}
