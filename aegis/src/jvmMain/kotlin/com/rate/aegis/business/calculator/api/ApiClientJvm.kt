package com.rate.aegis.business.calculator.api

import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * JVM-only Excel-upload extension. WASM target can't `File.readBytes()` from disk;
 * if/when we add a browser upload flow it'll take a `ByteArray` + filename.
 */
suspend fun ApiClient.uploadExcel(file: File): Map<String, JsonElement> =
    http.post("$baseUrl/api/import/upload") {
        setBody(MultiPartFormDataContent(formData {
            append("file", file.readBytes(), Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                append(HttpHeaders.ContentType, "application/vnd.ms-excel")
            })
        }))
    }.body()
