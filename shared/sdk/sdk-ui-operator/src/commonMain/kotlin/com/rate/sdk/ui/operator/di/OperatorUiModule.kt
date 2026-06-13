package com.rate.sdk.ui.operator.di

import com.rate.core.network.client.TanvritClient
import com.rate.sdk.ui.operator.OperatorContext
import com.rate.sdk.ui.operator.OperatorRole
import com.rate.sdk.ui.operator.network.AuditReadApi
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.network.GroupQuotingApi
import com.rate.sdk.ui.operator.registry.ConfigEntityRegistry
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for sdk-ui-operator.
 *
 * Binds the operator-console singletons that are constructible in pure-KMP commonMain from the
 * shared [TanvritClient] (which the app shell binds): the entity [ConfigEntityRegistry], the generic
 * admin-CRUD client, the audit/group read clients, and the [OperatorContext] (built for the
 * [role]/[actor] the host supplies). The Compose composables ([com.rate.sdk.ui.operator.OperatorConsole])
 * resolve the context from this graph or are handed one explicitly.
 *
 * @param role  the operator role this console runs as.
 * @param actor optional operator identity for audit attribution.
 */
fun operatorUiModule(
    role: OperatorRole = OperatorRole.BUSINESS,
    actor: String? = null,
): Module = module {
    single { ConfigEntityRegistry.default() }
    single { ConfigAdminApi(get<TanvritClient>()) }
    single { AuditReadApi(get<TanvritClient>()) }
    single { GroupQuotingApi(get<TanvritClient>()) }
    single {
        OperatorContext(
            client = get<TanvritClient>(),
            role = role,
            actor = actor,
            registry = get<ConfigEntityRegistry>(),
        )
    }
}
