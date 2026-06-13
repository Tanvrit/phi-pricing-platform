package com.rate.server.routes

import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.PageRequest
import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.actuarial.AuthorityLevel
import com.rate.sdk.catalog.model.actuarial.ClaimThreshold
import com.rate.sdk.catalog.model.actuarial.IBNRReserve
import com.rate.sdk.catalog.model.actuarial.IndividualLoadingFactor
import com.rate.sdk.catalog.model.actuarial.InflationMTF
import com.rate.sdk.catalog.model.actuarial.MedicalTrendFactor
import com.rate.sdk.catalog.model.demographics.AgeBand
import com.rate.sdk.catalog.model.demographics.FamilyRelation
import com.rate.sdk.catalog.model.demographics.Gender
import com.rate.sdk.catalog.model.demographics.MemberType
import com.rate.sdk.catalog.model.demographics.PaymentFrequency
import com.rate.sdk.catalog.model.financialcapital.CostOfCapitalMatrix
import com.rate.sdk.catalog.model.financialcapital.ReinsuranceTreaty
import com.rate.sdk.catalog.model.financialcapital.SumInsuredTier
import com.rate.sdk.catalog.model.financialcore.DiscountConfig
import com.rate.sdk.catalog.model.financialcore.InvestmentIncomeConfig
import com.rate.sdk.catalog.model.financialcore.PolicyCostConfig
import com.rate.sdk.catalog.model.financialcore.VariableExpenseConfig
import com.rate.sdk.catalog.model.grouping.FamilyType
import com.rate.sdk.catalog.model.grouping.GroupSize
import com.rate.sdk.catalog.model.grouping.GroupType
import com.rate.sdk.catalog.model.grouping.IndustryType
import com.rate.sdk.catalog.model.partnerlink.BusinessType
import com.rate.sdk.catalog.model.partnerlink.ClientLocation
import com.rate.sdk.catalog.model.partnerlink.RelatedParty
import com.rate.sdk.catalog.model.partnerlink.SalesIntermediaryMapping
import com.rate.sdk.catalog.model.partnerorg.Channel
import com.rate.sdk.catalog.model.partnerorg.Insurer
import com.rate.sdk.catalog.model.partnerorg.Intermediary
import com.rate.sdk.catalog.model.partnerorg.Tpa
import com.rate.sdk.catalog.model.productconfig.AdaptiveCategory
import com.rate.sdk.catalog.model.productconfig.BenefitType
import com.rate.sdk.catalog.model.productconfig.Category
import com.rate.sdk.catalog.model.productconfig.CategoryModel
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig
import com.rate.sdk.catalog.model.rfq.ExperienceRating
import com.rate.sdk.catalog.model.rfq.GroupSizeMatrix
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.model.underwriting.DiseaseMapping
import com.rate.sdk.catalog.model.underwriting.RatingParameter
import com.rate.sdk.catalog.model.underwriting.TreatmentCategory
import com.rate.sdk.catalog.repository.AgeBandRepository
import com.rate.sdk.catalog.repository.AuthorityLevelRepository
import com.rate.sdk.catalog.repository.BusinessTypeRepository
import com.rate.sdk.catalog.repository.ChannelRepository
import com.rate.sdk.catalog.repository.ClaimThresholdRepository
import com.rate.sdk.catalog.repository.ClientLocationRepository
import com.rate.sdk.catalog.repository.CostOfCapitalMatrixRepository
import com.rate.sdk.catalog.repository.DiscountConfigRepository
import com.rate.sdk.catalog.repository.DiseaseMappingRepository
import com.rate.sdk.catalog.repository.FamilyRelationRepository
import com.rate.sdk.catalog.repository.FamilyTypeRepository
import com.rate.sdk.catalog.repository.GenderRepository
import com.rate.sdk.catalog.repository.GroupSizeRepository
import com.rate.sdk.catalog.repository.GroupTypeRepository
import com.rate.sdk.catalog.repository.IBNRReserveRepository
import com.rate.sdk.catalog.repository.IndividualLoadingFactorRepository
import com.rate.sdk.catalog.repository.IndustryTypeRepository
import com.rate.sdk.catalog.repository.InflationMTFRepository
import com.rate.sdk.catalog.repository.InsurerRepository
import com.rate.sdk.catalog.repository.IntermediaryRepository
import com.rate.sdk.catalog.repository.InvestmentIncomeConfigRepository
import com.rate.sdk.catalog.repository.MedicalTrendFactorRepository
import com.rate.sdk.catalog.repository.MemberTypeRepository
import com.rate.sdk.catalog.repository.PaymentFrequencyRepository
import com.rate.sdk.catalog.repository.PolicyCostConfigRepository
import com.rate.sdk.catalog.repository.RatingParameterRepository
import com.rate.sdk.catalog.repository.ReinsuranceTreatyRepository
import com.rate.sdk.catalog.repository.RelatedPartyRepository
import com.rate.sdk.catalog.repository.SalesIntermediaryMappingRepository
import com.rate.sdk.catalog.repository.SumInsuredTierRepository
import com.rate.sdk.catalog.repository.AdaptiveCategoryRepository
import com.rate.sdk.catalog.repository.BenefitTypeRepository
import com.rate.sdk.catalog.repository.CategoryModelRepository
import com.rate.sdk.catalog.repository.CategoryRepository
import com.rate.sdk.catalog.repository.ExperienceRatingRepository
import com.rate.sdk.catalog.repository.GroupSizeMatrixRepository
import com.rate.sdk.catalog.repository.ProductAddonConfigRepository
import com.rate.sdk.catalog.repository.RfqRepository
import com.rate.sdk.catalog.repository.TpaRepository
import com.rate.sdk.catalog.repository.TreatmentCategoryRepository
import com.rate.sdk.catalog.repository.VariableExpenseConfigRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * DEV-ONLY idempotent seeding for the Wave B masters. For each repository, if the collection is
 * currently empty (total == 0), it bulk-upserts that entity's companion defaults() rows under the
 * actor "owner-seed". Already-populated collections are left untouched (count reported as 0).
 *
 * Mounted ONLY inside the AEGIS_DEV_PROFILE dev block. POST /api/dev/seed-masters returns a JSON
 * summary of the form {"seeded": {"age-bands": 6, "genders": 3, ...}}.
 */
fun Route.seedMastersRoutes(
    ageBands: AgeBandRepository,
    genders: GenderRepository,
    paymentFrequencies: PaymentFrequencyRepository,
    memberTypes: MemberTypeRepository,
    familyRelations: FamilyRelationRepository,
    groupTypes: GroupTypeRepository,
    groupSizes: GroupSizeRepository,
    familyTypes: FamilyTypeRepository,
    industryTypes: IndustryTypeRepository,
    insurers: InsurerRepository,
    channels: ChannelRepository,
    intermediaries: IntermediaryRepository,
    tpas: TpaRepository,
    salesMappings: SalesIntermediaryMappingRepository,
    clientLocations: ClientLocationRepository,
    businessTypes: BusinessTypeRepository,
    relatedParties: RelatedPartyRepository,
    treatmentCategories: TreatmentCategoryRepository,
    diseaseMappings: DiseaseMappingRepository,
    ratingParameters: RatingParameterRepository,
    discountConfigs: DiscountConfigRepository,
    variableExpenses: VariableExpenseConfigRepository,
    policyCosts: PolicyCostConfigRepository,
    investmentIncome: InvestmentIncomeConfigRepository,
    sumInsuredTiers: SumInsuredTierRepository,
    reinsuranceTreaties: ReinsuranceTreatyRepository,
    costOfCapital: CostOfCapitalMatrixRepository,
    authorityLevels: AuthorityLevelRepository,
    medicalTrendFactors: MedicalTrendFactorRepository,
    inflationFactors: InflationMTFRepository,
    claimThresholds: ClaimThresholdRepository,
    loadingFactors: IndividualLoadingFactorRepository,
    ibnrReserves: IBNRReserveRepository,
    productAddonConfigs: ProductAddonConfigRepository,
    categories: CategoryRepository,
    benefitTypes: BenefitTypeRepository,
    categoryModels: CategoryModelRepository,
    adaptiveCategories: AdaptiveCategoryRepository,
    rfqs: RfqRepository,
    experienceRatings: ExperienceRatingRepository,
    groupSizeMatrices: GroupSizeMatrixRepository,
) {
    post("/api/dev/seed-masters") {
        val seeded = LinkedHashMap<String, Int>()

        suspend fun <T : ConfigEntity> seed(key: String, repo: ConfigRepository<T>, defaults: List<T>) {
            val empty = repo.list(PageRequest()).total == 0L
            seeded[key] = if (empty && defaults.isNotEmpty()) repo.bulkUpsert(defaults, "owner-seed") else 0
        }

        seed("age-bands", ageBands, AgeBand.defaults())
        seed("genders", genders, Gender.defaults())
        seed("payment-frequencies", paymentFrequencies, PaymentFrequency.defaults())
        seed("member-types", memberTypes, MemberType.defaults())
        seed("family-relations", familyRelations, FamilyRelation.defaults())
        seed("group-types", groupTypes, GroupType.defaults())
        seed("group-sizes", groupSizes, GroupSize.defaults())
        seed("family-types", familyTypes, FamilyType.defaults())
        seed("industry-types", industryTypes, IndustryType.defaults())
        seed("insurers", insurers, Insurer.defaults())
        seed("channels", channels, Channel.defaults())
        seed("intermediaries", intermediaries, Intermediary.defaults())
        seed("tpas", tpas, Tpa.defaults())
        seed("sales-mappings", salesMappings, SalesIntermediaryMapping.defaults())
        seed("client-locations", clientLocations, ClientLocation.defaults())
        seed("business-types", businessTypes, BusinessType.defaults())
        seed("related-parties", relatedParties, RelatedParty.defaults())
        seed("treatment-categories", treatmentCategories, TreatmentCategory.defaults())
        seed("disease-mappings", diseaseMappings, DiseaseMapping.defaults())
        seed("rating-parameters", ratingParameters, RatingParameter.defaults())
        seed("discount-configs", discountConfigs, DiscountConfig.defaults())
        seed("variable-expenses", variableExpenses, VariableExpenseConfig.defaults())
        seed("policy-costs", policyCosts, PolicyCostConfig.defaults())
        seed("investment-income", investmentIncome, InvestmentIncomeConfig.defaults())
        seed("sum-insured-tiers", sumInsuredTiers, SumInsuredTier.defaults())
        seed("reinsurance-treaties", reinsuranceTreaties, ReinsuranceTreaty.defaults())
        seed("cost-of-capital", costOfCapital, CostOfCapitalMatrix.defaults())
        seed("authority-levels", authorityLevels, AuthorityLevel.defaults())
        seed("medical-trend-factors", medicalTrendFactors, MedicalTrendFactor.defaults())
        seed("inflation-factors", inflationFactors, InflationMTF.defaults())
        seed("claim-thresholds", claimThresholds, ClaimThreshold.defaults())
        seed("loading-factors", loadingFactors, IndividualLoadingFactor.defaults())
        seed("ibnr-reserves", ibnrReserves, IBNRReserve.defaults())

        // Wave C1 masters — product-config + RFQ sales pipeline.
        seed("product-addon-configs", productAddonConfigs, ProductAddonConfig.defaults())
        seed("categories", categories, Category.defaults())
        seed("benefit-types", benefitTypes, BenefitType.defaults())
        seed("category-models", categoryModels, CategoryModel.defaults())
        seed("adaptive-categories", adaptiveCategories, AdaptiveCategory.defaults())
        seed("rfqs", rfqs, Rfq.defaults())
        seed("experience-ratings", experienceRatings, ExperienceRating.defaults())
        seed("group-size-matrices", groupSizeMatrices, GroupSizeMatrix.defaults())

        val body = buildJsonObject {
            put(
                "seeded",
                buildJsonObject { seeded.entries.forEach { (k, v) -> put(k, v) } },
            )
        }
        call.respondText(body.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }
}
