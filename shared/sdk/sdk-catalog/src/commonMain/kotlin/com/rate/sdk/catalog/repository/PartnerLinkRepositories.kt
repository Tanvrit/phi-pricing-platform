package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.partnerlink.BusinessType
import com.rate.sdk.catalog.model.partnerlink.ClientLocation
import com.rate.sdk.catalog.model.partnerlink.RelatedParty
import com.rate.sdk.catalog.model.partnerlink.SalesIntermediaryMapping

/**
 * PARTNERS domain — partner-linkage admin-CRUD repository PORTS. Each is a typed-marker interface
 * over the generic [ConfigRepository] so DI binds a distinct collection per entity while the
 * generic CRUD + draft/publish + optimistic concurrency does the work. Mongo actuals live in
 * server-persistence.
 */
interface SalesIntermediaryMappingRepository : ConfigRepository<SalesIntermediaryMapping>
interface ClientLocationRepository : ConfigRepository<ClientLocation>
interface BusinessTypeRepository : ConfigRepository<BusinessType>
interface RelatedPartyRepository : ConfigRepository<RelatedParty>
