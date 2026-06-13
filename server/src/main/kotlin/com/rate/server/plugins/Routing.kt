package com.rate.server.plugins

import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.model.Plan
import com.rate.persistence.importer.ExcelRateImporter
import com.rate.persistence.rating.RateTableCache
import com.rate.persistence.repository.PlanRepositoryImpl
import com.rate.server.audit.ServerAuditService
import com.rate.server.email.EmailSender
import com.rate.server.email.FileSystemEmailSender
import com.rate.sdk.ingestion.handler.CatalogSeeder
import com.rate.server.routes.adminCrudRoutes
import com.rate.server.routes.adminRoutes
import com.rate.server.routes.auditRoutes
import com.rate.server.routes.authRoutes
import com.rate.server.routes.loginAuditRoutes
import com.rate.server.routes.buyOnlineRoutes
import com.rate.server.routes.catalogRoutes
import com.rate.server.routes.devSeedRoutes
import com.rate.server.routes.healthRoutes
import com.rate.server.routes.importRoutes
import com.rate.server.routes.metricsRoutes
import com.rate.server.routes.policyRoutes
import com.rate.server.routes.quoteRoutes
import com.rate.server.routes.seedRatesRoutes
import com.rate.server.routes.vendorRoutes
import com.rate.core.auth.otp.OtpStore
import com.rate.core.auth.session.SessionStore
import com.rate.core.auth.token.TokenSigner
import com.rate.server.security.AppSecrets
import com.rate.server.security.HmacIdempotencyHasher
import com.rate.server.security.PasswordHasher
import com.rate.sdk.audit.repository.IdempotencyStore
import com.rate.sdk.catalog.handler.CatalogHandler
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Tenure
import com.rate.sdk.catalog.model.User
import com.rate.sdk.catalog.model.group.BenefitSchedule
import com.rate.sdk.catalog.model.group.ChronicOpdGrid
import com.rate.sdk.catalog.model.group.ConsumablesList
import com.rate.sdk.catalog.model.group.DayCareProcedure
import com.rate.sdk.catalog.model.group.EligibilityCriteria
import com.rate.sdk.catalog.model.group.GroupGrade
import com.rate.sdk.catalog.model.group.GroupProductConfig
import com.rate.sdk.catalog.model.group.HealthCheckupPackage
import com.rate.sdk.catalog.model.group.PpdPtdTable
import com.rate.sdk.catalog.model.group.WaitingPeriod
// Wave B masters — models
import com.rate.sdk.catalog.model.demographics.AgeBand
import com.rate.sdk.catalog.model.demographics.FamilyRelation
import com.rate.sdk.catalog.model.demographics.Gender
import com.rate.sdk.catalog.model.demographics.MemberType
import com.rate.sdk.catalog.model.demographics.PaymentFrequency
import com.rate.sdk.catalog.model.grouping.FamilyType
import com.rate.sdk.catalog.model.grouping.GroupSize
import com.rate.sdk.catalog.model.grouping.GroupType
import com.rate.sdk.catalog.model.grouping.IndustryType
import com.rate.sdk.catalog.model.partnerorg.Channel
import com.rate.sdk.catalog.model.partnerorg.Insurer
import com.rate.sdk.catalog.model.partnerorg.Intermediary
import com.rate.sdk.catalog.model.partnerorg.Tpa
import com.rate.sdk.catalog.model.partnerlink.BusinessType
import com.rate.sdk.catalog.model.partnerlink.ClientLocation
import com.rate.sdk.catalog.model.partnerlink.RelatedParty
import com.rate.sdk.catalog.model.partnerlink.SalesIntermediaryMapping
import com.rate.sdk.catalog.model.underwriting.DiseaseMapping
import com.rate.sdk.catalog.model.underwriting.RatingParameter
import com.rate.sdk.catalog.model.underwriting.TreatmentCategory
import com.rate.sdk.catalog.model.financialcore.DiscountConfig
import com.rate.sdk.catalog.model.financialcore.InvestmentIncomeConfig
import com.rate.sdk.catalog.model.financialcore.PolicyCostConfig
import com.rate.sdk.catalog.model.financialcore.VariableExpenseConfig
import com.rate.sdk.catalog.model.financialcapital.CostOfCapitalMatrix
import com.rate.sdk.catalog.model.financialcapital.ReinsuranceTreaty
import com.rate.sdk.catalog.model.financialcapital.SumInsuredTier
import com.rate.sdk.catalog.model.actuarial.AuthorityLevel
import com.rate.sdk.catalog.model.actuarial.ClaimThreshold
import com.rate.sdk.catalog.model.actuarial.IBNRReserve
import com.rate.sdk.catalog.model.actuarial.IndividualLoadingFactor
import com.rate.sdk.catalog.model.actuarial.InflationMTF
import com.rate.sdk.catalog.model.actuarial.MedicalTrendFactor
// Wave C1 masters — models
import com.rate.sdk.catalog.model.productconfig.AdaptiveCategory
import com.rate.sdk.catalog.model.productconfig.BenefitType
import com.rate.sdk.catalog.model.productconfig.Category
import com.rate.sdk.catalog.model.productconfig.CategoryModel
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig
import com.rate.sdk.catalog.model.rfq.ExperienceRating
import com.rate.sdk.catalog.model.rfq.GroupSizeMatrix
import com.rate.sdk.catalog.model.rfq.Rfq
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
// Wave C1 masters — ports
import com.rate.sdk.catalog.repository.AdaptiveCategoryRepository
import com.rate.sdk.catalog.repository.BenefitTypeRepository
import com.rate.sdk.catalog.repository.CategoryModelRepository
import com.rate.sdk.catalog.repository.CategoryRepository
import com.rate.sdk.catalog.repository.ExperienceRatingRepository
import com.rate.sdk.catalog.repository.GroupSizeMatrixRepository
import com.rate.sdk.catalog.repository.ProductAddonConfigRepository
import com.rate.sdk.catalog.repository.RfqRepository
import com.rate.server.routes.seedMastersRoutes
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
import com.rate.sdk.ingestion.handler.RateImportHandler
import com.rate.sdk.policy.handler.RenewalEngine
import com.rate.sdk.policy.repository.ClaimRepository
import com.rate.sdk.policy.repository.PolicyRepository
import com.rate.sdk.proposal.handler.EligibilityHandler
import com.rate.sdk.proposal.handler.KycHandler
import com.rate.sdk.proposal.handler.OtpHandler
import com.rate.sdk.proposal.handler.PremiumHandler
import com.rate.sdk.proposal.handler.ProposalHandler
import com.rate.sdk.proposal.repository.SessionRepository
import com.rate.sdk.quoting.handler.QuoteHandler
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import java.io.File

/**
 * Mounts EVERY route group. The big structural win over the monolith: the per-entity admin route
 * files (PlanRoutes/CoverRoutes/DiscountRoutes/…) collapse into a single generic
 * [adminCrudRoutes] call per config entity, all sharing identical list/get/create/update/delete/
 * restore/publish/import/export semantics + auditing + scope-gating.
 *
 * Everything is resolved from the Koin graph (serverModule + persistenceModule + the sdk feature
 * modules) so the route layer holds no construction logic — it is pure wiring.
 */
fun Application.configureRouting(startedAtIso: String) {
    val audit: ServerAuditService = get()
    val secrets: AppSecrets = get()
    val hasher: HmacIdempotencyHasher = get()
    val idempotency: IdempotencyStore = get()
    val emailSender: EmailSender = get()

    val outboxDir: File = (emailSender as? FileSystemEmailSender)?.outboxDir
        ?: File(System.getProperty("user.home"), ".aegis/outbox")

    routing {
        // ── Infra / diagnostics ──────────────────────────────────────────────
        healthRoutes()
        metricsRoutes()
        auditRoutes(audit)
        adminRoutes(outboxDir, audit, startedAtIso)

        // ── Auth (console email/password login, refresh, logout, password reset) ──
        authRoutes(
            users = this@configureRouting.get<UserRepository>(),
            loginAudits = this@configureRouting.get<LoginAuditRepository>(),
            tokenSigner = this@configureRouting.get<TokenSigner>(),
            sessions = this@configureRouting.get<SessionStore>(),
            otpStore = this@configureRouting.get<OtpStore>(),
            emailSender = emailSender,
            passwordHasher = this@configureRouting.get<PasswordHasher>(),
            secrets = secrets,
            audit = audit,
        )

        // ── Quoting (backward-compatible /api/quotes/*) ──────────────────────
        quoteRoutes(this@configureRouting.get<QuoteHandler>(), audit, idempotency, hasher)

        // ── Buy-online customer journey (/api/buy-online/*) ──────────────────
        buyOnlineRoutes(
            otp = this@configureRouting.get<OtpHandler>(),
            eligibility = this@configureRouting.get<EligibilityHandler>(),
            premium = this@configureRouting.get<PremiumHandler>(),
            kyc = this@configureRouting.get<KycHandler>(),
            proposal = this@configureRouting.get<ProposalHandler>(),
            sessions = this@configureRouting.get<SessionRepository>(),
            audit = audit,
            idempotency = idempotency,
            hasher = hasher,
            emailSender = emailSender,
            secrets = secrets,
        )

        // ── Policy lifecycle / renewal / claims ──────────────────────────────
        policyRoutes(
            policies = this@configureRouting.get<PolicyRepository>(),
            claims = this@configureRouting.get<ClaimRepository>(),
            renewal = this@configureRouting.get<RenewalEngine>(),
        )

        // ── Catalog reference data + zone lookup ─────────────────────────────
        catalogRoutes(this@configureRouting.get<CatalogHandler>())

        // ── Rate import (Excel via persistence POI + ingestion handler) ──────
        importRoutes(
            importer = this@configureRouting.get<ExcelRateImporter>(),
            rateImport = this@configureRouting.get<RateImportHandler>(),
            rateCache = this@configureRouting.get<RateTableCache>(),
            audit = audit,
            idempotencyHasher = hasher,
        )

        // ── Generic admin CRUD for EVERY config entity ───────────────────────
        // `scopeEntity` maps each entity onto a core-auth role-bundle scope token where its audit
        // name is more specific than the coarse RBAC vocabulary (product→plan, CI-list/annexure→
        // cover), so a BUSINESS operator's default bundle grants access (ADMIN `*` is unaffected).
        //
        // RETAIL catalog
        adminCrudRoutes("/api/admin/plans", "plan", Plan.serializer(), this@configureRouting.get<PlanRepositoryImpl>(), audit, scopeEntity = "plan")
        adminCrudRoutes("/api/admin/products", "product", Product.serializer(), this@configureRouting.get<ProductRepository>(), audit, scopeEntity = "plan")
        adminCrudRoutes("/api/admin/sections", "section", Section.serializer(), this@configureRouting.get<SectionRepository>(), audit)
        adminCrudRoutes("/api/admin/covers", "cover", Cover.serializer(), this@configureRouting.get<CoverRepository>(), audit)
        // Route path MUST equal the operator-console descriptor id (resource segment in
        // /api/admin/{id}/…) — descriptor id is "ci-lists", not "critical-illness-lists".
        adminCrudRoutes("/api/admin/ci-lists", "criticalIllnessList", CriticalIllnessList.serializer(), this@configureRouting.get<CriticalIllnessListRepository>(), audit, scopeEntity = "cover")
        adminCrudRoutes("/api/admin/annexures", "annexure", Annexure.serializer(), this@configureRouting.get<AnnexureRepository>(), audit, scopeEntity = "cover")
        adminCrudRoutes("/api/admin/addons", "addon", AddOn.serializer(), this@configureRouting.get<AddOnRepository>(), audit)
        adminCrudRoutes("/api/admin/tenures", "tenure", Tenure.serializer(), this@configureRouting.get<TenureRepository>(), audit)
        adminCrudRoutes("/api/admin/pincode-zones", "pincode", PincodeZone.serializer(), this@configureRouting.get<PincodeZoneRepository>(), audit)

        // GROUP catalog
        adminCrudRoutes("/api/admin/group-products", "group", GroupProductConfig.serializer(), this@configureRouting.get<GroupProductConfigRepository>(), audit)
        adminCrudRoutes("/api/admin/group-grades", "group", GroupGrade.serializer(), this@configureRouting.get<GroupGradeRepository>(), audit)
        adminCrudRoutes("/api/admin/benefit-schedules", "group", BenefitSchedule.serializer(), this@configureRouting.get<BenefitScheduleRepository>(), audit)
        adminCrudRoutes("/api/admin/waiting-periods", "group", WaitingPeriod.serializer(), this@configureRouting.get<WaitingPeriodRepository>(), audit)
        // Route paths MUST equal the operator-console descriptor ids (resource segment in
        // /api/admin/{id}/…). Descriptor ids are the short forms below, not the long collection
        // names — keeping them in sync stops every group config screen 404ing.
        adminCrudRoutes("/api/admin/eligibility", "group", EligibilityCriteria.serializer(), this@configureRouting.get<EligibilityCriteriaRepository>(), audit)
        adminCrudRoutes("/api/admin/ppd-ptd", "group", PpdPtdTable.serializer(), this@configureRouting.get<PpdPtdTableRepository>(), audit)
        adminCrudRoutes("/api/admin/day-care", "group", DayCareProcedure.serializer(), this@configureRouting.get<DayCareProcedureRepository>(), audit)
        adminCrudRoutes("/api/admin/consumables", "group", ConsumablesList.serializer(), this@configureRouting.get<ConsumablesListRepository>(), audit)
        adminCrudRoutes("/api/admin/health-checkup", "group", HealthCheckupPackage.serializer(), this@configureRouting.get<HealthCheckupPackageRepository>(), audit)
        adminCrudRoutes("/api/admin/chronic-opd", "group", ChronicOpdGrid.serializer(), this@configureRouting.get<ChronicOpdGridRepository>(), audit)

        // GLOBAL vendor registry + GROUP annexure-derived catalogs (surgical sublimits, vaccination
        // lists, medical-device lists) — admin-CRUD via the same generic factory, grouped in their
        // own route file. Paths equal the operator-console descriptor ids (vendors,
        // surgical-sublimits, vaccination-catalogs, medical-device-catalogs).
        vendorRoutes(
            vendors = this@configureRouting.get<VendorRepository>(),
            surgicalSublimits = this@configureRouting.get<SurgicalSublimitRepository>(),
            vaccinationCatalogs = this@configureRouting.get<VaccinationCatalogRepository>(),
            medicalDeviceCatalogs = this@configureRouting.get<MedicalDeviceCatalogRepository>(),
            audit = audit,
        )

        // IDENTITY / ACCESS — console users (admin-CRUD) + append-only login-audit log (read-only).
        // Users are scoped under the `rbac` vocabulary; only ADMIN/OWNER (`*`) hold rbac.manage, so a
        // plain BUSINESS operator cannot list/edit users. The login-audit log rides a dedicated
        // read-only route (append-only security log; never authored via CRUD) gated by `audit.read`.
        adminCrudRoutes("/api/admin/users", "user", User.serializer(), this@configureRouting.get<UserRepository>(), audit, scopeEntity = "rbac")
        loginAuditRoutes(this@configureRouting.get<LoginAuditRepository>())

        // Wave B masters — demographics / grouping / partners / underwriting / financial / actuarial.
        // Each is a metadata-driven ConfigEntity exposed through the same generic admin-CRUD factory;
        // the route path equals its operator-console descriptor id.
        adminCrudRoutes("/api/admin/age-bands", "ageBand", AgeBand.serializer(), this@configureRouting.get<AgeBandRepository>(), audit)
        adminCrudRoutes("/api/admin/genders", "gender", Gender.serializer(), this@configureRouting.get<GenderRepository>(), audit)
        adminCrudRoutes("/api/admin/payment-frequencies", "paymentFrequency", PaymentFrequency.serializer(), this@configureRouting.get<PaymentFrequencyRepository>(), audit)
        adminCrudRoutes("/api/admin/member-types", "memberType", MemberType.serializer(), this@configureRouting.get<MemberTypeRepository>(), audit)
        adminCrudRoutes("/api/admin/family-relations", "familyRelation", FamilyRelation.serializer(), this@configureRouting.get<FamilyRelationRepository>(), audit, scopeEntity = "member-types")
        adminCrudRoutes("/api/admin/group-types", "groupType", GroupType.serializer(), this@configureRouting.get<GroupTypeRepository>(), audit, scopeEntity = "group")
        adminCrudRoutes("/api/admin/group-sizes", "groupSize", GroupSize.serializer(), this@configureRouting.get<GroupSizeRepository>(), audit, scopeEntity = "group")
        adminCrudRoutes("/api/admin/family-types", "familyType", FamilyType.serializer(), this@configureRouting.get<FamilyTypeRepository>(), audit, scopeEntity = "group")
        adminCrudRoutes("/api/admin/industry-types", "industryType", IndustryType.serializer(), this@configureRouting.get<IndustryTypeRepository>(), audit, scopeEntity = "group")
        adminCrudRoutes("/api/admin/insurers", "Insurer", Insurer.serializer(), this@configureRouting.get<InsurerRepository>(), audit)
        adminCrudRoutes("/api/admin/channels", "Channel", Channel.serializer(), this@configureRouting.get<ChannelRepository>(), audit)
        adminCrudRoutes("/api/admin/intermediaries", "Intermediary", Intermediary.serializer(), this@configureRouting.get<IntermediaryRepository>(), audit)
        adminCrudRoutes("/api/admin/tpas", "TPA", Tpa.serializer(), this@configureRouting.get<TpaRepository>(), audit)
        adminCrudRoutes("/api/admin/sales-mappings", "SalesIntermediaryMapping", SalesIntermediaryMapping.serializer(), this@configureRouting.get<SalesIntermediaryMappingRepository>(), audit)
        adminCrudRoutes("/api/admin/client-locations", "ClientLocation", ClientLocation.serializer(), this@configureRouting.get<ClientLocationRepository>(), audit)
        adminCrudRoutes("/api/admin/business-types", "BusinessType", BusinessType.serializer(), this@configureRouting.get<BusinessTypeRepository>(), audit)
        adminCrudRoutes("/api/admin/related-parties", "RelatedParty", RelatedParty.serializer(), this@configureRouting.get<RelatedPartyRepository>(), audit)
        adminCrudRoutes("/api/admin/treatment-categories", "treatmentCategory", TreatmentCategory.serializer(), this@configureRouting.get<TreatmentCategoryRepository>(), audit)
        adminCrudRoutes("/api/admin/disease-mappings", "diseaseMapping", DiseaseMapping.serializer(), this@configureRouting.get<DiseaseMappingRepository>(), audit)
        adminCrudRoutes("/api/admin/rating-parameters", "ratingParameter", RatingParameter.serializer(), this@configureRouting.get<RatingParameterRepository>(), audit)
        adminCrudRoutes("/api/admin/discount-configs", "DiscountConfig", DiscountConfig.serializer(), this@configureRouting.get<DiscountConfigRepository>(), audit)
        adminCrudRoutes("/api/admin/variable-expenses", "VariableExpenseConfig", VariableExpenseConfig.serializer(), this@configureRouting.get<VariableExpenseConfigRepository>(), audit)
        adminCrudRoutes("/api/admin/policy-costs", "PolicyCostConfig", PolicyCostConfig.serializer(), this@configureRouting.get<PolicyCostConfigRepository>(), audit)
        adminCrudRoutes("/api/admin/investment-income", "InvestmentIncomeConfig", InvestmentIncomeConfig.serializer(), this@configureRouting.get<InvestmentIncomeConfigRepository>(), audit)
        adminCrudRoutes("/api/admin/sum-insured-tiers", "SumInsuredTier", SumInsuredTier.serializer(), this@configureRouting.get<SumInsuredTierRepository>(), audit)
        adminCrudRoutes("/api/admin/reinsurance-treaties", "ReinsuranceTreaty", ReinsuranceTreaty.serializer(), this@configureRouting.get<ReinsuranceTreatyRepository>(), audit)
        adminCrudRoutes("/api/admin/cost-of-capital", "CostOfCapitalMatrix", CostOfCapitalMatrix.serializer(), this@configureRouting.get<CostOfCapitalMatrixRepository>(), audit)
        adminCrudRoutes("/api/admin/authority-levels", "authorityLevel", AuthorityLevel.serializer(), this@configureRouting.get<AuthorityLevelRepository>(), audit)
        adminCrudRoutes("/api/admin/medical-trend-factors", "medicalTrendFactor", MedicalTrendFactor.serializer(), this@configureRouting.get<MedicalTrendFactorRepository>(), audit)
        adminCrudRoutes("/api/admin/inflation-factors", "inflationFactor", InflationMTF.serializer(), this@configureRouting.get<InflationMTFRepository>(), audit)
        adminCrudRoutes("/api/admin/claim-thresholds", "claimThreshold", ClaimThreshold.serializer(), this@configureRouting.get<ClaimThresholdRepository>(), audit)
        adminCrudRoutes("/api/admin/loading-factors", "loadingFactor", IndividualLoadingFactor.serializer(), this@configureRouting.get<IndividualLoadingFactorRepository>(), audit)
        adminCrudRoutes("/api/admin/ibnr-reserves", "ibnrReserve", IBNRReserve.serializer(), this@configureRouting.get<IBNRReserveRepository>(), audit)

        // Wave C1 masters — product-config (CONFIGURATION hub) + RFQ sales pipeline (SALES hub).
        // Each is a metadata-driven ConfigEntity exposed through the same generic admin-CRUD factory;
        // the route path equals its operator-console descriptor id.
        adminCrudRoutes("/api/admin/product-addon-configs", "productAddonConfig", ProductAddonConfig.serializer(), this@configureRouting.get<ProductAddonConfigRepository>(), audit)
        adminCrudRoutes("/api/admin/categories", "category", Category.serializer(), this@configureRouting.get<CategoryRepository>(), audit)
        adminCrudRoutes("/api/admin/benefit-types", "benefitType", BenefitType.serializer(), this@configureRouting.get<BenefitTypeRepository>(), audit)
        adminCrudRoutes("/api/admin/category-models", "categoryModel", CategoryModel.serializer(), this@configureRouting.get<CategoryModelRepository>(), audit)
        adminCrudRoutes("/api/admin/adaptive-categories", "adaptiveCategory", AdaptiveCategory.serializer(), this@configureRouting.get<AdaptiveCategoryRepository>(), audit)
        adminCrudRoutes("/api/admin/rfqs", "Rfq", Rfq.serializer(), this@configureRouting.get<RfqRepository>(), audit)
        adminCrudRoutes("/api/admin/experience-ratings", "ExperienceRating", ExperienceRating.serializer(), this@configureRouting.get<ExperienceRatingRepository>(), audit)
        adminCrudRoutes("/api/admin/group-size-matrices", "GroupSizeMatrix", GroupSizeMatrix.serializer(), this@configureRouting.get<GroupSizeMatrixRepository>(), audit)

        // DEV-ONLY: one-time CSV catalog seed from /data (mounted only under AEGIS_DEV_PROFILE).
        if (System.getenv("AEGIS_DEV_PROFILE")?.equals("true", ignoreCase = true) == true) {
            devSeedRoutes(this@configureRouting.get<CatalogSeeder>())
            // Synthetic retail rate scaffold so the Rate Calculator + /api/quotes/calculate price
            // NON-ZERO in dev (drives the same ingestion path /api/import uses, then reloads the cache).
            seedRatesRoutes(
                plansRepo = this@configureRouting.get<PlanRepository>(),
                coversRepo = this@configureRouting.get<CoverRepository>(),
                rateImport = this@configureRouting.get<RateImportHandler>(),
                rateCache = this@configureRouting.get<RateTableCache>(),
            )
            seedMastersRoutes(
                ageBands = this@configureRouting.get<AgeBandRepository>(),
                genders = this@configureRouting.get<GenderRepository>(),
                paymentFrequencies = this@configureRouting.get<PaymentFrequencyRepository>(),
                memberTypes = this@configureRouting.get<MemberTypeRepository>(),
                familyRelations = this@configureRouting.get<FamilyRelationRepository>(),
                groupTypes = this@configureRouting.get<GroupTypeRepository>(),
                groupSizes = this@configureRouting.get<GroupSizeRepository>(),
                familyTypes = this@configureRouting.get<FamilyTypeRepository>(),
                industryTypes = this@configureRouting.get<IndustryTypeRepository>(),
                insurers = this@configureRouting.get<InsurerRepository>(),
                channels = this@configureRouting.get<ChannelRepository>(),
                intermediaries = this@configureRouting.get<IntermediaryRepository>(),
                tpas = this@configureRouting.get<TpaRepository>(),
                salesMappings = this@configureRouting.get<SalesIntermediaryMappingRepository>(),
                clientLocations = this@configureRouting.get<ClientLocationRepository>(),
                businessTypes = this@configureRouting.get<BusinessTypeRepository>(),
                relatedParties = this@configureRouting.get<RelatedPartyRepository>(),
                treatmentCategories = this@configureRouting.get<TreatmentCategoryRepository>(),
                diseaseMappings = this@configureRouting.get<DiseaseMappingRepository>(),
                ratingParameters = this@configureRouting.get<RatingParameterRepository>(),
                discountConfigs = this@configureRouting.get<DiscountConfigRepository>(),
                variableExpenses = this@configureRouting.get<VariableExpenseConfigRepository>(),
                policyCosts = this@configureRouting.get<PolicyCostConfigRepository>(),
                investmentIncome = this@configureRouting.get<InvestmentIncomeConfigRepository>(),
                sumInsuredTiers = this@configureRouting.get<SumInsuredTierRepository>(),
                reinsuranceTreaties = this@configureRouting.get<ReinsuranceTreatyRepository>(),
                costOfCapital = this@configureRouting.get<CostOfCapitalMatrixRepository>(),
                authorityLevels = this@configureRouting.get<AuthorityLevelRepository>(),
                medicalTrendFactors = this@configureRouting.get<MedicalTrendFactorRepository>(),
                inflationFactors = this@configureRouting.get<InflationMTFRepository>(),
                claimThresholds = this@configureRouting.get<ClaimThresholdRepository>(),
                loadingFactors = this@configureRouting.get<IndividualLoadingFactorRepository>(),
                ibnrReserves = this@configureRouting.get<IBNRReserveRepository>(),
                productAddonConfigs = this@configureRouting.get<ProductAddonConfigRepository>(),
                categories = this@configureRouting.get<CategoryRepository>(),
                benefitTypes = this@configureRouting.get<BenefitTypeRepository>(),
                categoryModels = this@configureRouting.get<CategoryModelRepository>(),
                adaptiveCategories = this@configureRouting.get<AdaptiveCategoryRepository>(),
                rfqs = this@configureRouting.get<RfqRepository>(),
                experienceRatings = this@configureRouting.get<ExperienceRatingRepository>(),
                groupSizeMatrices = this@configureRouting.get<GroupSizeMatrixRepository>(),
            )
        }
    }
}
