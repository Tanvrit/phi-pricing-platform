package com.rate.sdk.party.model.group

import com.rate.core.regulatory.AgeBand
import com.rate.core.regulatory.getAgeBand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Count of lives in a single (age-band) bucket, the unit the group rating roll-up
 * consumes — group premium = Σ over buckets of (count × band base-rate × loadings).
 * [ageBandMinAge] is the band's `minAge`, the same key `RateDataProvider` lookups use.
 */
@Serializable
data class AgeBandBucket(
    @SerialName("ageBandLabel") val ageBandLabel: String,
    @SerialName("ageBandMinAge") val ageBandMinAge: Int,
    @SerialName("count") val count: Int,
)

/**
 * Per-grade roll-up of a census: how many lives fall into each age band within one
 * benefit grade, plus the grade's representative sum insured and total lives.
 */
@Serializable
data class GradeAggregation(
    @SerialName("grade") val grade: String,
    @SerialName("totalLives") val totalLives: Int,
    /** Modal (most common) SI in the grade — the grade's rating SI. */
    @SerialName("sumInsured") val sumInsured: Long,
    @SerialName("buckets") val buckets: List<AgeBandBucket>,
)

/**
 * Full age-band rollup of a census for rating: per-grade aggregations plus the
 * census-wide age-band distribution and head count. Produced by
 * [com.rate.sdk.party.handler.group.CensusHandler.aggregate]; consumed by the
 * group rating path (sdk-rating) over `GroupRateDataProvider`.
 */
@Serializable
data class CensusAggregation(
    @SerialName("employerPartyRef") val employerPartyRef: String,
    @SerialName("censusId") val censusId: String,
    @SerialName("totalLives") val totalLives: Int,
    @SerialName("byGrade") val byGrade: List<GradeAggregation>,
    /** Census-wide distribution across age bands (sum of all grades). */
    @SerialName("overallBuckets") val overallBuckets: List<AgeBandBucket>,
    @SerialName("averageAge") val averageAge: Double,
) {
    companion object {
        /**
         * Pure roll-up of census [members] into the (grade × age-band) structure.
         * Stable ordering: grades by `RateDataProvider`-friendly natural order,
         * buckets by ascending band min-age.
         */
        fun from(
            employerPartyRef: String,
            censusId: String,
            members: List<CensusMember>,
        ): CensusAggregation {
            if (members.isEmpty()) {
                return CensusAggregation(
                    employerPartyRef = employerPartyRef,
                    censusId = censusId,
                    totalLives = 0,
                    byGrade = emptyList(),
                    overallBuckets = emptyList(),
                    averageAge = 0.0,
                )
            }

            val byGrade = members.groupBy { it.grade }
                .entries.sortedBy { it.key.toString() }
                .map { (grade, gradeMembers) ->
                    GradeAggregation(
                        grade = grade,
                        totalLives = gradeMembers.size,
                        sumInsured = modalSumInsured(gradeMembers),
                        buckets = bucketize(gradeMembers),
                    )
                }

            val averageAge = members.sumOf { it.age }.toDouble() / members.size

            return CensusAggregation(
                employerPartyRef = employerPartyRef,
                censusId = censusId,
                totalLives = members.size,
                byGrade = byGrade,
                overallBuckets = bucketize(members),
                averageAge = averageAge,
            )
        }

        /** Group lives into ascending age-band buckets, dropping empty bands. */
        private fun bucketize(members: List<CensusMember>): List<AgeBandBucket> =
            members.groupBy { bandFor(it.age) }
                .entries
                .sortedBy { it.key.minAge }
                .map { (band, lives) ->
                    AgeBandBucket(
                        ageBandLabel = band.label,
                        ageBandMinAge = band.minAge,
                        count = lives.size,
                    )
                }

        /** Most common SI in the group (ties broken by the larger SI for safety). */
        private fun modalSumInsured(members: List<CensusMember>): Long =
            members.groupingBy { it.sumInsured }.eachCount()
                .entries
                .maxWithOrNull(compareBy({ it.value }, { it.key }))
                ?.key ?: 0L

        /** Clamp census ages into the supported rating range before banding. */
        private fun bandFor(age: Int): AgeBand = getAgeBand(age.coerceIn(0, 120))
    }
}
