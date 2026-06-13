package com.rate.sdk.ui.operator.network

import com.rate.core.base.json.AppJson
import com.rate.core.network.client.TanvritClient
import com.rate.sdk.party.model.group.Census
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.model.group.CensusMember
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Request to ingest a group census for an employer. */
@Serializable
data class CensusUploadRequest(
    @SerialName("employerPartyRef") val employerPartyRef: String,
    @SerialName("label") val label: String = "",
    @SerialName("members") val members: List<CensusMember>,
)

/**
 * Group quoting / census client for the operator console. The census ingest + grade×age-band
 * roll-up logic lives server-side in [com.rate.sdk.party.handler.group.CensusHandler]; this client
 * uploads the parsed census and fetches the aggregation the group rating path consumes. Group
 * premium rating itself runs server-side over the GroupRateDataProvider.
 */
class GroupQuotingApi(
    private val client: TanvritClient,
    private val json: Json = AppJson.json,
) {
    /** Upload + persist a census; the server validates and stamps the lives count. */
    suspend fun uploadCensus(request: CensusUploadRequest): Census {
        val response = client.execute(HttpMethod.Post, "/api/group/census") {
            setBody(TextContent(json.encodeToString(CensusUploadRequest.serializer(), request), ContentType.Application.Json))
        }
        return json.decodeFromString(Census.serializer(), response.bodyAsText())
    }

    /** Fetch the grade × age-band roll-up for a persisted census. */
    suspend fun aggregate(censusId: String): CensusAggregation {
        val response = client.execute(HttpMethod.Get, "/api/group/census/$censusId/aggregation")
        return json.decodeFromString(CensusAggregation.serializer(), response.bodyAsText())
    }
}
