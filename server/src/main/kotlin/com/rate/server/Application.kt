package com.rate.server

import com.rate.server.database.DatabaseFactory
import com.rate.server.plugins.configureHTTP
import com.rate.server.plugins.configureRouting
import com.rate.server.plugins.configureSerialization
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    configureSerialization()
    configureHTTP()
    configureRouting()
}
