package com.rate.sdk.quoting.handler.doc

import com.rate.core.money.Money
import com.rate.core.money.toMoney
import com.rate.sdk.catalog.model.group.BenefitSchedule
import com.rate.sdk.catalog.model.group.GroupGrade
import com.rate.sdk.catalog.model.group.GroupProductConfig
import com.rate.sdk.quoting.model.GroupQuote
import kotlinx.serialization.Serializable

/**
 * Builds the GROUP "Benefit Schedule" document issued with an Employer-Employee / GHI policy.
 *
 * There was no monolith equivalent (the old codebase only had the three RETAIL IRDAI docs); this
 * is the GROUP-side counterpart, assembled from sdk-catalog's group config tree
 * ([GroupProductConfig] → [GroupGrade] → [BenefitSchedule]) and stamped with the priced
 * [GroupQuote] totals. The app layer renders it to PDF as Annexure A of the group policy.
 *
 * Per grade we emit the resolved cover limits (the PBT "Part 1 Base Covers / Part 2 Optional
 * Covers" tables) plus the grade's sum-insured; the document footer carries the group roll-up
 * (total lives + total annual premium incl. GST).
 */
@Serializable
data class GradeBenefitLine(
    val coverCode: String,
    val coverName: String,
    val limit: Money,
    val selectedOption: String? = null,
    val subLimitText: String = "",
    val included: Boolean = true,
)

@Serializable
data class GradeBenefitBlock(
    val grade: String,
    val description: String,
    val sumInsured: Money,
    val lines: List<GradeBenefitLine>,
)

@Serializable
data class GroupBenefitSchedule(
    val productName: String,
    val productCode: String,
    val policyType: String,
    val employerName: String,
    val totalLives: Int,
    val grades: List<GradeBenefitBlock>,
    val totalAnnualPremiumInclGst: Money,
    val disclaimer: String = DEFAULT_DISCLAIMER,
) {
    /** Number of covered lines aggregated across all grades. */
    val totalBenefitLines: Int get() = grades.sumOf { it.lines.count(GradeBenefitLine::included) }

    companion object {
        const val DEFAULT_DISCLAIMER: String =
            "This benefit schedule forms part of the group master policy and supersedes any prior " +
                "schedule. Cover limits are per the grade assignment in force on the date of " +
                "hospitalisation. Refer to the Policy Wording for exclusions, sub-limits and " +
                "waiting periods."
    }
}

object GroupBenefitScheduleBuilder {

    /**
     * Assemble the benefit schedule for a priced [quote].
     *
     * @param config   the group product config (drives product name / policy type).
     * @param grades   the grades in this scheme (each references its benefit schedules).
     * @param schedules the resolved benefit schedules, keyed by id, that [grades] reference.
     */
    fun build(
        quote: GroupQuote,
        config: GroupProductConfig,
        grades: List<GroupGrade>,
        schedules: List<BenefitSchedule>,
    ): GroupBenefitSchedule {
        val byId = schedules.associateBy { it.id }
        val gradeBlocks = grades
            .sortedBy { it.displayOrder }
            .map { grade -> gradeBlock(grade, byId) }

        return GroupBenefitSchedule(
            productName = config.name,
            productCode = config.code,
            policyType = config.policyType.name,
            employerName = quote.employerName ?: "(employer)",
            totalLives = quote.totalLives,
            grades = gradeBlocks,
            totalAnnualPremiumInclGst = quote.result.totalIncludingGst.toMoney(),
        )
    }

    private fun gradeBlock(grade: GroupGrade, byId: Map<String, BenefitSchedule>): GradeBenefitBlock {
        // Flatten every benefit schedule referenced by the grade into resolved lines, keeping
        // the first occurrence per cover code (a later schedule does not override an earlier one).
        val lines = grade.benefitScheduleRefs
            .mapNotNull { byId[it] }
            .flatMap { it.lines }
            .associateBy { it.coverCode }
            .values
            .map { line ->
                GradeBenefitLine(
                    coverCode = line.coverCode,
                    coverName = line.coverName,
                    limit = line.limit,
                    selectedOption = line.selectedOption,
                    subLimitText = line.subLimitText,
                    included = line.included,
                )
            }
        return GradeBenefitBlock(
            grade = grade.grade,
            description = grade.description,
            sumInsured = grade.sumInsured,
            lines = lines,
        )
    }
}
