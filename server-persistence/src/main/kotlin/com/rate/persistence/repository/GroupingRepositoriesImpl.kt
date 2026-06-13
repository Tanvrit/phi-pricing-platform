package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.grouping.FamilyType
import com.rate.sdk.catalog.model.grouping.GroupSize
import com.rate.sdk.catalog.model.grouping.GroupType
import com.rate.sdk.catalog.model.grouping.IndustryType
import com.rate.sdk.catalog.repository.FamilyTypeRepository
import com.rate.sdk.catalog.repository.GroupSizeRepository
import com.rate.sdk.catalog.repository.GroupTypeRepository
import com.rate.sdk.catalog.repository.IndustryTypeRepository

/**
 * Mongo actuals for the GROUPING demographics master PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic CRUD + draft/publish + optimistic concurrency does all
 * the work; the only per-entity inputs are the collection name and entity class. Collection names
 * are held in [GroupingCollections] (lowerCamel plural per entity).
 */
object GroupingCollections {
    const val GROUP_TYPES = "groupTypes"
    const val GROUP_SIZES = "groupSizes"
    const val FAMILY_TYPES = "familyTypes"
    const val INDUSTRY_TYPES = "industryTypes"
}

class GroupTypeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<GroupType>(db, GroupingCollections.GROUP_TYPES, GroupType::class.java, "GroupType"),
    GroupTypeRepository

class GroupSizeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<GroupSize>(db, GroupingCollections.GROUP_SIZES, GroupSize::class.java, "GroupSize"),
    GroupSizeRepository

class FamilyTypeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<FamilyType>(db, GroupingCollections.FAMILY_TYPES, FamilyType::class.java, "FamilyType"),
    FamilyTypeRepository

class IndustryTypeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<IndustryType>(
        db, GroupingCollections.INDUSTRY_TYPES, IndustryType::class.java, "IndustryType",
    ),
    IndustryTypeRepository
