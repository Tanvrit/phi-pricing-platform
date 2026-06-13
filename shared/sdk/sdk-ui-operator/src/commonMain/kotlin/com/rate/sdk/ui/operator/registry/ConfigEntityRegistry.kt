package com.rate.sdk.ui.operator.registry

import com.rate.sdk.ui.operator.model.EntityDescriptor

/**
 * A console nav/hub group — a coarse business grouping of admin entities. The operator console
 * routes OWNER/ADMIN to a grid of these hubs (home); each hub drills into the per-entity
 * [com.rate.sdk.ui.operator.screen.ConfigEntityScreen]. The side nav also collapses its config
 * entries under these same group labels.
 *
 * The groups are intentionally business-facing (not "retail vs group"): an operator thinks in
 * terms of "Configuration", "Partners", "Actuarial" — not the storage line. A single entity always
 * belongs to exactly one group; the [ConfigEntityRegistry] resolves an entity's group from
 * [HubGroup.entityIds] and falls back to [HubGroup.CONFIGURATION] for any unmapped (e.g. Wave-B)
 * entity so a freshly-registered descriptor still appears somewhere.
 *
 * @param title operator-facing section label (side-nav header + hub-card group).
 * @param subtitle one-line description shown atop the hub.
 * @param entityIds the registry ids that live in this group, in display order.
 */
enum class HubGroup(
    val title: String,
    val subtitle: String,
    val entityIds: List<String>,
) {
    CONFIGURATION(
        title = "Configuration",
        subtitle = "Products, sections, covers, CI lists, annexures, tenures, add-ons and rating plans.",
        entityIds = listOf(
            "products", "sections", "covers", "ci-lists", "annexures", "tenures", "addons", "plans",
            "product-addon-configs", "categories", "benefit-types", "category-models", "adaptive-categories",
        ),
    ),
    DEMOGRAPHICS(
        title = "Demographics",
        subtitle = "Geography and population masters that drive eligibility and zoning.",
        entityIds = listOf(
            "pincode-zones",
            "age-bands", "genders", "payment-frequencies", "member-types", "family-relations",
            "group-types", "group-sizes", "family-types", "industry-types",
        ),
    ),
    PARTNERS(
        title = "Partners",
        subtitle = "Vendors, suppliers and the people with access to the console.",
        entityIds = listOf(
            "vendors", "users", "login-audits",
            "insurers", "channels", "intermediaries", "tpas",
            "sales-mappings", "client-locations", "business-types", "related-parties",
        ),
    ),
    UNDERWRITING(
        title = "Underwriting",
        subtitle = "Disease/ICD mapping, treatment categories and the rating parameter grid.",
        entityIds = listOf("treatment-categories", "disease-mappings", "rating-parameters"),
    ),
    FINANCIAL(
        title = "Financial",
        subtitle = "Group benefit schedules and financial cover limits per grade.",
        entityIds = listOf(
            "group-products", "group-grades", "benefit-schedules",
            "discount-configs", "variable-expenses", "policy-costs", "investment-income",
            "sum-insured-tiers", "reinsurance-treaties", "cost-of-capital",
        ),
    ),
    ACTUARIAL(
        title = "Actuarial",
        subtitle = "Disability schedules, waiting periods, eligibility and clinical reference masters.",
        entityIds = listOf(
            "ppd-ptd", "waiting-periods", "eligibility", "surgical-sublimits",
            "day-care", "consumables", "health-checkup", "chronic-opd",
            "vaccination-catalogs", "medical-device-catalogs",
            "authority-levels", "medical-trend-factors", "inflation-factors",
            "claim-thresholds", "loading-factors", "ibnr-reserves",
        ),
    ),
    SALES(
        title = "Sales",
        subtitle = "Buy-online offers and the curated journeys shown to customers (Wave B).",
        // Add-on bundles live in CONFIGURATION per the WS1 brief; Wave B registers the
        // sales-specific masters (campaigns, journeys, pricing offers) here.
        entityIds = listOf("rfqs", "experience-ratings", "group-size-matrices"),
    ),
}

/**
 * A resolved hub: the [group] plus the registered descriptors that belong to it (already filtered to
 * what's actually present in the registry, in [HubGroup.entityIds] order). Empty hubs are still
 * returned so Wave B can light them up as it registers masters.
 */
data class ConfigHub(
    val group: HubGroup,
    val descriptors: List<EntityDescriptor>,
)

/**
 * The single source of truth mapping EVERY admin-CRUD config entity to its admin API resource +
 * metadata-driven form. The operator console's config-editor nav and the generic
 * [com.rate.sdk.ui.operator.screen.ConfigEntityScreen] are both driven by this registry, so adding
 * a new admin entity is one [TypedEntityDescriptor] — no new screen, no new ViewModel.
 *
 * The registry covers:
 *  - RETAIL: plans, products, sections, covers/benefits, CI lists, annexures, tenures,
 *    pincode-zones, add-on bundles, vendors.
 *  - GROUP: group products, grades, benefit schedules, waiting periods, eligibility, PPD/PTD,
 *    day-care, consumables, health-checkup, chronic-OPD, surgical sub-limits, vaccination +
 *    medical-device catalogs.
 *  - IDENTITY: users, login-audits.
 *
 * Each descriptor's [EntityDescriptor.id] doubles as the admin REST resource path segment
 * (`/api/admin/{id}/…`).
 *
 * HUBS: [hubs] groups the descriptors into business [HubGroup]s for the console's hub grid + grouped
 * side nav. Wave B registers new masters by (1) adding the [TypedEntityDescriptor] to the
 * descriptor list passed to the registry and (2) adding its id to the relevant [HubGroup.entityIds];
 * unmapped ids auto-fall into [HubGroup.CONFIGURATION] so nothing is ever orphaned.
 */
class ConfigEntityRegistry(
    val descriptors: List<TypedEntityDescriptor<*>>,
) {
    private val byId: Map<String, TypedEntityDescriptor<*>> = descriptors.associateBy { it.id }

    /** All registered descriptors as the `Any`-boundary [EntityDescriptor] interface. */
    val all: List<EntityDescriptor> get() = descriptors

    val retail: List<EntityDescriptor> get() = descriptors.filter { !it.isGroup }
    val group: List<EntityDescriptor> get() = descriptors.filter { it.isGroup }

    fun get(id: String): TypedEntityDescriptor<*>? = byId[id]

    /** The [HubGroup] an entity belongs to (CONFIGURATION fallback for unmapped/Wave-B ids). */
    fun hubOf(id: String): HubGroup =
        HubGroup.entries.firstOrNull { id in it.entityIds } ?: HubGroup.CONFIGURATION

    /**
     * The console hubs in [HubGroup] declaration order, each carrying its present descriptors
     * (ordered per [HubGroup.entityIds], with any unmapped registered ids appended to CONFIGURATION
     * so a newly-added descriptor is always reachable). Empty hubs are retained.
     */
    val hubs: List<ConfigHub> by lazy {
        val mappedIds = HubGroup.entries.flatMap { it.entityIds }.toSet()
        // Registered ids no HubGroup claims — keep them reachable under Configuration.
        val orphanDescriptors = descriptors.filter { it.id !in mappedIds }
        HubGroup.entries.map { groupEnum ->
            val inGroup = groupEnum.entityIds.mapNotNull { byId[it] }
            val descriptors = if (groupEnum == HubGroup.CONFIGURATION) inGroup + orphanDescriptors else inGroup
            ConfigHub(groupEnum, descriptors)
        }
    }

    companion object {
        /** The default registry wiring every catalog + rating + identity admin entity. */
        fun default(): ConfigEntityRegistry =
            ConfigEntityRegistry(
                RetailDescriptors.all + GroupDescriptors.all + IdentityDescriptors.all +
                    DemographicsDescriptors.all + GroupingDescriptors.all +
                    PartnerOrgDescriptors.all + PartnerLinkDescriptors.all +
                    UnderwritingDescriptors.all + FinancialCoreDescriptors.all +
                    FinancialCapitalDescriptors.all + ActuarialDescriptors.all +
                    ProductConfigDescriptors.all + RfqDescriptors.all,
            )
    }
}
