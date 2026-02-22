package com.rate.server.routes

import com.rate.server.import.ExcelImporter
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream

@Serializable internal data class ImportErrorResponse(val error: String)
@Serializable internal data class ImportMessageResponse(val message: String)

fun Route.importRoutes() {
    val importer = ExcelImporter()

    route("/api/import") {

        // Upload an Excel file to import rates
        post("/upload") {
            val parts = call.receiveMultipart()
            var imported = false
            parts.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val bytes = part.provider().readRemaining().readByteArray()
                    ByteArrayInputStream(bytes).use { stream ->
                        importer.importFromExcel(stream)
                        imported = true
                    }
                }
                part.dispose()
            }
            if (imported) {
                call.respond(HttpStatusCode.OK, ImportMessageResponse("Excel data imported successfully"))
            } else {
                call.respond(HttpStatusCode.BadRequest, ImportErrorResponse("No file provided"))
            }
        }
    }
}
