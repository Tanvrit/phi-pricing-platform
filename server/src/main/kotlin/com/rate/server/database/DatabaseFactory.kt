package com.rate.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.util.concurrent.TimeUnit

object DatabaseFactory {

    /**
     * Exposed to /metrics + /health/ready so we can read pool stats and run a
     * `SELECT 1` against the live pool. Null until `init()` runs.
     */
    @Volatile var dataSource: HikariDataSource? = null
        private set

    fun init(config: ApplicationConfig) {
        val jdbcUrl  = config.property("database.url").getString()
        val user     = config.property("database.user").getString()
        // password is required from env (no default); fail fast with a useful message.
        val password = config.propertyOrNull("database.password")?.getString()
            ?: error("DB_PASSWORD env var is required (no default since Foundation Pack §1D).")
        val poolSize = config.property("database.maxPoolSize").getString().toInt()

        // Flyway: pure additive migrations now. baselineOnMigrate=true so any
        // pre-Foundation-Pack DB that already has the V1 tables baselines cleanly.
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl              = jdbcUrl
            driverClassName           = config.property("database.driver").getString()
            username                  = user
            this.password             = password
            maximumPoolSize           = poolSize
            isAutoCommit              = false
            transactionIsolation      = "TRANSACTION_REPEATABLE_READ"
            connectionTimeout         = TimeUnit.SECONDS.toMillis(30)
            idleTimeout               = TimeUnit.MINUTES.toMillis(10)
            maxLifetime               = TimeUnit.MINUTES.toMillis(30)
            leakDetectionThreshold    = TimeUnit.SECONDS.toMillis(60)
            validate()
        }
        val ds = HikariDataSource(hikariConfig)
        Database.connect(ds)
        dataSource = ds
    }
}
