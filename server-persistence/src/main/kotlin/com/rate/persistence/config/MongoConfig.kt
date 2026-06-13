package com.rate.persistence.config

/**
 * Connection settings for the MongoDB-backed persistence layer.
 *
 * Read from the environment so the secret connection string never lands in source / VCS
 * (the only place secrets live is this app layer). There is NO production default for the
 * URI password — a misconfigured deployment must fail fast rather than silently hit a
 * wrong/empty database, mirroring the old server's `DB_PASSWORD`-has-no-default rule.
 *
 *  - `MONGO_URI`  e.g. `mongodb://rate_admin:****@localhost:27017/?authSource=admin`
 *  - `MONGO_DB`   logical database name (defaults to `rate`).
 */
data class MongoConfig(
    val uri: String,
    val database: String,
) {
    init {
        require(uri.isNotBlank()) { "MONGO_URI must not be blank" }
        require(database.isNotBlank()) { "MONGO_DB must not be blank" }
    }

    companion object {
        const val ENV_URI = "MONGO_URI"
        const val ENV_DB = "MONGO_DB"
        const val DEFAULT_DB = "rate"

        /** Local-dev convenience default; production MUST supply [ENV_URI] via the environment. */
        const val DEFAULT_LOCAL_URI = "mongodb://localhost:27017"

        /**
         * Build from process environment. [requireUri] = true (production) fail-fasts when
         * [ENV_URI] is unset; tests pass [requireUri] = false to fall back to the local URI.
         */
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            requireUri: Boolean = true,
        ): MongoConfig {
            val uri = env[ENV_URI]?.takeIf { it.isNotBlank() }
                ?: if (requireUri) {
                    error("$ENV_URI environment variable is required (no default in production)")
                } else {
                    DEFAULT_LOCAL_URI
                }
            val db = env[ENV_DB]?.takeIf { it.isNotBlank() } ?: DEFAULT_DB
            return MongoConfig(uri = uri, database = db)
        }
    }
}
