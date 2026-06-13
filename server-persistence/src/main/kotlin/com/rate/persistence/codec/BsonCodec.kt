package com.rate.persistence.codec

import com.mongodb.MongoClientSettings
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import org.bson.BsonDateTime
import org.bson.BsonInt64
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.kotlinx.defaultSerializersModule

/**
 * Builds the ONE BSON codec registry the persistence layer uses for every collection.
 *
 * It is derived from [com.rate.core.base.json.AppJson] semantics: the
 * `classDiscriminator` is "_class" (matching `AppJson.json`'s `classDiscriminator`) so the
 * sealed hierarchies (`Quote` → RetailQuote/GroupQuote, `PolicyHolder` →
 * RetailProposer/GroupEmployer) round-trip between the REST wire format and BSON with the
 * same discriminator field.
 *
 * Custom codecs registered as CONTEXTUAL serializers (used by any `@Contextual` field and by
 * the standalone codecs registered on the registry):
 *  - [Instant] → BSON Date (`BsonDateTime`), millisecond precision — so date-range queries,
 *    `createdAt: -1` sorts and TTL bookkeeping behave as real dates.
 *  - [Money] → BSON Int64 (the canonical `paise` Long) — exact, single-scalar, index-friendly.
 *
 * NOTE: the SDK entity fields are plain (non-`@Contextual`) `Instant`/`Money`, so kotlinx
 * serialization resolves their COMPILED serializers, not these contextual ones. To keep
 * Instant/Money behaving like real BSON scalars at the DOCUMENT level regardless, the
 * repositories stamp the few index-critical date fields (TTL `_ttlAt`) as native BSON dates
 * directly; for content fields the compiled serializers' representation (ISO-8601 string for
 * Instant, `{paise: <long>}` sub-doc for Money) round-trips faithfully and ISO-8601 strings
 * sort correctly. See the module README "Money/Instant BSON representation" note.
 */
object BsonCodec {

    /** Mongo field name used everywhere for the BSON `_id`. */
    const val ID = "_id"

    @OptIn(ExperimentalSerializationApi::class)
    val serializersModule: SerializersModule =
        // overwriteWith (not +) because bson's defaultSerializersModule already registers a
        // contextual Instant serializer; `+` would throw on the duplicate. overwriteWith keeps
        // ObjectId/BsonValue/LocalDate from the default and (re)asserts Instant + adds Money.
        defaultSerializersModule.overwriteWith(
            SerializersModule {
                contextual(Instant::class, InstantAsBsonDate)
                contextual(Money::class, MoneyAsInt64)
            },
        )

    @OptIn(ExperimentalSerializationApi::class)
    private val bsonConfiguration: BsonConfiguration = BsonConfiguration(
        encodeDefaults = true,
        explicitNulls = false,
        classDiscriminator = "_class",
    )

    /**
     * The complete registry: the JVM default registry (primitives, Document, BsonValue, …)
     * with the kotlinx-serialization provider layered ON TOP so @Serializable data classes
     * encode/decode via their generated serializers + our contextual scalar codecs.
     */
    @OptIn(ExperimentalSerializationApi::class)
    val registry: CodecRegistry =
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                KotlinSerializerCodecProvider(
                    serializersModule = serializersModule,
                    bsonConfiguration = bsonConfiguration,
                ),
            ),
        )
}

/** kotlinx [Instant] ⇄ BSON `Date`. Millisecond precision (BSON Date is epoch-millis). */
@OptIn(ExperimentalSerializationApi::class)
object InstantAsBsonDate : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.rate.InstantAsBsonDate", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDateTime(value.toEpochMilliseconds()))
            else -> encoder.encodeLong(value.toEpochMilliseconds())
        }
    }

    override fun deserialize(decoder: Decoder): Instant = when (decoder) {
        is BsonDecoder -> Instant.fromEpochMilliseconds(decoder.decodeBsonValue().asDateTime().value)
        else -> Instant.fromEpochMilliseconds(decoder.decodeLong())
    }
}

/** [Money] ⇄ BSON `Int64` (the canonical paise Long). Exact, single-scalar, index-friendly. */
@OptIn(ExperimentalSerializationApi::class)
object MoneyAsInt64 : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.rate.MoneyAsInt64", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonInt64(value.paise))
            else -> encoder.encodeLong(value.paise)
        }
    }

    override fun deserialize(decoder: Decoder): Money = when (decoder) {
        is BsonDecoder -> Money(decoder.decodeBsonValue().asInt64().value)
        else -> Money(decoder.decodeLong())
    }
}
