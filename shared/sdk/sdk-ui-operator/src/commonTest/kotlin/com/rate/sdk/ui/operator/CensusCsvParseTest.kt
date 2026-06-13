package com.rate.sdk.ui.operator

import com.rate.sdk.party.model.Sex
import com.rate.sdk.party.model.group.CensusRelation
import com.rate.sdk.ui.operator.surface.parseCensusCsv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CensusCsvParseTest {

    @Test
    fun parses_a_well_formed_census_skipping_the_header() {
        val csv = """
            empId,name,age,gender,grade,relation,sumInsured
            E1,Asha,34,F,Grade A,EMPLOYEE,2500000
            E1,Ravi,36,M,Grade A,SPOUSE,2500000
            E2,Meera,29,F,Grade B,EMPLOYEE,1000000
        """.trimIndent()
        val (members, warnings) = parseCensusCsv(csv)
        assertEquals(3, members.size)
        assertTrue(warnings.isEmpty())
        assertEquals("E1", members[0].empId)
        assertEquals(Sex.FEMALE, members[0].gender)
        assertEquals(CensusRelation.SPOUSE, members[1].relation)
        assertEquals(2_500_000L, members[0].sumInsured)
    }

    @Test
    fun warns_and_skips_unparseable_rows() {
        val csv = """
            E1,Asha,notanage,F,Grade A,EMPLOYEE,100
            ,Nobody,40,M,Grade A,EMPLOYEE,100
            E3,Valid,40,M,Grade C,EMPLOYEE,500000
        """.trimIndent()
        val (members, warnings) = parseCensusCsv(csv)
        assertEquals(1, members.size)
        assertEquals("E3", members[0].empId)
        assertEquals(2, warnings.size)
    }

    @Test
    fun defaults_missing_optional_columns() {
        val (members, _) = parseCensusCsv("E9,,45")
        assertEquals(1, members.size)
        assertEquals("DEFAULT", members[0].grade)
        assertEquals(CensusRelation.EMPLOYEE, members[0].relation)
        assertEquals(0L, members[0].sumInsured)
    }
}
