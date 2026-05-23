package com.rate.aegis.business.calculator.api

import com.rate.aegis.settings.AegisSettingsStore
import com.rate.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Wire-only DTO mirror of the server's `AuditVerifyResponse`. The hash-chain
 * walker reports `ok` plus `rowsChecked`; on a break it also hands back the
 * offending row id and a human-readable reason. Defaults let older payloads
 * (without `breakAtId`/`reason`) hydrate cleanly.
 */
@Serializable
data class AuditVerify(
    val ok: Boolean,
    val rowsChecked: Int,
    val breakAtId: Long? = null,
    val reason: String? = null
)

/**
 * Wire-only DTO mirror of the server's `IdempotencyEntry`. One row per cached
 * idempotency-key entry — diagnostic listing only; the cached response body is
 * NOT included (operators get the byte length via `firstResponseBytes`).
 * Defaults hydrate cleanly if older server builds omit `responseStatus` /
 * `firstResponseBytes`.
 */
@Serializable
data class IdempotencyRow(
    val key: String,
    val routeKey: String,
    val requestHash: String,
    val responseStatus: Int = 0,
    val createdAt: String,
    val expiresAt: String,
    val firstResponseBytes: Int = 0
)

/**
 * Wire-only DTO mirror of the server's [com.rate.server.auth.Operator]. Lives in
 * the client file so :shared doesn't need to know about RBAC primitives.
 */
@Serializable
data class ApiOperator(
    val identity: String,
    val role: String,
    val scopes: Set<String> = emptySet(),
    val addedAtIso: String = ""
)

/**
 * Wire-only mirror of the server's `RedactedSession` DTO. Server irreversibly
 * truncates PII fields (mobile → last 4, pincode → first 3 + "XXX", member
 * lists → counts) before sending; this client sees only the redacted shape.
 * Keep field names byte-identical to the server DTO — deserialisation is by
 * name and any drift will silently null fields with defaults.
 */
@Serializable
data class RedactedSession(
    val sessionId: String,
    val currentScreen: String,
    val mobileMasked: String = "",
    val pincodePrefix: String = "",
    val eldestAge: String = "",
    val kidsCount: Int = 0,
    val hasPED: Boolean = false,
    val hasCriticalIllness: Boolean = false,
    val pedMemberCount: Int = 0,
    val criticalIllnessMemberCount: Int = 0,
    val selectedTier: String = "",
    val selectedSumInsured: Long = 0L,
    val selectedTenure: Int = 0,
    val selectedAddOnIds: List<String> = emptyList(),
    // Server-side DB timestamps (`buyonline_session.created_at` / `updated_at`)
    // used to compute time-to-completion in the Reports funnel surface. Defaults
    // to empty so older cached/persisted blobs without the fields still hydrate.
    val createdAtIso: String = "",
    val updatedAtIso: String = ""
)

/**
 * Operator-side HTTP client for the BUSINESS calculator. Multiplatform-friendly —
 * no explicit engine declared so Ktor auto-discovers (CIO on JVM, JS on WASM).
 *
 * The Excel upload endpoint that used to live here required `java.io.File` and so
 * was JVM-only; it has been split out into `ApiClient.uploadExcel()` as a JVM-only
 * extension in `:aegis/jvmMain` so the rest of the client can run in the browser.
 *
 * AUDIT ATTRIBUTION: every outbound request carries an `X-Aegis-Actor` header
 * sourced from `AegisSettings.operatorIdentity` (the value the operator types
 * into the Settings surface). The server's request interceptor lifts it into
 * `ACTOR_SUBJECT_KEY` so existing audit-recording code attributes events
 * automatically. The lookup happens per-request — not at client construction —
 * so editing Settings takes effect immediately, no client rebuild needed.
 * A blank identity skips the header so the server falls back to "unknown".
 */
class ApiClient(val baseUrl: String = "http://localhost:9090") {

    val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
            })
        }
        install(DefaultRequest) {
            // Re-read the persisted operator identity on every request so edits in
            // Settings take effect without rebuilding the client. The store load is
            // a cheap file/localStorage read; if it ever shows up in a profile we
            // can cache + invalidate, but for now correctness beats micro-opt.
            val identity = AegisSettingsStore.load().operatorIdentity
            if (identity.isNotBlank()) header("X-Aegis-Actor", identity)
        }
    }

    // ── Covers ────────────────────────────────────────────────────────────

    suspend fun getCovers(): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/covers").body()

    suspend fun getAgeBands(): List<AgeBand> =
        http.get("$baseUrl/api/covers/age-bands").body()

    suspend fun getFamilyTypes(): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/covers/family-types").body()

    suspend fun getSumInsureds(): Map<String, JsonElement> =
        http.get("$baseUrl/api/covers/sum-insureds").body()

    // ── Plans ─────────────────────────────────────────────────────────────

    suspend fun getPlans(): List<Plan> =
        http.get("$baseUrl/api/plans").body()

    suspend fun getPlan(id: String): Plan =
        http.get("$baseUrl/api/plans/$id").body()

    suspend fun savePlan(plan: Plan): Plan {
        val resp = http.post("$baseUrl/api/plans") {
            contentType(ContentType.Application.Json)
            setBody(plan)
        }
        return resp.body()
    }

    suspend fun deletePlan(id: String) {
        http.delete("$baseUrl/api/plans/$id")
    }

    // ── Quotes ────────────────────────────────────────────────────────────

    suspend fun calculate(request: QuoteRequest): QuoteResult =
        http.post("$baseUrl/api/quotes/calculate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun saveQuote(request: QuoteRequest): Map<String, JsonElement> =
        http.post("$baseUrl/api/quotes") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun listQuotes(limit: Int = 50): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/quotes?limit=$limit").body()

    /**
     * Recent saved quotes priced under [planId], newest first. Server caps the
     * filter window to the most recent 500 quotes and the response to [limit]
     * rows. Raw JSON shape matches [listQuotes] — same QuoteListItem fields.
     */
    suspend fun getQuotesByPlan(planId: String, limit: Int = 20): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/quotes/by-plan/$planId?limit=$limit").body()

    // ── Audit ─────────────────────────────────────────────────────────────

    /**
     * Returns the newest [limit] audit events as raw JSON maps so the Aegis
     * surface can parse fields itself (matches the pattern of [listQuotes]).
     * Server caps the limit at 500 regardless of what we send.
     */
    suspend fun getAuditEvents(limit: Int = 100): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/audit/events?limit=$limit").body()

    // SSE consumer for `/api/audit/stream` lives in
    // `com.rate.aegis.data.openAuditStream(baseUrl)` rather than on this client,
    // because the implementation is platform-specific (WASM uses the browser
    // `EventSource`; JVM stays on polling until we wire `ktor-client-sse`).
    // ActivityFeed + AuditEventsSurface call that factory directly with this
    // client's [baseUrl].

    /**
     * Walks the hash chain server-side and reports the result. Scope-gated
     * (`audit.verify`); a caller without scope gets HTTP 403 with the standard
     * `{errorCode, scope, identity}` body. We surface that as a thrown
     * exception with the status code embedded in the message so the calling
     * surface can render a sensible "403 — insufficient scope" callout
     * without dragging a Ktor exception type into commonMain.
     */
    suspend fun verifyAuditChain(): AuditVerify {
        val resp = http.get("$baseUrl/api/audit/verify")
        if (!resp.status.isSuccess()) {
            val bodyText = runCatching { resp.bodyAsText() }.getOrNull().orEmpty()
            error("HTTP ${resp.status.value} ${resp.status.description}${if (bodyText.isNotBlank()) " — $bodyText" else ""}")
        }
        return resp.body()
    }

    /**
     * Lists recent idempotency-key cache entries (newest-first). Scope-gated
     * server-side (`audit.verify`); the same 403→thrown-error contract as
     * [verifyAuditChain] applies so the calling surface can render a WARN
     * callout instead of bubbling a Ktor exception type into commonMain.
     */
    suspend fun listIdempotencyKeys(limit: Int = 100): List<IdempotencyRow> {
        val resp = http.get("$baseUrl/api/audit/idempotency?limit=$limit")
        if (!resp.status.isSuccess()) {
            val bodyText = runCatching { resp.bodyAsText() }.getOrNull().orEmpty()
            error("HTTP ${resp.status.value} ${resp.status.description}${if (bodyText.isNotBlank()) " — $bodyText" else ""}")
        }
        return resp.body()
    }

    /**
     * Server-rendered IRDAI prospectus for [planId] as a self-contained HTML
     * document. Same 12-section structure as the Compose ProspectusSurface,
     * usable by external integrations (regulatory portal, agent CRM) and
     * print-friendly via the browser (File → Print → Save as PDF).
     */
    suspend fun getProspectusHtml(planId: String): String =
        http.get("$baseUrl/api/plans/$planId/prospectus.html").bodyAsText()

    // ── Discounts ─────────────────────────────────────────────────────────

    /**
     * Returns the discount catalogue with server-resolved rates. Same
     * parse-on-client shape as [listQuotes] / [getAuditEvents] so the Aegis
     * surface can pick fields without dragging a serialization schema across
     * the wire.
     */
    suspend fun getDiscounts(): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/discounts").body()

    // ── Import (seed + multipart file upload) ─────────────────────────────

    suspend fun seedBuiltinData(): Map<String, JsonElement> =
        http.post("$baseUrl/api/import/seed").body()

    /**
     * Multipart upload of an Excel rate workbook by raw bytes + filename.
     *
     * Sibling to the JVM-only `uploadExcel(File)` extension in `:aegis/jvmMain`
     * — that one still exists for the legacy ImportScreen, but the new Aegis
     * operator-shell Import surface (commonMain) routes through here so the
     * same code path drives both the desktop FileDialog flow and the WASM
     * `<input type="file">` flow. Ktor's `MultiPartFormDataContent` +
     * `formData { append(name, ByteArray, headers) }` is multiplatform — no
     * `java.io.File` reach — so we can live in commonMain.
     *
     * The Content-Type is `application/vnd.ms-excel` to match the legacy
     * extension; the server's POI parser doesn't actually check it, but
     * keeping the byte-level request identical avoids surprise if a future
     * server build does start gating on MIME.
     */
    suspend fun uploadExcelBytes(bytes: ByteArray, filename: String): Map<String, JsonElement> =
        http.post("$baseUrl/api/import/upload") {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    append(HttpHeaders.ContentType, "application/vnd.ms-excel")
                })
            }))
        }.body()

    // ── Server health & Prometheus metrics ────────────────────────────────

    /**
     * Lightweight liveness probe. Returns the small JSON status payload the
     * server emits (e.g. `{"status":"ok","db":"up"}`). Parsed as a raw map so
     * the Aegis surface can render whatever fields the server happens to ship
     * without us shipping a schema in lockstep.
     */
    suspend fun health(): Map<String, JsonElement> = http.get("$baseUrl/health").body()

    /**
     * Raw Prometheus text-format exposition. Parsing into name/value pairs is
     * the caller's job — keeps this transport-thin and avoids a Prometheus
     * client dep in commonMain.
     */
    suspend fun metricsText(): String = http.get("$baseUrl/metrics").bodyAsText()

    // ── Buy-online sessions (operator analytics) ──────────────────────────

    /**
     * Recent buy-online session snapshots for the Reports "Customer journey"
     * funnel. Server caps at 2000; default 500.
     *
     * Phase-2 redaction: the wire payload is [RedactedSession], NOT the raw
     * [BuyOnlineSessionState] persisted server-side. PII masking is performed
     * server-side (irreversible) before serialisation — mobile collapses to a
     * last-4 mask, pincode keeps only its first 3 chars, and PED/CI member
     * lists are reduced to counts. The funnel only ever needed `currentScreen`,
     * so this is purely additive defence-in-depth.
     *
     * Gated by the `sessions.read` scope on the server. Callers must be a
     * registered operator with that scope OR the operators store must be
     * empty (Phase-1 bootstrap window).
     */
    suspend fun listBuyOnlineSessions(limit: Int = 500): List<RedactedSession> =
        http.get("$baseUrl/api/buy-online/sessions?limit=$limit").body()

    // ── Operators (Phase-1 RBAC allowlist) ────────────────────────────────

    suspend fun listOperators(): List<ApiOperator> =
        http.get("$baseUrl/api/operators").body()

    suspend fun addOperator(op: ApiOperator): ApiOperator =
        http.post("$baseUrl/api/operators") {
            contentType(ContentType.Application.Json); setBody(op)
        }.body()

    suspend fun removeOperator(identity: String) {
        http.delete("$baseUrl/api/operators/$identity")
    }

    fun close() = http.close()
}
