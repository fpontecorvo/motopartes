package org.motopartes.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.motopartes.api.dto.ErrorResponse
import org.motopartes.api.serialization.apiJson
import org.motopartes.repository.SettingsRepository

fun Route.authenticated(settingsRepo: SettingsRepository, allowLocalhostBypass: Boolean = true, build: Route.() -> Unit) {
    intercept(ApplicationCallPipeline.Plugins) {
        // Allow localhost without auth (disabled in tests)
        if (allowLocalhostBypass) {
            val remoteHost = call.request.local.remoteHost
            if (remoteHost == "127.0.0.1" || remoteHost == "0:0:0:0:0:0:0:1" || remoteHost == "localhost") {
                return@intercept
            }
        }

        val apiKey = call.request.header("X-API-Key")
        val validKey = settingsRepo.getApiKey()

        if (validKey == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("API key no configurada en el servidor"))
            finish()
            return@intercept
        }

        if (apiKey == null || apiKey != validKey) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("API key invalida o ausente"))
            finish()
            return@intercept
        }
    }
    build()
}
