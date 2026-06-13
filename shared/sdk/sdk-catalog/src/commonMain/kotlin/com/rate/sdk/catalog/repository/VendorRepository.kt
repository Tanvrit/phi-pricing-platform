package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.Vendor

/**
 * GLOBAL vendor repository PORT — extends the generic [ConfigRepository] so the frontend
 * lists/searches/creates/updates/soft-deletes/restores/publishes vendors with the same wiring
 * as catalog entities. A typed-marker interface (no extra methods) so DI binds it to its own
 * Mongo collection (suggested: "vendors"); the Mongo-backed actual lives in server-persistence.
 *
 * Unlike catalog repos, [Vendor] is not productLine-scoped — it is shared across RETAIL/GROUP.
 */
interface VendorRepository : ConfigRepository<Vendor>
