package com.rate.aegis

/**
 * Aegis is a single Compose Multiplatform app whose surface depends on the role
 * of whoever opens it. JVM (desktop) defaults to BUSINESS; WASM (web) defaults
 * to CUSTOMER. Admin is reachable from BUSINESS once an authenticated user has
 * the right RBAC scope — wired in a later phase.
 */
enum class AegisRole {
    /** End-customer buy-online journey (22 screens). Currently the WASM target. */
    CUSTOMER,

    /** Operator surfaces: home / quote explorer / plan configurator / cover catalog
     *  + the rate-calculator originally lived in :desktop. Currently the JVM target. */
    BUSINESS,

    /** Audit / RBAC / settings — opened from BUSINESS once authenticated with the
     *  admin scope. Not yet implemented; rendered as a placeholder. */
    ADMIN
}
