package com.rate.server.routes

import com.rate.domain.data.CoverCatalog
import com.rate.domain.model.Plan
import com.rate.domain.model.PlanLifecycle
import com.rate.domain.repository.PlanRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * `GET /api/plans/{id}/prospectus.html` returns a self-contained, printable IRDAI-style
 * prospectus document for a given plan. Mirrors the 12-section structure of
 * `aegis/.../surfaces/prospectus/ProspectusSurface.kt` so external integrations
 * (regulatory portal, agent CRM, internal tooling) can consume the same content
 * without depending on the Aegis Compose layer.
 *
 * Output is intentionally self-contained — single inline `<style>` block, no external
 * fonts/CSS/JS — so the browser can do File → Print → Save as PDF without surprises.
 *
 * UIN: the domain `Plan` doesn't carry a UIN field yet (the Aegis-side surface reads
 * it from `FakeAegisRepo.PlanMeta`, which isn't visible from the server). The server-side
 * renderer therefore shows "UIN: pending registration" with a draft-watermark note.
 * TODO: when `Plan.uin` lands in :shared, swap this placeholder for the real value.
 */
fun Route.prospectusRoutes(planRepo: PlanRepository) {
    route("/api/plans/{id}/prospectus.html") {
        get {
            val id = call.parameters["id"] ?: return@get call.respondText(
                renderError("Missing plan id"),
                contentType = ContentType.Text.Html,
                status = HttpStatusCode.BadRequest
            )
            val plan = planRepo.getPlan(id) ?: return@get call.respondText(
                renderError("Plan not found: ${esc(id)}"),
                contentType = ContentType.Text.Html,
                status = HttpStatusCode.NotFound
            )
            call.respondText(renderProspectusHtml(plan), contentType = ContentType.Text.Html)
        }
    }
}

private fun esc(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun renderError(message: String): String = """
<!doctype html><html><head><meta charset="utf-8"><title>Prospectus</title>
<style>body{font-family:system-ui,sans-serif;padding:32px;max-width:600px;margin:auto;color:#1f2328}</style>
</head><body><h2>Prospectus unavailable</h2><p>${esc(message)}</p></body></html>
""".trimIndent()

private fun renderProspectusHtml(plan: Plan): String {
    val today = Clock.System.now().toString().substringBefore('T')
    val name = esc(plan.name)
    val description = esc(plan.description.ifBlank { "No description on file." })
    val familyTypes = plan.availableFamilyTypes.joinToString(", ").ifBlank { "—" }
    val zones = plan.availableZones.joinToString(", ").ifBlank { "—" }
    val siGrid = if (plan.availableSumInsureds.isEmpty()) "—"
    else plan.availableSumInsureds.joinToString(", ") { formatRupeesServer(it) }
    val gstPct = formatPercentOneDecimal(plan.gstRate)
    val capPct = formatPercentOneDecimal(plan.maxDiscountCap)

    val coversList = plan.allowedCoverIds.takeIf { it.isNotEmpty() }
        ?.map { coverId -> CoverCatalog.findById(coverId)?.name ?: coverId }
        ?.sorted()
        ?: emptyList()

    val coverHtml = if (coversList.isEmpty())
        "<p>No optional covers attached to this plan.</p>"
    else
        "<ul>" + coversList.joinToString("") { "<li>${esc(it)}</li>" } + "</ul>"

    val draftBanner = when (plan.lifecycle) {
        PlanLifecycle.DRAFT -> """
            <div class="banner banner-draft">
              <strong>Internal draft — do not distribute.</strong>
              This product is in pre-IRDAI-registration review. The prospectus below is the
              working draft, not the filed document.
            </div>
        """.trimIndent()
        PlanLifecycle.RETIRED -> """
            <div class="banner banner-retired">
              <strong>Retired product.</strong>
              No new business; renewals only as per master schedule.
            </div>
        """.trimIndent()
        PlanLifecycle.LIVE -> ""
    }

    return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Prospectus — $name</title>
<style>
  :root { color-scheme: light; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    max-width: 800px; margin: 0 auto; padding: 32px;
    line-height: 1.55; color: #1f2328; background: #fafbfc;
  }
  header.cover {
    border: 1px solid #d0d7de; border-radius: 8px;
    padding: 20px 24px; background: white; margin-bottom: 20px;
  }
  header.cover h1 { margin: 0 0 4px; font-size: 24px; font-weight: 700; }
  header.cover .meta { color: #6e7781; font-size: 13px; }
  header.cover .uin { font-family: ui-monospace, "SF Mono", Menlo, monospace; }
  h2 { font-size: 18px; font-weight: 600; color: #0d1117;
       border-bottom: 1px solid #d0d7de; padding-bottom: 6px; margin-top: 28px; }
  h2 .num { font-family: ui-monospace, monospace; color: #6f42c1; margin-right: 8px; font-size: 16px; }
  p { margin: 8px 0; }
  ul { margin: 8px 0; padding-left: 22px; }
  table { border-collapse: collapse; width: 100%; margin: 12px 0; }
  th, td { border: 1px solid #d0d7de; padding: 6px 10px; text-align: left; font-size: 13px; }
  th { background: #f6f8fa; font-weight: 600; }
  .banner { padding: 14px 18px; border-radius: 6px; margin: 16px 0; font-size: 14px; }
  .banner-draft   { background: #ffe5e0; color: #b1361e; border: 1px solid #f3b8ad; }
  .banner-retired { background: #fff8c5; color: #6f5a00; border: 1px solid #eac54f; }
  .footer { margin-top: 32px; padding-top: 16px; border-top: 1px solid #d0d7de;
            color: #6e7781; font-size: 12px; }
  @media print {
    body { background: white; max-width: none; padding: 12px; }
    header.cover { box-shadow: none; }
  }
</style>
</head>
<body>

<header class="cover">
  <h1>$name</h1>
  <div class="meta">
    <span class="uin">UIN: pending registration</span>
    &nbsp;·&nbsp; Document version: draft &nbsp;·&nbsp; Generated: $today &nbsp;·&nbsp;
    Product family: ${esc(plan.planType.name)}
  </div>
</header>

$draftBanner

<h2><span class="num">§1</span>Product overview</h2>
<p>$description</p>

<h2><span class="num">§2</span>Scope of cover</h2>
$coverHtml

<h2><span class="num">§3</span>Eligibility</h2>
<table>
  <tr><th style="width:35%">Minimum entry age</th><td>${plan.minAge} years</td></tr>
  <tr><th>Maximum entry age</th><td>${plan.maxAge} years</td></tr>
  <tr><th>Allowed family types</th><td>${esc(familyTypes)}</td></tr>
  <tr><th>Geography / Zones</th><td>${esc(zones)}</td></tr>
  <tr><th>Sum insured grid</th><td>${esc(siGrid)}</td></tr>
</table>

<h2><span class="num">§4</span>Premium</h2>
<p>Goods &amp; Services Tax is applied at <strong>$gstPct</strong> on the chargeable premium
(HSN 9971). Multi-year tenure discounts are applied as per the master rate schedule.
Detailed band-by-band rate tables are out of scope for this prospectus; they are filed
with the regulator under the same UIN.</p>
<p>Discount stacking is capped at <strong>$capPct</strong> of total chargeable premium.</p>

<h2><span class="num">§5</span>Waiting periods</h2>
<table>
  <tr><th style="width:35%">Initial waiting period</th><td>30 days from policy commencement (medical conditions other than accidents)</td></tr>
  <tr><th>Specified diseases / illnesses</th><td>24 months</td></tr>
  <tr><th>Pre-existing diseases (PED)</th><td>36 months from policy commencement</td></tr>
</table>
<p><em>Defaults aligned to IRDAI (Health Insurance) Regulations, 2016. Per-plan overrides
not yet wired in :shared; this section will reflect plan-level fields when they land.</em></p>

<h2><span class="num">§6</span>Free-look period</h2>
<p>The policyholder may review the policy and return it within <strong>15 days</strong>
of receipt (electronic delivery counts from the date of confirmed delivery). On return,
the company refunds premium less proportionate risk premium for the on-cover period,
medical examination expenses, and stamp duty.</p>

<h2><span class="num">§7</span>Grace period</h2>
<p>A grace period of <strong>30 days</strong> from the renewal date is allowed for premium
payment. Continuous-cover benefits (PED clock, no-claim bonus, waiting-period credit) are
preserved if premium is received during grace. No new losses are covered during the grace
window itself.</p>

<h2><span class="num">§8</span>Claim process</h2>
<p>Claim intimation: within 24 hours of hospitalisation (emergency) or 48 hours in advance
for planned admissions. Final claim documents must be submitted within 15 days of discharge.
Settlement SLA: 30 days from receipt of the last necessary document, per IRDAI norms.</p>

<h2><span class="num">§9</span>Cancellation &amp; portability</h2>
<p>The policyholder may cancel mid-term with 15 days' written notice; refund is computed
on the company's short-period scale. Portability to another insurer requires a 45-day
written notice and is honoured per IRDAI (Protection of Policyholders' Interests) Regulations.
Continuity-of-cover credits transfer subject to the gaining insurer's underwriting.</p>

<h2><span class="num">§10</span>Grievance redressal</h2>
<p>First-level: PRUHealth Customer Care &mdash; helpline 1800-XXX-XXXX (placeholder),
grievance@pruhealth.example.in (placeholder). Second-level: Grievance Redressal Officer
at the registered office (per policy schedule). Final escalation: IRDAI Integrated
Grievance Management System (IGMS) at <em>igms.irda.gov.in</em> or 155255 / 1800 4254 732.</p>

<h2><span class="num">§11</span>Lifecycle status</h2>
<p>Current status: <strong>${plan.lifecycle.name}</strong>.
${
        when (plan.lifecycle) {
            PlanLifecycle.LIVE    -> "This product is open for new business and renewals."
            PlanLifecycle.DRAFT   -> "Pending IRDAI filing. Not available for sale."
            PlanLifecycle.RETIRED -> "Closed to new business; renewals continue per master schedule."
        }
    }</p>

<h2><span class="num">§12</span>Document control</h2>
<p>This prospectus was generated by the Aegis server at $today against plan id
<code>${esc(plan.id)}</code>. It is intended as a working draft until the corresponding
UIN is registered with IRDAI; once filed, the same endpoint will render the
regulator-approved document with the issued UIN in the cover block.</p>

<div class="footer">
  Aegis &middot; PRUHealth pricing platform &middot; Generated $today &middot;
  Plan id <code>${esc(plan.id)}</code>
</div>

</body>
</html>
    """.trimIndent()
}

/** Indian-grouping rupee formatter — server-side echo of `formatRupees`. */
private fun formatRupeesServer(paise: Long): String {
    val whole = paise.toString()
    // Indian grouping: last 3 digits, then groups of 2.
    return if (whole.length <= 3) "₹$whole"
    else {
        val last3 = whole.takeLast(3)
        val rest = whole.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        "₹$grouped,$last3"
    }
}

private fun formatPercentOneDecimal(rate: Double): String {
    val pct = rate * 100.0
    val rounded = (pct * 10).toInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()}%" else "$rounded%"
}
