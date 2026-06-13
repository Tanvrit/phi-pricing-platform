package com.rate.persistence.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.codec.BsonCodec
import java.io.Closeable

/**
 * Owns the single process-wide [MongoClient] (the driver pools connections internally, so
 * exactly one instance must exist per process) and hands out the configured [MongoDatabase].
 *
 * The database is bound to the persistence-layer codec registry ([BsonCodec.registry]) so
 * every collection obtained from it encodes/decodes @Serializable entities through the
 * kotlinx-serialization BSON codecs. Construct ONCE (via the Koin module) and [close] on
 * shutdown.
 */
class MongoClientProvider(
    private val config: MongoConfig,
) : Closeable {

    val client: MongoClient by lazy {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(config.uri))
            .codecRegistry(BsonCodec.registry)
            .build()
        MongoClient.create(settings)
    }

    /** The application database, already wired to the kotlinx-serialization codec registry. */
    val database: MongoDatabase by lazy {
        client.getDatabase(config.database).withCodecRegistry(BsonCodec.registry)
    }

    override fun close() {
        client.close()
    }
}
