package com.rate.server.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Operator allowlist — Phase-1 "trust the header" RBAC. An operator's identity is
 * what they typed into the Aegis Settings UI; it arrives as `X-Aegis-Actor`. This
 * file declares which identities have which scopes. Real auth (JWT) is Phase 2;
 * until then, a hostile actor that can forge the header bypasses this.
 *
 * Storage is a flat JSON file at `~/.aegis/operators.json` — easy to inspect or
 * edit out-of-band, easy to migrate away from when Phase 2 auth lands.
 */
@Serializable
data class Operator(
    val identity: String,
    val role: String,                  // "admin" or "operator"
    val scopes: Set<String> = emptySet(),
    val addedAtIso: String = ""
)

@Serializable
data class OperatorsFile(val operators: List<Operator> = emptyList())

object OperatorsStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val path: File by lazy { File(System.getProperty("user.home"), ".aegis/operators.json") }

    @Volatile private var cache: OperatorsFile? = null

    fun load(): OperatorsFile {
        cache?.let { return it }
        return try {
            if (!path.exists()) seedDefault()
            val parsed = json.decodeFromString<OperatorsFile>(path.readText())
            cache = parsed
            parsed
        } catch (_: Throwable) {
            OperatorsFile()
        }
    }

    fun save(file: OperatorsFile) {
        path.parentFile?.mkdirs()
        path.writeText(json.encodeToString(file))
        cache = file
    }

    fun upsert(op: Operator): OperatorsFile {
        val current = load()
        val updated = current.copy(operators = current.operators
            .filter { !it.identity.equals(op.identity, ignoreCase = true) } + op)
        save(updated)
        return updated
    }

    fun remove(identity: String): OperatorsFile {
        val current = load()
        val updated = current.copy(operators = current.operators
            .filter { !it.identity.equals(identity, ignoreCase = true) })
        save(updated)
        return updated
    }

    fun hasScope(identity: String?, scope: String): Boolean {
        if (identity.isNullOrBlank()) return false
        val op = load().operators.firstOrNull { it.identity.equals(identity, ignoreCase = true) }
            ?: return false
        return op.role == "admin" || scope in op.scopes
    }

    private fun seedDefault() {
        path.parentFile?.mkdirs()
        path.writeText("""{"operators":[]}""")
    }
}
