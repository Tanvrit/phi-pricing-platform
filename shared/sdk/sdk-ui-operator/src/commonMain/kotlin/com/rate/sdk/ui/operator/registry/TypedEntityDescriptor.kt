package com.rate.sdk.ui.operator.registry

import com.rate.core.base.model.ConfigEntity
import com.rate.sdk.ui.operator.model.EntityDescriptor
import com.rate.sdk.ui.operator.model.FormBuffer
import com.rate.sdk.ui.operator.model.FormField
import kotlinx.serialization.KSerializer

/**
 * The typed bridge between the per-entity closures (which know `T`) and the heterogeneous,
 * `Any`-typed [EntityDescriptor] the screen/ViewModel consume. Each catalog/admin entity builds
 * one of these; a registry holds a `List<TypedEntityDescriptor<*>>`.
 *
 * It captures `T`, the [serializer] (so [com.rate.sdk.ui.operator.network.ConfigAdminApi] can
 * encode/decode without a `reified`), and the form closures, then implements the `Any`-boundary
 * [EntityDescriptor] by unchecked-casting at the edges — safe because the registry only ever hands
 * a descriptor instances of its own `T`.
 */
class TypedEntityDescriptor<T : ConfigEntity>(
    override val id: String,
    override val singular: String,
    override val plural: String,
    override val description: String,
    override val isGroup: Boolean,
    val serializer: KSerializer<T>,
    override val fields: List<FormField>,
    private val columns: List<Pair<String, (T) -> String>>,
    private val factory: () -> T,
    private val reader: (T) -> FormBuffer,
    private val editor: (T, FormBuffer) -> T,
    private val validator: (FormBuffer) -> List<String> = { emptyList() },
) : EntityDescriptor {

    @Suppress("UNCHECKED_CAST")
    private fun cast(entity: Any): T = entity as T

    override val listColumns: List<Pair<String, (Any) -> String>> =
        columns.map { (header, extract) -> header to { e: Any -> extract(cast(e)) } }

    override fun idOf(entity: Any): String = cast(entity).id
    override fun titleOf(entity: Any): String = columns.firstOrNull()?.second?.invoke(cast(entity)) ?: cast(entity).id
    override fun versionOf(entity: Any): Long = cast(entity).v
    override fun statusOf(entity: Any): String = cast(entity).status.name
    override fun isDeleted(entity: Any): Boolean = cast(entity).isDeleted

    override fun newInstance(): Any = factory()
    override fun read(entity: Any): FormBuffer = reader(cast(entity))
    override fun applyEdit(base: Any, buffer: FormBuffer): Any = editor(cast(base), buffer)
    override fun validate(buffer: FormBuffer): List<String> = validator(buffer)
}
