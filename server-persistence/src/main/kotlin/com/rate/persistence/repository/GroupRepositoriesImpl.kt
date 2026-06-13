package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
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
import com.rate.sdk.catalog.repository.BenefitScheduleRepository
import com.rate.sdk.catalog.repository.ChronicOpdGridRepository
import com.rate.sdk.catalog.repository.ConsumablesListRepository
import com.rate.sdk.catalog.repository.DayCareProcedureRepository
import com.rate.sdk.catalog.repository.EligibilityCriteriaRepository
import com.rate.sdk.catalog.repository.GroupGradeRepository
import com.rate.sdk.catalog.repository.GroupProductConfigRepository
import com.rate.sdk.catalog.repository.HealthCheckupPackageRepository
import com.rate.sdk.catalog.repository.PpdPtdTableRepository
import com.rate.sdk.catalog.repository.WaitingPeriodRepository

/**
 * Mongo actuals for the GROUP catalog admin-CRUD PORTS. Identical uniform CRUD as the RETAIL
 * repos — the ProductLine discriminator on the entities keeps GROUP a facet of the one catalog.
 */

class GroupProductConfigRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<GroupProductConfig>(
        db, CollectionNames.GROUP_PRODUCT_CONFIGS, GroupProductConfig::class.java, "GroupProductConfig",
    ),
    GroupProductConfigRepository

class GroupGradeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<GroupGrade>(
        db, CollectionNames.GROUP_GRADES, GroupGrade::class.java, "GroupGrade",
    ),
    GroupGradeRepository

class BenefitScheduleRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<BenefitSchedule>(
        db, CollectionNames.BENEFIT_SCHEDULES, BenefitSchedule::class.java, "BenefitSchedule",
    ),
    BenefitScheduleRepository

class WaitingPeriodRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<WaitingPeriod>(
        db, CollectionNames.WAITING_PERIODS, WaitingPeriod::class.java, "WaitingPeriod",
    ),
    WaitingPeriodRepository

class EligibilityCriteriaRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<EligibilityCriteria>(
        db, CollectionNames.ELIGIBILITY_CRITERIA, EligibilityCriteria::class.java, "EligibilityCriteria",
    ),
    EligibilityCriteriaRepository

class PpdPtdTableRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<PpdPtdTable>(
        db, CollectionNames.PPD_PTD_TABLES, PpdPtdTable::class.java, "PpdPtdTable",
    ),
    PpdPtdTableRepository

class DayCareProcedureRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<DayCareProcedure>(
        db, CollectionNames.DAY_CARE_PROCEDURES, DayCareProcedure::class.java, "DayCareProcedure",
    ),
    DayCareProcedureRepository

class ConsumablesListRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ConsumablesList>(
        db, CollectionNames.CONSUMABLES_LISTS, ConsumablesList::class.java, "ConsumablesList",
    ),
    ConsumablesListRepository

class HealthCheckupPackageRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<HealthCheckupPackage>(
        db, CollectionNames.HEALTH_CHECKUP_PACKAGES, HealthCheckupPackage::class.java, "HealthCheckupPackage",
    ),
    HealthCheckupPackageRepository

class ChronicOpdGridRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ChronicOpdGrid>(
        db, CollectionNames.CHRONIC_OPD_GRIDS, ChronicOpdGrid::class.java, "ChronicOpdGrid",
    ),
    ChronicOpdGridRepository
