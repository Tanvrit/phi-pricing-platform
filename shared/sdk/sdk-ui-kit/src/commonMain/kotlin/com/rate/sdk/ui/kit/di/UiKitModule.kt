package com.rate.sdk.ui.kit.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for sdk-ui-kit.
 *
 * The design system is purely presentational — themes, tokens, components and
 * the i18n catalog are all stateless composables / objects, so there is nothing
 * stateful to wire here yet. This module exists to keep the DI surface uniform
 * across every sdk module: hosts can `modules(uiKitModule, …)` without special-
 * casing the kit, and any future kit-level singleton (a theme/locale preference
 * holder, an in-process toast controller, …) gets a home without churning the
 * host wiring.
 */
fun uiKitModule(): Module = module {
}
