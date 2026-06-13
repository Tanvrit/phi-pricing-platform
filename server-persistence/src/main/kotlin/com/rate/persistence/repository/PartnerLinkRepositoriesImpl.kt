package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.partnerlink.BusinessType
import com.rate.sdk.catalog.model.partnerlink.ClientLocation
import com.rate.sdk.catalog.model.partnerlink.RelatedParty
import com.rate.sdk.catalog.model.partnerlink.SalesIntermediaryMapping
import com.rate.sdk.catalog.repository.BusinessTypeRepository
import com.rate.sdk.catalog.repository.ClientLocationRepository
import com.rate.sdk.catalog.repository.RelatedPartyRepository
import com.rate.sdk.catalog.repository.SalesIntermediaryMappingRepository

/**
 * Mongo actuals for the PARTNERS partner-linkage admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository], bound to its own distinct collection; the generic CRUD +
 * draft/publish + optimistic concurrency does all the work.
 */
object PartnerLinkCollections {
    const val SALES_INTERMEDIARY_MAPPINGS = "salesIntermediaryMappings"
    const val CLIENT_LOCATIONS = "clientLocations"
    const val BUSINESS_TYPES = "businessTypes"
    const val RELATED_PARTIES = "relatedParties"
}

class SalesIntermediaryMappingRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<SalesIntermediaryMapping>(
        db, PartnerLinkCollections.SALES_INTERMEDIARY_MAPPINGS, SalesIntermediaryMapping::class.java, "SalesIntermediaryMapping",
    ),
    SalesIntermediaryMappingRepository

class ClientLocationRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ClientLocation>(
        db, PartnerLinkCollections.CLIENT_LOCATIONS, ClientLocation::class.java, "ClientLocation",
    ),
    ClientLocationRepository

class BusinessTypeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<BusinessType>(
        db, PartnerLinkCollections.BUSINESS_TYPES, BusinessType::class.java, "BusinessType",
    ),
    BusinessTypeRepository

class RelatedPartyRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<RelatedParty>(
        db, PartnerLinkCollections.RELATED_PARTIES, RelatedParty::class.java, "RelatedParty",
    ),
    RelatedPartyRepository
