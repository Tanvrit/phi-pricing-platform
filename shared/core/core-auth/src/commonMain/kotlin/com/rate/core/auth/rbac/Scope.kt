package com.rate.core.auth.rbac

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A single permission, encoded as a dotted string `config.<entity>.<verb>` (e.g.
 * `config.plan.write`, `config.cover.read`, `quote.run`). Value-type over a String so
 * scopes are cheap to pass, compare and serialize while still being a distinct type.
 *
 * Wildcards are supported via [matches]: a granted `config.*.read` satisfies a
 * required `config.plan.read`, and `*` satisfies anything (the admin grant).
 *
 * Relocated from the server's free-string scopes (`"plans.write"` etc.) into a typed
 * but wire-compatible value class.
 */
@Serializable
@JvmInline
value class Scope(val value: String) {

    /** Dotted segments of this scope, e.g. `["config","plan","write"]`. */
    val segments: List<String> get() = value.split('.')

    /**
     * Does THIS scope (as a GRANT) satisfy [required]? Segment-wise match where a `*`
     * segment in the grant matches any single required segment; a sole `*` grant
     * matches everything. Lengths must match unless the grant is the global `*`.
     */
    fun matches(required: Scope): Boolean {
        if (value == ALL) return true
        val g = segments
        val r = required.segments
        if (g.size != r.size) return false
        return g.indices.all { g[it] == "*" || g[it] == r[it] }
    }

    override fun toString(): String = value

    companion object {
        /** The super-grant: satisfies every required scope. */
        const val ALL = "*"

        // ── Verb suffixes ────────────────────────────────────────────────────
        const val READ = "read"
        const val WRITE = "write"
        const val DELETE = "delete"
        const val PUBLISH = "publish"

        /** Build a `config.<entity>.<verb>` scope for an admin-CRUD entity. */
        fun config(entity: String, verb: String): Scope = Scope("config.$entity.$verb")

        // ── Well-known non-config scopes ─────────────────────────────────────
        val QUOTE_RUN = Scope("quote.run")
        val QUOTE_READ = Scope("quote.read")
        val PROPOSAL_OWN = Scope("proposal.own")
        val PROPOSAL_READ_ALL = Scope("proposal.read.all")
        val IMPORT_RUN = Scope("import.run")
        val AUDIT_READ = Scope("audit.read")
        val RBAC_MANAGE = Scope("rbac.manage")
        val SETTINGS_MANAGE = Scope("settings.manage")

        /** Catalog of admin-CRUD entity names that get config.* scopes. */
        private val CONFIG_ENTITIES = listOf(
            "plan", "cover", "discount", "tenure", "section",
            "zone", "pincode", "addon", "rate", "group",
        )

        private fun configBundle(verbs: List<String>): Set<Scope> =
            CONFIG_ENTITIES.flatMap { e -> verbs.map { v -> config(e, v) } }.toSet()

        /**
         * The default scope bundle for a role.
         *  - CUSTOMER: read catalog, run + read own quotes, own proposals.
         *  - BUSINESS: everything CUSTOMER has + write catalog, run any quote,
         *    read all proposals, run imports, read audit.
         *  - ADMIN: the global `*` grant.
         */
        fun bundleFor(role: AuthRole): Set<Scope> = when (role) {
            AuthRole.CUSTOMER -> buildSet {
                addAll(configBundle(listOf(READ)))
                add(QUOTE_RUN); add(QUOTE_READ); add(PROPOSAL_OWN)
            }
            AuthRole.BUSINESS -> buildSet {
                addAll(configBundle(listOf(READ, WRITE, PUBLISH)))
                add(QUOTE_RUN); add(QUOTE_READ)
                add(PROPOSAL_OWN); add(PROPOSAL_READ_ALL)
                add(IMPORT_RUN); add(AUDIT_READ)
            }
            AuthRole.ADMIN -> setOf(Scope(ALL))
            AuthRole.OWNER -> setOf(Scope(ALL))
        }
    }
}

/** Render a scope set to the flat strings that live in [com.rate.core.auth.token.JwtClaims.scopes]. */
fun Set<Scope>.toStringSet(): Set<String> = mapTo(mutableSetOf()) { it.value }

/** Parse the flat strings from a JWT back into typed scopes. */
fun Set<String>.toScopeSet(): Set<Scope> = mapTo(mutableSetOf()) { Scope(it) }
