package com.rate.server.routes

import com.rate.server.audit.ServerAuditService
import com.rate.sdk.catalog.model.Vendor
import com.rate.sdk.catalog.model.group.MedicalDeviceCatalog
import com.rate.sdk.catalog.model.group.SurgicalSublimit
import com.rate.sdk.catalog.model.group.VaccinationCatalog
import com.rate.sdk.catalog.repository.MedicalDeviceCatalogRepository
import com.rate.sdk.catalog.repository.SurgicalSublimitRepository
import com.rate.sdk.catalog.repository.VaccinationCatalogRepository
import com.rate.sdk.catalog.repository.VendorRepository
import io.ktor.server.routing.Route

/**
 * Admin-CRUD wiring for the GLOBAL vendor registry plus the GROUP annexure-derived catalogs
 * (surgical sublimit table, adult vaccination list, medical-device list). Each is exposed through
 * the SAME generic [adminCrudRoutes] factory as every other config entity — list/get/create/
 * update/soft-delete/restore/publish/import/export with identical auditing + scope-gating.
 *
 * Route paths MUST equal the operator-console descriptor ids (the resource segment in
 * /api/admin/{id}/…) so the frontend screens resolve without a 404:
 *   /api/admin/vendors                — GLOBAL [Vendor] (no productLine; scoped `config.vendor.*`)
 *   /api/admin/surgical-sublimits     — GROUP [SurgicalSublimit]
 *   /api/admin/vaccination-catalogs   — GROUP [VaccinationCatalog]
 *   /api/admin/medical-device-catalogs— GROUP [MedicalDeviceCatalog]
 *
 * The three GROUP entities are scoped under the coarse `group` token (like the rest of the GROUP
 * config tree) so a BUSINESS operator's default role bundle grants access; ADMIN (`*`) is
 * unaffected. Vendor keeps its own `vendor` scope segment.
 */
fun Route.vendorRoutes(
    vendors: VendorRepository,
    surgicalSublimits: SurgicalSublimitRepository,
    vaccinationCatalogs: VaccinationCatalogRepository,
    medicalDeviceCatalogs: MedicalDeviceCatalogRepository,
    audit: ServerAuditService,
) {
    // GLOBAL vendor registry
    adminCrudRoutes("/api/admin/vendors", "vendor", Vendor.serializer(), vendors, audit)

    // GROUP annexure-derived catalogs
    adminCrudRoutes(
        "/api/admin/surgical-sublimits", "surgicalSublimit", SurgicalSublimit.serializer(),
        surgicalSublimits, audit, scopeEntity = "group",
    )
    adminCrudRoutes(
        "/api/admin/vaccination-catalogs", "vaccinationCatalog", VaccinationCatalog.serializer(),
        vaccinationCatalogs, audit, scopeEntity = "group",
    )
    adminCrudRoutes(
        "/api/admin/medical-device-catalogs", "medicalDeviceCatalog", MedicalDeviceCatalog.serializer(),
        medicalDeviceCatalogs, audit, scopeEntity = "group",
    )
}
