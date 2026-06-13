package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.grouping.FamilyType
import com.rate.sdk.catalog.model.grouping.GroupSize
import com.rate.sdk.catalog.model.grouping.GroupType
import com.rate.sdk.catalog.model.grouping.IndustryType

/**
 * GROUPING demographics master repository PORTS — the group-classification masters (group type,
 * group-size band, family-definition, industry/occupation class) that drive eligibility and rating
 * loadings for group-insurance quotes. Each is a typed-marker interface over the generic
 * [ConfigRepository] so the operator console lists/searches/creates/updates/soft-deletes/restores/
 * publishes every entity with identical wiring; Mongo actuals live in server-persistence.
 */
interface GroupTypeRepository : ConfigRepository<GroupType>
interface GroupSizeRepository : ConfigRepository<GroupSize>
interface FamilyTypeRepository : ConfigRepository<FamilyType>
interface IndustryTypeRepository : ConfigRepository<IndustryType>
