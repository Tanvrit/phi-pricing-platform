package com.rate.aegis.data

import kotlinx.coroutines.flow.Flow

/**
 * JVM actual — returns `null` to signal "no SSE available on this platform".
 *
 * The desktop operator binary stays on the polling path against
 * `/api/audit/events` (5 s on the dashboard, 30 s in the audit surface), which
 * is fine for the way operators consume the audit log. Adding SSE here would
 * require `ktor-client-sse`, which forces a transitive Ktor minor-version
 * bump — punted to a follow-up iteration.
 */
actual fun openAuditStream(baseUrl: String): Flow<AuditEventDto>? = null
