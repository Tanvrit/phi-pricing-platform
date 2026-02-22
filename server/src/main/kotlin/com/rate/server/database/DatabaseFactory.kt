package com.rate.server.database

import com.rate.server.database.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val jdbcUrl  = config.property("database.url").getString()
        val user     = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        val poolSize = config.property("database.maxPoolSize").getString().toInt()

        // Run Flyway migrations first (V1 drops + recreates all tables)
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl         = jdbcUrl
            driverClassName      = config.property("database.driver").getString()
            username             = user
            this.password        = password
            maximumPoolSize      = poolSize
            isAutoCommit         = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        Database.connect(HikariDataSource(hikariConfig))
    }
}
