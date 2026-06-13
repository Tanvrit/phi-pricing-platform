package com.rate.sdk.ingestion.di

import com.rate.core.rating.ports.PlanRepository
import com.rate.sdk.catalog.repository.AnnexureRepository
import com.rate.sdk.catalog.repository.BenefitScheduleRepository
import com.rate.sdk.catalog.repository.GroupGradeRepository
import com.rate.sdk.catalog.repository.MedicalDeviceCatalogRepository
import com.rate.sdk.catalog.repository.SurgicalSublimitRepository
import com.rate.sdk.catalog.repository.TenureRepository
import com.rate.sdk.catalog.repository.VaccinationCatalogRepository
import com.rate.sdk.catalog.repository.VendorRepository
import com.rate.sdk.catalog.repository.ChronicOpdGridRepository
import com.rate.sdk.catalog.repository.ConsumablesListRepository
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.catalog.repository.CriticalIllnessListRepository
import com.rate.sdk.catalog.repository.DayCareProcedureRepository
import com.rate.sdk.catalog.repository.EligibilityCriteriaRepository
import com.rate.sdk.catalog.repository.GroupProductConfigRepository
import com.rate.sdk.catalog.repository.HealthCheckupPackageRepository
import com.rate.sdk.catalog.repository.PpdPtdTableRepository
import com.rate.sdk.catalog.repository.SectionRepository
import com.rate.sdk.catalog.repository.WaitingPeriodRepository
import com.rate.sdk.ingestion.event.IngestionEventSink
import com.rate.sdk.ingestion.handler.CatalogSeeder
import com.rate.sdk.ingestion.handler.CsvCatalogParser
import com.rate.sdk.ingestion.handler.RateImportHandler
import com.rate.sdk.ingestion.repository.RateImportRepository
import com.rate.sdk.ingestion.repository.RateMetaRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for sdk-ingestion.
 *
 * Binds only what is constructible in pure-KMP commonMain:
 *  - [CsvCatalogParser]   → stateless pure parser.
 *  - [CatalogSeeder]      → wired from the sdk-catalog repository PORTs + the parser + event sink.
 *  - [RateImportHandler]  → wired from the ingestion PORTs ([RateImportRepository],
 *                           [RateMetaRepository]) + event sink.
 *
 * The repository PORT actuals (Mongo-backed) and a real [IngestionEventSink] (audit/bus) are bound
 * by `server-persistence`/`server` on the app layer; the [IngestionEventSink.NOOP] default keeps
 * the handlers constructible offline / in tests. The client uses
 * [com.rate.sdk.ingestion.network.IngestionApi] instead of these handlers.
 */
fun ingestionModule(): Module = module {
    single { CsvCatalogParser() }

    single<IngestionEventSink> { IngestionEventSink.NOOP }

    single {
        RateImportHandler(
            rows = get<RateImportRepository>(),
            meta = get<RateMetaRepository>(),
            sink = get<IngestionEventSink>(),
        )
    }

    single {
        CatalogSeeder(
            sections = get<SectionRepository>(),
            covers = get<CoverRepository>(),
            criticalIllnessLists = get<CriticalIllnessListRepository>(),
            annexures = get<AnnexureRepository>(),
            groupProducts = get<GroupProductConfigRepository>(),
            benefitSchedules = get<BenefitScheduleRepository>(),
            waitingPeriods = get<WaitingPeriodRepository>(),
            eligibility = get<EligibilityCriteriaRepository>(),
            ppdPtdTables = get<PpdPtdTableRepository>(),
            dayCare = get<DayCareProcedureRepository>(),
            consumables = get<ConsumablesListRepository>(),
            healthCheckups = get<HealthCheckupPackageRepository>(),
            chronicOpd = get<ChronicOpdGridRepository>(),
            plans = get<PlanRepository>(),
            groupGrades = get<GroupGradeRepository>(),
            tenures = get<TenureRepository>(),
            vendors = get<VendorRepository>(),
            surgicalSublimits = get<SurgicalSublimitRepository>(),
            vaccinationCatalogs = get<VaccinationCatalogRepository>(),
            medicalDeviceCatalogs = get<MedicalDeviceCatalogRepository>(),
            parser = get(),
            sink = get<IngestionEventSink>(),
        )
    }
}
