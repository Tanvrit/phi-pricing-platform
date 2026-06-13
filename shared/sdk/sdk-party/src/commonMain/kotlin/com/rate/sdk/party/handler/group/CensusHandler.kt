package com.rate.sdk.party.handler.group

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.time.Now
import com.rate.sdk.party.event.PartyEvent
import com.rate.sdk.party.event.PartyEventSink
import com.rate.sdk.party.model.group.Census
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.model.group.CensusMember
import com.rate.sdk.party.repository.CensusRepository

/**
 * Group census ingest + grade/age-band roll-up. Validates incoming lives, normalises
 * the denormalised `lives` count, persists through [CensusRepository], and exposes the
 * pure [aggregate] roll-up the group rating path consumes.
 */
class CensusHandler(
    private val repository: CensusRepository,
    private val events: PartyEventSink = PartyEventSink.NOOP,
) {

    /** Validate census members. Empty list = valid. */
    fun validate(members: List<CensusMember>): List<String> = buildList {
        if (members.isEmpty()) add("Census must contain at least one life")
        members.forEachIndexed { i, m ->
            val row = i + 1
            if (m.empId.isBlank()) add("Row $row: empId is required")
            if (m.age !in 0..120) add("Row $row (${m.empId}): age ${m.age} is out of range 0..120")
            if (m.sumInsured < 0) add("Row $row (${m.empId}): sum insured cannot be negative")
        }
    }

    /**
     * Ingest a census for an employer: validate, stamp the consistent `lives` count, and
     * upsert. On success emits [PartyEvent.CensusIngested] with the rolled-up life count.
     */
    suspend fun ingest(
        employerPartyRef: String,
        members: List<CensusMember>,
        label: String = "",
        actor: String? = null,
    ): AppResult<Census> {
        val errors = validate(members)
        if (errors.isNotEmpty()) return AppResult.Err(DomainError.Validation(errors))

        val census = Census(
            employerPartyRef = employerPartyRef,
            lives = members.size,
            members = members,
            label = label,
        )
        val saved = repository.upsert(census, actor)
        events.emit(
            PartyEvent.CensusIngested(
                censusId = saved.id,
                employerPartyRef = employerPartyRef,
                lives = saved.lives,
                actor = actor,
                at = Now.instant(),
            ),
        )
        return AppResult.Ok(saved)
    }

    /**
     * Append more lives to an existing census (chunked upload), re-stamping `lives`.
     * Returns Conflict if the census is missing.
     */
    suspend fun appendLives(
        censusId: String,
        more: List<CensusMember>,
        actor: String? = null,
    ): AppResult<Census> {
        val errors = validate(more)
        if (errors.isNotEmpty()) return AppResult.Err(DomainError.Validation(errors))
        val existing = repository.get(censusId)
            ?: return AppResult.Err(DomainError.NotFound("Census", censusId))
        val merged = existing.members + more
        val updated = existing.copy(members = merged, lives = merged.size, updatedAt = Now.instant())
        val saved = repository.upsert(updated, actor)
        events.emit(
            PartyEvent.CensusIngested(
                censusId = saved.id,
                employerPartyRef = saved.employerPartyRef,
                lives = saved.lives,
                actor = actor,
                at = Now.instant(),
            ),
        )
        return AppResult.Ok(saved)
    }

    /** Pure grade × age-band roll-up of an in-memory census (no IO). */
    fun rollup(census: Census): CensusAggregation =
        CensusAggregation.from(census.employerPartyRef, census.id, census.members)

    /**
     * Roll up a persisted census. Prefers the repository's server-side aggregation;
     * falls back to loading the document and rolling up in-process.
     */
    suspend fun aggregate(censusId: String): AppResult<CensusAggregation> {
        repository.aggregate(censusId)?.let { return AppResult.Ok(it) }
        val census = repository.get(censusId)
            ?: return AppResult.Err(DomainError.NotFound("Census", censusId))
        return AppResult.Ok(rollup(census))
    }
}
