package com.rate.sdk.ui.operator

import com.rate.core.network.client.TanvritClient
import com.rate.sdk.catalog.network.CatalogApi
import com.rate.sdk.ingestion.network.IngestionApi
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.registry.ConfigEntityRegistry

/**
 * The operator role. Drives which surfaces are visible — ADMIN sees everything (incl. the config
 * editor + ingestion); BUSINESS sees the operational surfaces (dashboard, quotes, calculator,
 * group quoting) but not destructive config CRUD; CUSTOMER is not an operator role (the customer
 * journey is sdk-ui-buyonline) but is accepted here as a read-only minimal view for shared shells.
 */
enum class OperatorRole { OWNER, ADMIN, BUSINESS, CUSTOMER }

/**
 * Everything the operator console needs to talk to the backend, bundled so the top-level
 * [OperatorConsole] composable and every ViewModel resolve their transport from one place.
 *
 * Built over the shared [TanvritClient] (auth + retry + status mapping + frozen AppJson). The
 * feature read-clients ([CatalogApi]/[QuoteApi]/[IngestionApi]) reuse the same underlying ktor
 * [TanvritClient.http] engine; the [ConfigAdminApi] drives the generic admin-CRUD surface; the
 * [registry] enumerates every admin entity.
 */
class OperatorContext(
    val client: TanvritClient,
    val role: OperatorRole,
    /** Operator identity stamped into admin mutations (audit attribution). */
    val actor: String? = null,
    val registry: ConfigEntityRegistry = ConfigEntityRegistry.default(),
) {
    private val baseUrl: String = client.config.normalizedBaseUrl

    val configAdmin: ConfigAdminApi = ConfigAdminApi(client)
    val catalog: CatalogApi = CatalogApi(client.http, baseUrl)
    val quotes: QuoteApi = QuoteApi(client.http, baseUrl)
    val ingestion: IngestionApi = IngestionApi(client.http, baseUrl)

    /** Whether [role] may mutate admin config (OWNER/ADMIN). */
    val canEditConfig: Boolean get() = role == OperatorRole.OWNER || role == OperatorRole.ADMIN
    /** Whether [role] may run rate imports / catalog seeds (OWNER/ADMIN). */
    val canIngest: Boolean get() = role == OperatorRole.OWNER || role == OperatorRole.ADMIN
}
