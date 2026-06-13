package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.group.MedicalDeviceCatalog
import com.rate.sdk.catalog.model.group.SurgicalSublimit
import com.rate.sdk.catalog.model.group.VaccinationCatalog

/**
 * GROUP annexure-derived catalog repository PORTS — the structured entities split out of the
 * EE annexure (surgical sublimit table, adult vaccination list, medical-device list). Each
 * extends the generic [ConfigRepository] so the frontend gets identical list/search/create/
 * update/soft-delete/restore/publish wiring; Mongo-backed actuals live in server-persistence.
 *
 * Typed-marker interfaces (no extra methods beyond the generic CRUD) so DI can bind a distinct
 * collection per entity while handlers stay uniform.
 */
interface SurgicalSublimitRepository : ConfigRepository<SurgicalSublimit>
interface VaccinationCatalogRepository : ConfigRepository<VaccinationCatalog>
interface MedicalDeviceCatalogRepository : ConfigRepository<MedicalDeviceCatalog>
