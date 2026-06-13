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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.core.money.formatRupees
import com.rate.sdk.party.model.Sex
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.model.group.CensusMember
import com.rate.sdk.party.model.group.CensusRelation
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.components.showToast
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.CensusUploadRequest
import com.rate.sdk.ui.operator.network.GroupQuotingApi
import kotlinx.coroutines.launch

/**
 * GROUP quoting / census-upload surface. The operator pastes a census CSV
 * (`empId,name,age,gender,grade,relation,sumInsured`), which is parsed client-side into typed
 * [CensusMember]s, validated, uploaded via [GroupQuotingApi], and then aggregated server-side into
 * the grade × age-band roll-up the group rating path consumes.
 *
 * Keeping the parse client-side (pure-KMP, no POI) matches the layering contract — the heavy
 * lifting (persistence + aggregation + group premium rating) is server-side.
 */
@Composable
fun GroupQuotingSurface(group: GroupQuotingApi, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var employer by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<CensusMember>>(emptyList()) }
    var parseWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var aggregation by remember { mutableStateOf<CensusAggregation?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parse() {
        val (members, warnings) = parseCensusCsv(csv)
        parsed = members
        parseWarnings = warnings
    }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text("Group Quoting", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "Upload an employer census, roll it up by grade and age band, and rate the group policy.",
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )

        error?.let { AegisCallout(kind = CalloutKind.DANGER, title = "Upload failed", body = it) }

        AegisCard(title = "Census upload") {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisInput(employer, { employer = it }, "Employer ref", modifier = Modifier.weight(1f))
                    AegisInput(label, { label = it }, "Label (e.g. FY26-Q1)", modifier = Modifier.weight(1f))
                }
                AegisInput(
                    value = csv,
                    onValueChange = { csv = it },
                    label = "Census CSV",
                    helper = "Columns: empId, name, age, gender(M/F/O), grade, relation, sumInsured",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisButton(label = "Parse", onClick = { parse() }, variant = AegisButtonVariant.Secondary)
                    AegisButton(
                        label = "Upload census (${parsed.size})",
                        onClick = {
                            error = null
                            uploading = true
                            scope.launch {
                                runCatching {
                                    val census = group.uploadCensus(
                                        CensusUploadRequest(employer, label, parsed),
                                    )
                                    group.aggregate(census.id)
                                }.onSuccess {
                                    aggregation = it
                                    showToast("Census uploaded — ${it.totalLives} lives", CalloutKind.SUCCESS)
                                }.onFailure { error = it.message ?: "Server unreachable" }
                                uploading = false
                            }
                        },
                        loading = uploading,
                        enabled = parsed.isNotEmpty() && employer.isNotBlank(),
                    )
                }
            }
        }

        if (parseWarnings.isNotEmpty()) {
            AegisCallout(
                kind = CalloutKind.WARN,
                title = "${parseWarnings.size} row(s) skipped",
                body = parseWarnings.take(10).joinToString("\n") { "• $it" },
            )
        }

        if (parsed.isNotEmpty()) {
            AegisCard(title = "Parsed lives", subtitle = "${parsed.size} members") {
                AegisTable(
                    items = parsed,
                    maxHeight = 360.dp,
                    rowKey = { it.empId + it.relation.name + it.age },
                    columns = listOf(
                        AegisColumn<CensusMember>(header = "Emp ID", weight = 1.0f) {
                            Text(it.empId, style = AegisTypography.mono14)
                        },
                        AegisColumn(header = "Name", weight = 1.2f) { Text(it.name, style = AegisTypography.body) },
                        AegisColumn(header = "Age", weight = 0.5f, align = TextAlign.End) {
                            Text(it.age.toString(), style = AegisTypography.body)
                        },
                        AegisColumn(header = "Gender", weight = 0.6f) {
                            Text(it.gender.code, style = AegisTypography.body)
                        },
                        AegisColumn(header = "Grade", weight = 0.9f) { Text(it.grade, style = AegisTypography.body) },
                        AegisColumn(header = "Relation", weight = 1.0f) {
                            Text(it.relation.name, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                        },
                        AegisColumn(header = "SI", weight = 1.0f, align = TextAlign.End, mono = true) {
                            Text(formatRupees(it.sumInsured.toDouble()), style = AegisTypography.money)
                        },
                    ),
                )
            }
        }

        aggregation?.let { agg ->
            AegisCard(title = "Roll-up", subtitle = "${agg.totalLives} lives · avg age ${agg.averageAge.toInt()}") {
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    agg.byGrade.forEach { g ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${g.grade} — ${g.totalLives} lives",
                                style = AegisTypography.body.copy(color = AegisColors.textBody),
                            )
                            Text(formatRupees(g.sumInsured.toDouble()), style = AegisTypography.money.copy(color = AegisColors.textBody))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Parse a census CSV into [CensusMember]s. Skips a header row if present and emits a warning per
 * unparseable row (rather than failing the whole upload). Pure — no platform IO.
 */
internal fun parseCensusCsv(csv: String): Pair<List<CensusMember>, List<String>> {
    val members = mutableListOf<CensusMember>()
    val warnings = mutableListOf<String>()
    csv.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEachIndexed { idx, line ->
            // Skip a header line.
            if (idx == 0 && line.startsWith("empId", ignoreCase = true)) return@forEachIndexed
            val cols = line.split(',').map { it.trim() }
            if (cols.size < 3) { warnings.add("Row ${idx + 1}: too few columns"); return@forEachIndexed }
            val age = cols.getOrNull(2)?.toIntOrNull()
            if (cols[0].isBlank() || age == null) {
                warnings.add("Row ${idx + 1}: missing empId or age")
                return@forEachIndexed
            }
            members.add(
                CensusMember(
                    empId = cols[0],
                    name = cols.getOrElse(1) { "" },
                    age = age,
                    gender = parseSex(cols.getOrNull(3)),
                    grade = cols.getOrElse(4) { "DEFAULT" }.ifBlank { "DEFAULT" },
                    relation = parseRelation(cols.getOrNull(5)),
                    sumInsured = cols.getOrNull(6)?.filter { it.isDigit() }?.toLongOrNull() ?: 0L,
                ),
            )
        }
    return members to warnings
}

private fun parseSex(raw: String?): Sex = when (raw?.trim()?.uppercase()) {
    "M", "MALE" -> Sex.MALE
    "F", "FEMALE" -> Sex.FEMALE
    else -> Sex.OTHER
}

private fun parseRelation(raw: String?): CensusRelation = when (raw?.trim()?.uppercase()) {
    "SPOUSE" -> CensusRelation.SPOUSE
    "CHILD", "SON", "DAUGHTER" -> CensusRelation.CHILD
    "PARENT" -> CensusRelation.PARENT
    "PARENT_IN_LAW", "PARENTINLAW" -> CensusRelation.PARENT_IN_LAW
    else -> CensusRelation.EMPLOYEE
}
