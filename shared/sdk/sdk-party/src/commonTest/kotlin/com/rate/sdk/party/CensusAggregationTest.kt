package com.rate.sdk.party

import com.rate.sdk.party.model.Sex
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.model.group.CensusMember
import com.rate.sdk.party.model.group.CensusRelation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CensusAggregationTest {

    private fun life(empId: String, age: Int, grade: String, si: Long = 1_000_000) =
        CensusMember(
            empId = empId,
            age = age,
            gender = Sex.MALE,
            grade = grade,
            relation = CensusRelation.EMPLOYEE,
            sumInsured = si,
        )

    @Test
    fun emptyCensusRollsUpToZero() {
        val agg = CensusAggregation.from("emp1", "c1", emptyList())
        assertEquals(0, agg.totalLives)
        assertTrue(agg.byGrade.isEmpty())
        assertTrue(agg.overallBuckets.isEmpty())
        assertEquals(0.0, agg.averageAge)
    }

    @Test
    fun rollupBucketsByGradeAndAgeBand() {
        val members = listOf(
            life("E1", 28, "A", 5_000_000),     // band 26-30
            life("E2", 29, "A", 5_000_000),     // band 26-30
            life("E3", 42, "A", 5_000_000),     // band 41-45
            life("E4", 35, "B", 1_000_000),     // band 31-35
        )
        val agg = CensusAggregation.from("emp1", "c1", members)

        assertEquals(4, agg.totalLives)
        assertEquals(2, agg.byGrade.size)
        // grades sorted
        assertEquals(listOf("A", "B"), agg.byGrade.map { it.grade })

        val gradeA = agg.byGrade.first { it.grade == "A" }
        assertEquals(3, gradeA.totalLives)
        assertEquals(5_000_000, gradeA.sumInsured)
        // two age bands in grade A, ascending by min-age
        assertEquals(listOf(26, 41), gradeA.buckets.map { it.ageBandMinAge })
        assertEquals(2, gradeA.buckets.first { it.ageBandMinAge == 26 }.count)

        // overall avg age = (28+29+42+35)/4 = 33.5
        assertEquals(33.5, agg.averageAge)
        // total across overall buckets == total lives
        assertEquals(4, agg.overallBuckets.sumOf { it.count })
    }

    @Test
    fun modalSumInsuredPicksMostCommon() {
        val members = listOf(
            life("E1", 30, "X", 1_000_000),
            life("E2", 31, "X", 1_000_000),
            life("E3", 32, "X", 2_500_000),
        )
        val agg = CensusAggregation.from("emp1", "c1", members)
        assertEquals(1_000_000, agg.byGrade.single().sumInsured)
    }
}
