package com.rate.server.database.repositories

import com.rate.domain.model.*
import com.rate.domain.repository.RateDataProvider
import com.rate.server.database.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal

class RateDataProviderImpl : RateDataProvider {

    override suspend fun getBasePremium(
        planId: String, familyType: String, zone: String,
        ageBandMinAge: Int, sumInsured: Long
    ): Double = newSuspendedTransaction {
        BaseRatesTable.selectAll().where {
            (BaseRatesTable.planId eq planId) and
            (BaseRatesTable.familyType eq familyType) and
            (BaseRatesTable.zone eq zone) and
            (BaseRatesTable.ageBandMin eq ageBandMinAge) and
            (BaseRatesTable.sumInsured eq sumInsured)
        }.firstOrNull()?.get(BaseRatesTable.annualPremium)?.toDouble() ?: 0.0
    }

    override suspend fun getCoverRate(
        coverId: String, param1: String?, param2: String?,
        ageBandMinAge: Int?, sumInsured: Long?, planOrTenureKey: String?
    ): Double = newSuspendedTransaction {
        var q = CoverRateLookupTable.selectAll().where { CoverRateLookupTable.coverId eq coverId }
        if (param1 != null)          q = q.andWhere { CoverRateLookupTable.param1Key eq param1 }
        else                         q = q.andWhere { CoverRateLookupTable.param1Key.isNull() }
        if (param2 != null)          q = q.andWhere { CoverRateLookupTable.param2Key eq param2 }
        else                         q = q.andWhere { CoverRateLookupTable.param2Key.isNull() }
        if (ageBandMinAge != null)   q = q.andWhere { CoverRateLookupTable.ageBandMin eq ageBandMinAge }
        else                         q = q.andWhere { CoverRateLookupTable.ageBandMin.isNull() }
        if (sumInsured != null)      q = q.andWhere { CoverRateLookupTable.sumInsured eq sumInsured }
        else                         q = q.andWhere { CoverRateLookupTable.sumInsured.isNull() }
        if (planOrTenureKey != null) q = q.andWhere { CoverRateLookupTable.planId eq planOrTenureKey }
        else                         q = q.andWhere { CoverRateLookupTable.planId.isNull() }

        q.firstOrNull()?.get(CoverRateLookupTable.rate)?.toDouble() ?: 0.0
    }

    override suspend fun getMemberLevelRate(
        coverId: String, memberAgeBandMin: Int,
        param1: String?, sumInsured: Long?
    ): Double = newSuspendedTransaction {
        var q = MemberLevelRatesTable.selectAll().where { MemberLevelRatesTable.coverId eq coverId }
        if (memberAgeBandMin > 0) q = q.andWhere { MemberLevelRatesTable.ageBandMin eq memberAgeBandMin }
        else                      q = q.andWhere { MemberLevelRatesTable.ageBandMin.isNull() }
        if (param1 != null)       q = q.andWhere { MemberLevelRatesTable.param1Key eq param1 }
        else                      q = q.andWhere { MemberLevelRatesTable.param1Key.isNull() }
        q.firstOrNull()?.get(MemberLevelRatesTable.rate)?.toDouble() ?: 0.0
    }

    override suspend fun getInstalmentCount(
        tenure: Tenure, paymentTenure: Tenure, paymentMode: PaymentMode
    ): Int = newSuspendedTransaction {
        InstalmentConfigTable.selectAll().where {
            (InstalmentConfigTable.policyTenure  eq tenure.label) and
            (InstalmentConfigTable.paymentTenure eq paymentTenure.label) and
            (InstalmentConfigTable.paymentMode   eq paymentMode.label)
        }.firstOrNull()?.get(InstalmentConfigTable.instalmentCount) ?: 1
    }

    override suspend fun getAllPlans(): List<Plan> = newSuspendedTransaction {
        PlansTable.selectAll().map { it.toPlan() }
    }

    override suspend fun getPlan(planId: String): Plan? = newSuspendedTransaction {
        PlansTable.selectAll().where { PlansTable.id eq planId }.firstOrNull()?.toPlan()
    }

    override suspend fun getDiscountRate(discountId: String, paramKey: String?): Double = newSuspendedTransaction {
        val compositeId = if (paramKey != null) "${discountId}_${paramKey}" else discountId
        DiscountRatesTable.selectAll()
            .where { DiscountRatesTable.id eq compositeId }
            .firstOrNull()?.get(DiscountRatesTable.rate)?.toDouble() ?: 0.0
    }

    override suspend fun getCoverAvailability(planId: String): Set<String> = newSuspendedTransaction {
        CoverAvailabilityTable.selectAll()
            .where { CoverAvailabilityTable.planId eq planId }
            .map { it[CoverAvailabilityTable.coverId] }
            .toSet()
    }

    override suspend fun rateTableVersion(): String =
        // When the V2 migration (rate_meta table) lands, swap this to read the latest
        // imported version from the DB. For now, a static tag identifies the impl.
        "db-excel-v7.0"

    private fun ResultRow.toPlan() = Plan(
        id                   = this[PlansTable.id],
        name                 = this[PlansTable.name],
        planType             = PlanType.valueOf(this[PlansTable.planType]),
        underwritingCategory = UnderwritingCategory.valueOf(this[PlansTable.underwritingCategory]),
        geographyScope       = GeographyScope.valueOf(this[PlansTable.geographyScope]),
        coPaymentTable       = CoPaymentTable.valueOf(this[PlansTable.coPaymentTable]),
        description          = this[PlansTable.description],
        availableSumInsureds = this[PlansTable.availableSumInsureds].toJsonLongList(),
        availableZones       = this[PlansTable.availableZones].toJsonStringList(),
        availableFamilyTypes = this[PlansTable.availableFamilyTypes].toJsonStringList(),
        maxDiscountCap       = this[PlansTable.maxDiscountCap].toDouble(),
        rateTableId          = this[PlansTable.rateTableId],
        minAge               = this[PlansTable.minAge],
        maxAge               = this[PlansTable.maxAge],
        isActive             = this[PlansTable.isActive]
        // gstRate defaults to 0.18 — when DB schema gains a gst_rate column, read it here.
    )

    private fun String.toJsonLongList()   = trim('[',']').split(",").mapNotNull { it.trim().toLongOrNull() }
    private fun String.toJsonStringList() = trim('[',']').split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
}
