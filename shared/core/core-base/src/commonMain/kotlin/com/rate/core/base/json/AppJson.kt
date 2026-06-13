package com.rate.core.base.json

import kotlinx.serialization.json.Json

/**
 * The ONE kotlinx-serialization [Json] configuration used everywhere: REST wire
 * format, on-disk caches, and (via bson-kotlinx in server-persistence) the BSON
 * codec registry. Frozen — changing these flags changes the wire contract.
 */
object AppJson {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        // Only used where polymorphic @Serializable hierarchies are declared.
        classDiscriminator = "_class"
    }
}
