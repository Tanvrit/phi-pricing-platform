package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.ingestion.model.RateMeta
import com.rate.sdk.ingestion.network.ActivateVersionRequest
import com.rate.sdk.ingestion.network.IngestionApi
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisStatus
import com.rate.sdk.ui.kit.components.AegisStatusPill
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.components.showToast
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import kotlinx.coroutines.launch

/**
 * Rate-import & version console — relocated from aegis ImportSurface. Lists the rate-version
 * history per product line via [IngestionApi], shows the active version, and lets an admin promote
 * a previously-imported version to active (the server reloads its engine snapshot). The actual
 * file upload + Excel(POI) parse is server-side; this surface drives version lifecycle and shows
 * the resulting metadata.
 */
@Composable
fun ImportSurface(ingestion: IngestionApi, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var productLine by remember { mutableStateOf(ProductLine.RETAIL) }
    var versions by remember { mutableStateOf<List<RateMeta>>(emptyList()) }
    var active by remember { mutableStateOf<RateMeta?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true
        scope.launch {
            runCatching {
                val list = ingestion.listVersions(productLine)
                val act = ingestion.activeVersion(productLine)
                list to act
            }.onSuccess { (list, act) -> versions = list; active = act; error = null }
                .onFailure { error = it.message ?: "Server unreachable" }
            loading = false
        }
    }
    LaunchedEffect(productLine) { reload() }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text("Rate Imports", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "Immutable rate batches. Promote a version to activate it; the engine snapshot reloads server-side.",
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )

        error?.let { AegisCallout(kind = CalloutKind.WARN, title = "Could not reach the ingestion API", body = it) }

        active?.let {
            AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Active version: ${it.version}",
                body = "Imported ${it.importedAt.toString().take(10)} · ${it.rowCounts.values.sum()} rows · ${it.sourceFileName}",
            )
        }

        AegisCard(title = "Version history", subtitle = productLine.name) {
            AegisTable(
                items = versions,
                columns = listOf(
                    AegisColumn<RateMeta>(header = "Version", weight = 1.2f) {
                        Text(it.version, style = AegisTypography.mono14)
                    },
                    AegisColumn(header = "Imported", weight = 1.0f) {
                        Text(it.importedAt.toString().take(19).replace('T', ' '),
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                    },
                    AegisColumn(header = "File", weight = 1.4f) {
                        Text(it.sourceFileName.ifBlank { "—" }, style = AegisTypography.body)
                    },
                    AegisColumn(header = "Rows", weight = 0.6f, align = TextAlign.End) {
                        Text(it.rowCounts.values.sum().toString(), style = AegisTypography.body)
                    },
                    AegisColumn(header = "State", weight = 0.7f) {
                        AegisStatusPill(if (it.active) AegisStatus.Live else AegisStatus.Draft)
                    },
                    AegisColumn(header = "Actions", weight = 1.0f, align = TextAlign.End) { meta ->
                        if (!meta.active) {
                            AegisButton(
                                label = "Activate",
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            ingestion.activateVersion(ActivateVersionRequest(meta.version, productLine))
                                        }.onSuccess {
                                            showToast("Activated ${meta.version}", CalloutKind.SUCCESS)
                                            reload()
                                        }.onFailure {
                                            showToast(it.message ?: "Activation failed", CalloutKind.DANGER)
                                        }
                                    }
                                },
                                variant = AegisButtonVariant.Secondary,
                                size = AegisButtonSize.Sm,
                            )
                        } else {
                            Text("current", style = AegisTypography.small.copy(color = AegisColors.success700))
                        }
                    },
                ),
                emptyState = {
                    Text(
                        if (loading) "Loading versions…" else "No rate versions imported yet for ${productLine.name}.",
                        style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                    )
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = AegisSpacing.s3), horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                ProductLine.entries.forEach { pl ->
                    AegisButton(
                        label = pl.name,
                        onClick = { productLine = pl },
                        variant = if (pl == productLine) AegisButtonVariant.Primary else AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                    )
                }
            }
        }

        AegisCallout(
            kind = CalloutKind.INFO,
            title = "Uploading a new rate file",
            body = "File upload (Excel/CSV) is processed server-side by the ingestion handler. Drop the file " +
                "through the server admin upload endpoint; it appears here as a new version to activate.",
        )
    }
}
