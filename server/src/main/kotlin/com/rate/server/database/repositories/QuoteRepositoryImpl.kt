package com.rate.server.database.repositories

import com.rate.domain.model.QuoteRequest
import com.rate.domain.model.QuoteResult
import com.rate.domain.repository.QuoteRepository
import com.rate.server.database.tables.QuotesTable
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal

class QuoteRepositoryImpl : QuoteRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveQuote(request: QuoteRequest, result: QuoteResult): String =
        newSuspendedTransaction {
            QuotesTable.insert {
                it[id]               = result.requestId
                it[createdAt]        = Clock.System.now()
                it[planId]           = request.planId
                it[primaryAge]       = request.primaryAge
                it[sumInsured]       = request.sumInsured
                it[familyType]       = request.familyType
                it[zone]             = request.zone
                it[tenure]           = request.tenure.label
                it[paymentMode]      = request.paymentMode.label
                it[requestJson]      = json.encodeToString(request)
                it[resultJson]       = json.encodeToString(result)
                it[basePremium]      = BigDecimal.valueOf(result.basePremiumTotal)
                it[finalPremium]     = BigDecimal.valueOf(result.totalAfterDiscount)
                it[instalmentPremium] = BigDecimal.valueOf(result.instalmentPremium)
            }
            result.requestId
        }

    override suspend fun getQuote(id: String): Pair<QuoteRequest, QuoteResult>? =
        newSuspendedTransaction {
            QuotesTable.selectAll().where { QuotesTable.id eq id }.firstOrNull()?.let {
                json.decodeFromString<QuoteRequest>(it[QuotesTable.requestJson]) to
                json.decodeFromString<QuoteResult>(it[QuotesTable.resultJson])
            }
        }

    override suspend fun listQuotes(limit: Int): List<Pair<String, QuoteRequest>> =
        newSuspendedTransaction {
            QuotesTable.selectAll().limit(limit).map {
                it[QuotesTable.id] to json.decodeFromString<QuoteRequest>(it[QuotesTable.requestJson])
            }
        }
}
