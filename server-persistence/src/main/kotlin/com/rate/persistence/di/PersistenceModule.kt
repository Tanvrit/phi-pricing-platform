package com.rate.persistence.di

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.rating.ports.GroupRateDataProvider
import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.QuoteRepository
import com.rate.core.rating.ports.RateDataProvider
import com.rate.persistence.base.SeqCounter
import com.rate.persistence.config.MongoClientProvider
import com.rate.persistence.config.MongoConfig
import com.rate.persistence.importer.ExcelRateImporter
import com.rate.persistence.rating.MongoGroupRateDataProvider
import com.rate.persistence.rating.MongoRateDataProvider
import com.rate.persistence.rating.RateTableCache
import com.rate.persistence.repository.AddOnRepositoryImpl
import com.rate.persistence.repository.AnnexureRepositoryImpl
import com.rate.persistence.repository.AuditStoreImpl
import com.rate.persistence.repository.BenefitScheduleRepositoryImpl
import com.rate.persistence.repository.CensusRepositoryImpl
import com.rate.persistence.repository.ChronicOpdGridRepositoryImpl
import com.rate.persistence.repository.ClaimRepositoryImpl
import com.rate.persistence.repository.ConsumablesListRepositoryImpl
import com.rate.persistence.repository.CoverRepositoryImpl
import com.rate.persistence.repository.CriticalIllnessListRepositoryImpl
import com.rate.persistence.repository.DayCareProcedureRepositoryImpl
import com.rate.persistence.repository.EligibilityCriteriaRepositoryImpl
import com.rate.persistence.repository.GroupGradeRepositoryImpl
import com.rate.persistence.repository.GroupProductConfigRepositoryImpl
import com.rate.persistence.repository.HealthCheckupPackageRepositoryImpl
import com.rate.persistence.repository.IdempotencyStoreImpl
import com.rate.persistence.repository.LoginAuditRepositoryImpl
import com.rate.persistence.repository.MedicalDeviceCatalogRepositoryImpl
import com.rate.persistence.repository.PartyMemberRepositoryImpl
import com.rate.persistence.repository.PartyRepositoryImpl
import com.rate.persistence.repository.PincodeZoneRepositoryImpl
import com.rate.persistence.repository.PlanRepositoryImpl
import com.rate.persistence.repository.PolicyRepositoryImpl
import com.rate.persistence.repository.PpdPtdTableRepositoryImpl
import com.rate.persistence.repository.ProductRepositoryImpl
import com.rate.persistence.repository.ProposalRepositoryImpl
import com.rate.persistence.repository.QuoteRepositoryImpl
import com.rate.persistence.repository.RateImportRepositoryImpl
import com.rate.persistence.repository.RateMetaRepositoryImpl
import com.rate.persistence.repository.SectionRepositoryImpl
import com.rate.persistence.repository.SessionRepositoryImpl
import com.rate.persistence.repository.SurgicalSublimitRepositoryImpl
import com.rate.persistence.repository.TenureRepositoryImpl
import com.rate.persistence.repository.UserRepositoryImpl
import com.rate.persistence.repository.VaccinationCatalogRepositoryImpl
import com.rate.persistence.repository.VendorRepositoryImpl
import com.rate.persistence.repository.WaitingPeriodRepositoryImpl
// Wave B masters (demographics / partners / underwriting / financial / actuarial) — impls
import com.rate.persistence.repository.AgeBandRepositoryImpl
import com.rate.persistence.repository.AuthorityLevelRepositoryImpl
import com.rate.persistence.repository.BusinessTypeRepositoryImpl
import com.rate.persistence.repository.ChannelRepositoryImpl
import com.rate.persistence.repository.ClaimThresholdRepositoryImpl
import com.rate.persistence.repository.ClientLocationRepositoryImpl
import com.rate.persistence.repository.CostOfCapitalMatrixRepositoryImpl
import com.rate.persistence.repository.DiscountConfigRepositoryImpl
import com.rate.persistence.repository.DiseaseMappingRepositoryImpl
import com.rate.persistence.repository.FamilyRelationRepositoryImpl
import com.rate.persistence.repository.FamilyTypeRepositoryImpl
import com.rate.persistence.repository.GenderRepositoryImpl
import com.rate.persistence.repository.GroupSizeRepositoryImpl
import com.rate.persistence.repository.GroupTypeRepositoryImpl
import com.rate.persistence.repository.IBNRReserveRepositoryImpl
import com.rate.persistence.repository.IndividualLoadingFactorRepositoryImpl
import com.rate.persistence.repository.IndustryTypeRepositoryImpl
import com.rate.persistence.repository.InflationMTFRepositoryImpl
import com.rate.persistence.repository.InsurerRepositoryImpl
import com.rate.persistence.repository.IntermediaryRepositoryImpl
import com.rate.persistence.repository.InvestmentIncomeConfigRepositoryImpl
import com.rate.persistence.repository.MedicalTrendFactorRepositoryImpl
import com.rate.persistence.repository.MemberTypeRepositoryImpl
import com.rate.persistence.repository.PaymentFrequencyRepositoryImpl
import com.rate.persistence.repository.PolicyCostConfigRepositoryImpl
import com.rate.persistence.repository.RatingParameterRepositoryImpl
import com.rate.persistence.repository.ReinsuranceTreatyRepositoryImpl
import com.rate.persistence.repository.RelatedPartyRepositoryImpl
import com.rate.persistence.repository.SalesIntermediaryMappingRepositoryImpl
import com.rate.persistence.repository.SumInsuredTierRepositoryImpl
import com.rate.persistence.repository.TpaRepositoryImpl
import com.rate.persistence.repository.TreatmentCategoryRepositoryImpl
import com.rate.persistence.repository.VariableExpenseConfigRepositoryImpl
// Wave C1 masters (productconfig / rfq) — impls
import com.rate.persistence.repository.AdaptiveCategoryRepositoryImpl
import com.rate.persistence.repository.BenefitTypeRepositoryImpl
import com.rate.persistence.repository.CategoryModelRepositoryImpl
import com.rate.persistence.repository.CategoryRepositoryImpl
import com.rate.persistence.repository.ExperienceRatingRepositoryImpl
import com.rate.persistence.repository.GroupSizeMatrixRepositoryImpl
import com.rate.persistence.repository.ProductAddonConfigRepositoryImpl
import com.rate.persistence.repository.RfqRepositoryImpl
import com.rate.persistence.tx.QuoteIdempotencyTx
import com.rate.sdk.audit.repository.AuditStore
import com.rate.sdk.audit.repository.IdempotencyStore
import com.rate.sdk.audit.repository.SequenceCounter
// Wave B masters — ports
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
import com.rate.sdk.catalog.repository.TpaRepository
import com.rate.sdk.catalog.repository.TreatmentCategoryRepository
import com.rate.sdk.catalog.repository.VariableExpenseConfigRepository
// Wave C1 masters (productconfig / rfq) — ports
import com.rate.sdk.catalog.repository.AdaptiveCategoryRepository
import com.rate.sdk.catalog.repository.BenefitTypeRepository
import com.rate.sdk.catalog.repository.CategoryModelRepository
import com.rate.sdk.catalog.repository.CategoryRepository
import com.rate.sdk.catalog.repository.ExperienceRatingRepository
import com.rate.sdk.catalog.repository.GroupSizeMatrixRepository
import com.rate.sdk.catalog.repository.ProductAddonConfigRepository
import com.rate.sdk.catalog.repository.RfqRepository
import com.rate.sdk.catalog.repository.AddOnRepository
import com.rate.sdk.catalog.repository.AnnexureRepository
import com.rate.sdk.catalog.repository.BenefitScheduleRepository
import com.rate.sdk.catalog.repository.ChronicOpdGridRepository
import com.rate.sdk.catalog.repository.ConsumablesListRepository
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.catalog.repository.CriticalIllnessListRepository
import com.rate.sdk.catalog.repository.DayCareProcedureRepository
import com.rate.sdk.catalog.repository.EligibilityCriteriaRepository
import com.rate.sdk.catalog.repository.GroupGradeRepository
import com.rate.sdk.catalog.repository.GroupProductConfigRepository
import com.rate.sdk.catalog.repository.HealthCheckupPackageRepository
import com.rate.sdk.catalog.repository.LoginAuditRepository
import com.rate.sdk.catalog.repository.MedicalDeviceCatalogRepository
import com.rate.sdk.catalog.repository.PincodeZoneRepository
import com.rate.sdk.catalog.repository.PpdPtdTableRepository
import com.rate.sdk.catalog.repository.ProductRepository
import com.rate.sdk.catalog.repository.SectionRepository
import com.rate.sdk.catalog.repository.SurgicalSublimitRepository
import com.rate.sdk.catalog.repository.TenureRepository
import com.rate.sdk.catalog.repository.UserRepository
import com.rate.sdk.catalog.repository.VaccinationCatalogRepository
import com.rate.sdk.catalog.repository.VendorRepository
import com.rate.sdk.catalog.repository.WaitingPeriodRepository
import com.rate.sdk.ingestion.repository.RateImportRepository
import com.rate.sdk.ingestion.repository.RateMetaRepository
import com.rate.sdk.party.repository.CensusRepository
import com.rate.sdk.party.repository.PartyMemberRepository
import com.rate.sdk.party.repository.PartyRepository
import com.rate.sdk.policy.repository.ClaimRepository
import com.rate.sdk.policy.repository.PolicyRepository
import com.rate.sdk.proposal.repository.ProposalRepository
import com.rate.sdk.proposal.repository.SessionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for the persistence layer — binds the single [MongoClient]/[MongoDatabase], the
 * rate-table cache + the two rate providers, and EVERY core + sdk repository PORT to its
 * Mongo-backed actual. This is the one module the app (server) installs to make all the pure-KMP
 * feature handlers runnable against MongoDB.
 *
 * Provide a [MongoConfig] (e.g. `MongoConfig.fromEnv()`) when installing this module.
 */
fun persistenceModule(config: MongoConfig): Module = module {

    // ── Connection (single instance) ─────────────────────────────────────────
    single { config }
    single { MongoClientProvider(get()) }
    single<MongoClient> { get<MongoClientProvider>().client }
    single<MongoDatabase> { get<MongoClientProvider>().database }

    // ── Plan (rating anchor + admin-CRUD) ────────────────────────────────────
    single { PlanRepositoryImpl(get()) }
    single<PlanRepository> { get<PlanRepositoryImpl>() }

    // ── Rate cache + providers ───────────────────────────────────────────────
    single { RateImportRepositoryImpl(get()) }
    single<RateImportRepository> { get<RateImportRepositoryImpl>() }
    single { RateMetaRepositoryImpl(get()) }
    single<RateMetaRepository> { get<RateMetaRepositoryImpl>() }
    single {
        RateTableCache(
            rows = get<RateImportRepository>(),
            meta = get<RateMetaRepository>(),
        )
    }
    single { MongoRateDataProvider(get()) }
    single<RateDataProvider> { get<MongoRateDataProvider>() }
    single<GroupRateDataProvider> { MongoGroupRateDataProvider(get()) }

    // ── Saved quotes ─────────────────────────────────────────────────────────
    single { QuoteRepositoryImpl(get()) }
    single<QuoteRepository> { get<QuoteRepositoryImpl>() }
    single { QuoteIdempotencyTx(get(), get()) }

    // ── RETAIL catalog config repositories ───────────────────────────────────
    single<ProductRepository> { ProductRepositoryImpl(get()) }
    single<SectionRepository> { SectionRepositoryImpl(get()) }
    single<CoverRepository> { CoverRepositoryImpl(get()) }
    single<CriticalIllnessListRepository> { CriticalIllnessListRepositoryImpl(get()) }
    single<AnnexureRepository> { AnnexureRepositoryImpl(get()) }
    single<AddOnRepository> { AddOnRepositoryImpl(get()) }
    single<TenureRepository> { TenureRepositoryImpl(get()) }
    single<PincodeZoneRepository> { PincodeZoneRepositoryImpl(get()) }

    // ── GROUP catalog config repositories ────────────────────────────────────
    single<GroupProductConfigRepository> { GroupProductConfigRepositoryImpl(get()) }
    single<GroupGradeRepository> { GroupGradeRepositoryImpl(get()) }
    single<BenefitScheduleRepository> { BenefitScheduleRepositoryImpl(get()) }
    single<WaitingPeriodRepository> { WaitingPeriodRepositoryImpl(get()) }
    single<EligibilityCriteriaRepository> { EligibilityCriteriaRepositoryImpl(get()) }
    single<PpdPtdTableRepository> { PpdPtdTableRepositoryImpl(get()) }
    single<DayCareProcedureRepository> { DayCareProcedureRepositoryImpl(get()) }
    single<ConsumablesListRepository> { ConsumablesListRepositoryImpl(get()) }
    single<HealthCheckupPackageRepository> { HealthCheckupPackageRepositoryImpl(get()) }
    single<ChronicOpdGridRepository> { ChronicOpdGridRepositoryImpl(get()) }
    // Structured entities split out of the GROUP EE annexure.
    single<SurgicalSublimitRepository> { SurgicalSublimitRepositoryImpl(get()) }
    single<VaccinationCatalogRepository> { VaccinationCatalogRepositoryImpl(get()) }
    single<MedicalDeviceCatalogRepository> { MedicalDeviceCatalogRepositoryImpl(get()) }

    // ── GLOBAL catalog config repositories (no productLine; shared RETAIL/GROUP) ──
    single<VendorRepository> { VendorRepositoryImpl(get()) }

    // ── Identity / access (console users + append-only login log) ─────────────
    single { UserRepositoryImpl(get()) }
    single<UserRepository> { get<UserRepositoryImpl>() }
    single<LoginAuditRepository> { LoginAuditRepositoryImpl(get()) }

    // ── Party / census ───────────────────────────────────────────────────────
    single<PartyRepository> { PartyRepositoryImpl(get()) }
    single<PartyMemberRepository> { PartyMemberRepositoryImpl(get()) }
    single<CensusRepository> { CensusRepositoryImpl(get()) }

    // ── Proposal / session ───────────────────────────────────────────────────
    single<ProposalRepository> { ProposalRepositoryImpl(get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }

    // ── Policy / claim ───────────────────────────────────────────────────────
    single<PolicyRepository> { PolicyRepositoryImpl(get()) }
    single<ClaimRepository> { ClaimRepositoryImpl(get()) }

    // ── Audit / idempotency / sequence ───────────────────────────────────────
    single { SeqCounter(get()) }
    single<SequenceCounter> { get<SeqCounter>() }
    single<AuditStore> { AuditStoreImpl(get()) }
    single<IdempotencyStore> { IdempotencyStoreImpl(get()) }

    // Wave B masters (demographics / partners / underwriting / financial / actuarial)
    single<AgeBandRepository> { AgeBandRepositoryImpl(get()) }
    single<GenderRepository> { GenderRepositoryImpl(get()) }
    single<PaymentFrequencyRepository> { PaymentFrequencyRepositoryImpl(get()) }
    single<MemberTypeRepository> { MemberTypeRepositoryImpl(get()) }
    single<FamilyRelationRepository> { FamilyRelationRepositoryImpl(get()) }
    single<GroupTypeRepository> { GroupTypeRepositoryImpl(get()) }
    single<GroupSizeRepository> { GroupSizeRepositoryImpl(get()) }
    single<FamilyTypeRepository> { FamilyTypeRepositoryImpl(get()) }
    single<IndustryTypeRepository> { IndustryTypeRepositoryImpl(get()) }
    single<InsurerRepository> { InsurerRepositoryImpl(get()) }
    single<ChannelRepository> { ChannelRepositoryImpl(get()) }
    single<IntermediaryRepository> { IntermediaryRepositoryImpl(get()) }
    single<TpaRepository> { TpaRepositoryImpl(get()) }
    single<SalesIntermediaryMappingRepository> { SalesIntermediaryMappingRepositoryImpl(get()) }
    single<ClientLocationRepository> { ClientLocationRepositoryImpl(get()) }
    single<BusinessTypeRepository> { BusinessTypeRepositoryImpl(get()) }
    single<RelatedPartyRepository> { RelatedPartyRepositoryImpl(get()) }
    single<TreatmentCategoryRepository> { TreatmentCategoryRepositoryImpl(get()) }
    single<DiseaseMappingRepository> { DiseaseMappingRepositoryImpl(get()) }
    single<RatingParameterRepository> { RatingParameterRepositoryImpl(get()) }
    single<DiscountConfigRepository> { DiscountConfigRepositoryImpl(get()) }
    single<VariableExpenseConfigRepository> { VariableExpenseConfigRepositoryImpl(get()) }
    single<PolicyCostConfigRepository> { PolicyCostConfigRepositoryImpl(get()) }
    single<InvestmentIncomeConfigRepository> { InvestmentIncomeConfigRepositoryImpl(get()) }
    single<SumInsuredTierRepository> { SumInsuredTierRepositoryImpl(get()) }
    single<ReinsuranceTreatyRepository> { ReinsuranceTreatyRepositoryImpl(get()) }
    single<CostOfCapitalMatrixRepository> { CostOfCapitalMatrixRepositoryImpl(get()) }
    single<AuthorityLevelRepository> { AuthorityLevelRepositoryImpl(get()) }
    single<MedicalTrendFactorRepository> { MedicalTrendFactorRepositoryImpl(get()) }
    single<InflationMTFRepository> { InflationMTFRepositoryImpl(get()) }
    single<ClaimThresholdRepository> { ClaimThresholdRepositoryImpl(get()) }
    single<IndividualLoadingFactorRepository> { IndividualLoadingFactorRepositoryImpl(get()) }
    single<IBNRReserveRepository> { IBNRReserveRepositoryImpl(get()) }

    // Wave C1 masters (productconfig / rfq)
    single<ProductAddonConfigRepository> { ProductAddonConfigRepositoryImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<BenefitTypeRepository> { BenefitTypeRepositoryImpl(get()) }
    single<CategoryModelRepository> { CategoryModelRepositoryImpl(get()) }
    single<AdaptiveCategoryRepository> { AdaptiveCategoryRepositoryImpl(get()) }
    single<RfqRepository> { RfqRepositoryImpl(get()) }
    single<ExperienceRatingRepository> { ExperienceRatingRepositoryImpl(get()) }
    single<GroupSizeMatrixRepository> { GroupSizeMatrixRepositoryImpl(get()) }

    // ── JVM Excel rate importer (POI) ────────────────────────────────────────
    single { ExcelRateImporter() }
}
