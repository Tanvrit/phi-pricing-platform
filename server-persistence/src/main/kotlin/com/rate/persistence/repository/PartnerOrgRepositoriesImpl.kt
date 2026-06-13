package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.partnerorg.Channel
import com.rate.sdk.catalog.model.partnerorg.Insurer
import com.rate.sdk.catalog.model.partnerorg.Intermediary
import com.rate.sdk.catalog.model.partnerorg.Tpa
import com.rate.sdk.catalog.repository.ChannelRepository
import com.rate.sdk.catalog.repository.InsurerRepository
import com.rate.sdk.catalog.repository.IntermediaryRepository
import com.rate.sdk.catalog.repository.TpaRepository

/**
 * Mongo collection names for the PARTNERS domain (lowerCamel plural per entity). Kept local to the
 * domain so the persistence layer owns the physical names without touching shared CollectionNames.
 */
object PartnerOrgCollections {
    const val INSURERS = "insurers"
    const val CHANNELS = "channels"
    const val INTERMEDIARIES = "intermediaries"
    const val TPAS = "tpas"
}

/**
 * Mongo actuals for the PARTNERS-domain PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic admin-CRUD + draft/publish + optimistic concurrency does
 * all the work; only the collection name and the human entity-name (for error messages) differ.
 */
class InsurerRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Insurer>(db, PartnerOrgCollections.INSURERS, Insurer::class.java, "Insurer"),
    InsurerRepository

class ChannelRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Channel>(db, PartnerOrgCollections.CHANNELS, Channel::class.java, "Channel"),
    ChannelRepository

class IntermediaryRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Intermediary>(db, PartnerOrgCollections.INTERMEDIARIES, Intermediary::class.java, "Intermediary"),
    IntermediaryRepository

class TpaRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Tpa>(db, PartnerOrgCollections.TPAS, Tpa::class.java, "TPA"),
    TpaRepository
