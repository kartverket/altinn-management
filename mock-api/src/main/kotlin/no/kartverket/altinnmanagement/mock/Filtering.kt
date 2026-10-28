package no.kartverket.altinnmanagement.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

const val CLIENT_CERTIFICATE_HEADER = "X-Forwarded-Client-Cert"

data class Filter(val exact: List<String> = emptyList(), val prefix: List<String> = emptyList())

data class Access(val scopes: Filter = Filter(), val resources: Filter = Filter())

val testApps = mapOf(
    "tilgangsstyring/demo" to Access(
        scopes = Filter(exact = listOf("kartverk:dokumentbestilling", "kartverk:nrl.rapportering"), prefix = listOf("kartverk:tilgangsstyring/")),
        resources = Filter(prefix = listOf("nrl-")),
    ),
    "matrikkel/matrikkel-api" to Access(
        scopes = Filter(exact = listOf("kartverk:matrikkel:brukernavn")),
        resources = Filter(exact = listOf("kv-matrikkel-brukernavn")),
    ),
)

private val spiffeUri = Regex("""URI=spiffe://[^/;,"]+/ns/([^/;,"]+)/sa/([^/;,"]+)""")

// The last URI, like OPA, since Istio appends the caller it verified after anything the caller sent itself
fun appFrom(clientCertificate: String?): String? =
    clientCertificate?.let { spiffeUri.findAll(it).lastOrNull() }?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }

/** Plays OPA as the filtering service, in OPA's format: the input in, the policy's answer back as `result`. */
fun Route.filtering() {
    post("/filtering") {
        val headers = runCatching {
            Json.parseToJsonElement(call.receiveText()).jsonObject.getValue("input").jsonObject.getValue("headers").jsonObject
        }.getOrNull()
        val clientCertificate = headers?.entries
            ?.firstOrNull { it.key.equals(CLIENT_CERTIFICATE_HEADER, ignoreCase = true) }
            ?.let { runCatching { it.value.jsonPrimitive.content }.getOrNull() }
        val app = appFrom(clientCertificate)
            ?: return@post call.respondText(
                "input.headers must have ${CLIENT_CERTIFICATE_HEADER.lowercase()} with a SPIFFE URI",
                status = HttpStatusCode.BadRequest,
            )
        val access = testApps[app] ?: Access()
        call.respondJson(
            HttpStatusCode.OK,
            buildJsonObject {
                putJsonObject("result") {
                    put("scopes", access.scopes.toJson())
                    put("resources", access.resources.toJson())
                }
            },
        )
    }
}

private fun Filter.toJson() = JsonObject(
    mapOf("exact" to JsonArray(exact.map(::JsonPrimitive)), "prefix" to JsonArray(prefix.map(::JsonPrimitive))),
)
