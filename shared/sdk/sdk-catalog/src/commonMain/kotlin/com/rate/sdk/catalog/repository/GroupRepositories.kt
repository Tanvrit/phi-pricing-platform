package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
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

/**
 * GROUP catalog repository PORTS — one per admin-CRUD GROUP config entity, each extending
 * the generic [ConfigRepository]. Same uniform CRUD as the RETAIL repos; the ProductLine
 * discriminator (on the entities) keeps GROUP a facet of the one catalog, not a parallel tree.
 */
interface GroupProductConfigRepository : ConfigRepository<GroupProductConfig>
interface GroupGradeRepository : ConfigRepository<GroupGrade>
interface BenefitScheduleRepository : ConfigRepository<BenefitSchedule>
interface WaitingPeriodRepository : ConfigRepository<WaitingPeriod>
interface EligibilityCriteriaRepository : ConfigRepository<EligibilityCriteria>
interface PpdPtdTableRepository : ConfigRepository<PpdPtdTable>
interface DayCareProcedureRepository : ConfigRepository<DayCareProcedure>
interface ConsumablesListRepository : ConfigRepository<ConsumablesList>
interface HealthCheckupPackageRepository : ConfigRepository<HealthCheckupPackage>
interface ChronicOpdGridRepository : ConfigRepository<ChronicOpdGrid>
