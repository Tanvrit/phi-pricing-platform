package com.rate.aegis.surfaces.discounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.components.*
import com.rate.aegis.theme.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Discounts — BUSINESS-shell surface (iteration e).
 *
 * Browses the discount catalogue resolved by the server at `GET /api/discounts`.
 * Mirrors the Cover Catalog surface's visual density: 28sp title, helper text,
 * single AegisTable, drill-down AegisDrawer.
 *
 * The catalogue is static (eight discount lines), so we load once on first
 * composition and skip auto-refresh — the LOADING → LIVE → ERROR banner only
 * fires for the initial fetch and stays put afterwards.
 */
@Composable
fun DiscountsSurface(baseUrl: String = "http://localhost:9090") {
    var rows by remember { mutableStateOf<List<DiscountRow>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DiscountRow?>(null) }

    LaunchedEffect(baseUrl) {
        val client = ApiClient(baseUrl)
        runCatching { client.getDiscounts() }
            .onSuccess { raw ->
                rows = raw.mapNotNull { it.toDiscountRow() }
                loadError = null
                loaded = true
            }
            .onFailure { t ->
                loadError = t.message ?: t::class.simpleName ?: "unknown error"
                loaded = true
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Discounts",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody,
        )
        Text(
            "Every disc_* line the pricing engine knows about, with the live rate " +
                    "resolved server-side. Click a row to inspect parameter variants.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )

        when {
            loadError != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Server unreachable",
                body = "Could not load discounts: $loadError. The catalogue itself is " +
                        "static — try again once the server is back up.",
            )
            !loaded -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Fetching discount catalogue from the server.",
            )
            else -> AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Live data",
                body = "${rows.size} discounts loaded. Rates resolved by the server's " +
                        "RateDataProvider — no auto-refresh (the catalogue is static).",
            )
        }

        AegisCard {
            AegisTable(
                items = rows,
                onRowClick = { selected = it },
                rowKey = { it.id },
                columns = listOf(
                    AegisColumn<DiscountRow>(
                        header = "ID", weight = 1.4f, mono = true,
                        cell = {
                            Text(
                                it.id,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AegisColors.textBody,
                            )
                        },
                    ),
                    AegisColumn(
                        header = "Name", weight = 2.4f,
                        cell = { Text(it.name, fontSize = 13.sp, color = AegisColors.textBody) },
                    ),
                    AegisColumn(
                        header = "Default rate", weight = 1.0f, mono = true,
                        cell = {
                            Text(
                                formatPercent(it.defaultRate),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (it.defaultRate == 0.0) AegisColors.textSecondary
                                else AegisColors.textBody,
                            )
                        },
                    ),
                    AegisColumn(
                        header = "Param variants", weight = 1.0f,
                        cell = {
                            val n = it.params.size
                            Text(
                                if (n == 0) "—" else "$n variant${if (n == 1) "" else "s"}",
                                fontSize = 13.sp,
                                color = if (n == 0) AegisColors.textSecondary
                                else AegisColors.textBody,
                            )
                        },
                    ),
                    AegisColumn(
                        header = "Status", weight = 0.7f,
                        cell = { AegisStatusPill(AegisStatus.Live) },
                    ),
                ),
                emptyState = {
                    AegisEmptyState(
                        title = "No discounts loaded",
                        helper = if (loadError != null)
                            "Server fetch failed — see the banner above."
                        else "The server returned an empty discount catalogue.",
                    )
                },
            )
        }
    }

    AegisDrawer(
        open = selected != null,
        onClose = { selected = null },
        title = selected?.name ?: "Discount details",
        subtitle = selected?.id,
    ) {
        selected?.let { row ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AegisStatusPill(AegisStatus.Live)
                    AegisChip(
                        label = "Discount",
                        tone = AegisChipTone.Info,
                        selected = false,
                    )
                }
                AegisHDivider()
                LedgerRow("ID", row.id, mono = true)
                LedgerRow("Name", row.name)
                LedgerRow("Default rate", formatPercent(row.defaultRate), mono = true)
                LedgerRow(
                    "Variants",
                    if (row.params.isEmpty()) "—" else row.params.size.toString(),
                )

                AegisHDivider()
                Text(
                    "Description",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textSecondary,
                )
                Text(
                    row.description.ifBlank { "No description provided." },
                    fontSize = 14.sp,
                    color = AegisColors.textBody,
                )

                AegisHDivider()
                Text(
                    "Parameter variants",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textSecondary,
                )
                if (row.params.isEmpty()) {
                    AegisCallout(
                        kind = CalloutKind.INFO,
                        title = "No parameters",
                        body = "This discount applies as a single flat rate — the engine " +
                                "reads `disc_${'$'}{row.id}` without a parameter key.",
                    )
                } else {
                    val paramName = row.params.first().paramName
                    Text(
                        paramName,
                        fontSize = 12.sp,
                        color = AegisColors.textSecondary,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AegisColors.slate2, AegisRadii.shapeMd)
                            .padding(AegisSpacing.s3),
                        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                    ) {
                        row.params.forEach { opt ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    opt.paramKey,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AegisColors.textBody,
                                )
                                Text(
                                    formatPercent(opt.rate),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = AegisColors.textBody,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Internal client-side row — decouples the surface from server serialization tweaks. */
private data class DiscountRow(
    val id: String,
    val name: String,
    val description: String,
    val defaultRate: Double,
    val params: List<DiscountParam>,
)

private data class DiscountParam(
    val paramName: String,
    val paramKey: String,
    val rate: Double,
)

private fun Map<String, JsonElement>.toDiscountRow(): DiscountRow? {
    val id = get("id")?.jsonPrimitive?.contentOrNull ?: return null
    val name = get("name")?.jsonPrimitive?.contentOrNull ?: id
    val description = get("description")?.jsonPrimitive?.contentOrNull.orEmpty()
    val defaultRate = get("defaultRate")?.jsonPrimitive?.doubleOrNull ?: 0.0
    val params = get("params")?.jsonArray?.mapNotNull { element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val pName = obj["paramName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val pKey = obj["paramKey"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val rate = obj["rate"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        DiscountParam(pName, pKey, rate)
    }.orEmpty()
    return DiscountRow(
        id = id,
        name = name,
        description = description,
        defaultRate = defaultRate,
        params = params,
    )
}

/**
 * One-decimal percent formatter — avoids JVM-only `String.format`.
 * 0.025 → "2.5%", 0.15 → "15.0%", 0.0 → "0.0%".
 */
private fun formatPercent(rate: Double): String {
    val pct = rate * 100.0
    val rounded = (pct * 10).toInt() / 10.0
    return "$rounded%"
}

@Composable
private fun LedgerRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody,
        )
    }
}
