package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.partnerorg.Channel
import com.rate.sdk.catalog.model.partnerorg.Insurer
import com.rate.sdk.catalog.model.partnerorg.Intermediary
import com.rate.sdk.catalog.model.partnerorg.Tpa

/**
 * Admin-CRUD repository PORTS for the PARTNERS domain — the external parties the group-insurance
 * programme transacts with: risk carriers ([Insurer]), distribution channels ([Channel]) and the
 * intermediaries ([Intermediary]) that place through them, and the servicing administrators
 * ([Tpa]). All are GLOBAL config masters (shared across product lines); each is a plain
 * [ConfigRepository] so the generic CRUD + draft/publish wiring applies unchanged.
 */
interface InsurerRepository : ConfigRepository<Insurer>

interface ChannelRepository : ConfigRepository<Channel>

interface IntermediaryRepository : ConfigRepository<Intermediary>

interface TpaRepository : ConfigRepository<Tpa>
