package no.kartverket.altinnmanagement.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8090) { module() }.start(wait = true)
}

fun Application.module(registry: ResourceRegistry = ResourceRegistry.seeded(), tokens: Tokens = Tokens()) {
    routing {
        get("/health/live") { call.respond(HttpStatusCode.OK) }
        resourceRegistry(registry)
        maskinporten(Maskinporten(tokens))
        altinnAuthentication(tokens)
        filtering()
    }
}
