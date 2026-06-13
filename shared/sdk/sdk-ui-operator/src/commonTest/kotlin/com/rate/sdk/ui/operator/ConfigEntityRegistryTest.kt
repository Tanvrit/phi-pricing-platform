package com.rate.sdk.ui.operator

import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.PlanType
import com.rate.sdk.catalog.model.Tenure
import com.rate.sdk.ui.operator.registry.ConfigEntityRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigEntityRegistryTest {

    private val registry = ConfigEntityRegistry.default()

    @Test
    fun registry_covers_all_admin_entities() {
        // retail (incl. identity, which is non-group) + group descriptors.
        assertEquals(registry.retail.size + registry.group.size, registry.all.size)
        // Every required entity id is registered (catalog + group + identity).
        val ids = registry.all.map { it.id }.toSet()
        listOf(
            "plans", "products", "sections", "covers", "ci-lists", "annexures", "tenures",
            "pincode-zones", "addons", "vendors",
            "group-products", "group-grades", "benefit-schedules", "waiting-periods", "eligibility",
            "ppd-ptd", "day-care", "consumables", "health-checkup", "chronic-opd",
            "surgical-sublimits", "vaccination-catalogs", "medical-device-catalogs",
            "users", "login-audits",
        ).forEach { assertTrue(it in ids, "missing entity descriptor: $it") }
    }

    @Test
    fun every_descriptor_belongs_to_exactly_one_hub_and_no_descriptor_is_orphaned() {
        // Each descriptor maps to a hub, and the union of all hubs' descriptors == the registry.
        val hubIds = registry.hubs.flatMap { hub -> hub.descriptors.map { it.id } }
        // No descriptor appears in two hubs.
        assertEquals(hubIds.size, hubIds.toSet().size, "a descriptor is registered in two hubs")
        // Every registered descriptor is reachable from some hub.
        registry.all.forEach { d ->
            assertTrue(d.id in hubIds.toSet(), "descriptor ${d.id} is not reachable from any hub")
        }
    }

    @Test
    fun unmapped_entity_falls_back_to_configuration_hub() {
        // hubOf returns CONFIGURATION for an id no HubGroup explicitly claims.
        assertEquals(
            com.rate.sdk.ui.operator.registry.HubGroup.CONFIGURATION,
            registry.hubOf("totally-unknown-future-entity"),
        )
        // Known ids resolve to their declared group.
        assertEquals(
            com.rate.sdk.ui.operator.registry.HubGroup.PARTNERS,
            registry.hubOf("vendors"),
        )
        assertEquals(
            com.rate.sdk.ui.operator.registry.HubGroup.PARTNERS,
            registry.hubOf("users"),
        )
    }

    @Test
    fun plan_descriptor_round_trips_through_the_form_buffer() {
        val d = registry.get("plans")!!
        val original = Plan(
            name = "PHI Basic",
            planType = PlanType.DOMESTIC,
            availableSumInsureds = listOf(1_000_000L, 2_500_000L),
            minAge = 5,
            maxAge = 99,
        )
        val buffer = d.read(original)
        assertEquals("PHI Basic", buffer["name"])
        assertEquals("1000000, 2500000", buffer["availableSumInsureds"])

        // Mutate a field and apply back.
        val edited = buffer + ("name" to "PHI Basic v2") + ("maxAge" to "100")
        val applied = d.applyEdit(original, edited) as Plan
        assertEquals("PHI Basic v2", applied.name)
        assertEquals(100, applied.maxAge)
        assertEquals(listOf(1_000_000L, 2_500_000L), applied.availableSumInsureds)
    }

    @Test
    fun plan_validation_catches_inverted_age_range() {
        val d = registry.get("plans")!!
        val errors = d.validate(mapOf("name" to "X", "minAge" to "60", "maxAge" to "30", "maxDiscountCap" to "0.3"))
        assertTrue(errors.any { it.contains("age") }, "expected an age-range error, got $errors")
    }

    @Test
    fun tenure_descriptor_parses_discount_fraction() {
        val d = registry.get("tenures")!!
        val base = Tenure(years = 2)
        val applied = d.applyEdit(base, d.read(base) + ("multiTenureDiscount" to "0.075")) as Tenure
        assertEquals(0.075, applied.multiTenureDiscount)
    }

    @Test
    fun serializer_is_exposed_for_every_descriptor() {
        registry.descriptors.forEach { assertNotNull(it.serializer, "no serializer for ${it.id}") }
    }
}
