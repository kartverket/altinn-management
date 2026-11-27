package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal fun mockApi(
    registry: ResourceRegistry = ResourceRegistry.seeded(),
    lists: AccessLists = AccessLists.seeded(),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { module(registry, lists = lists) }
    block()
}

/** A JWT grant for the mock's Maskinporten. The mock does not check signatures, so this one has a made-up one. */
internal fun grant(
    scope: String? = "altinn:resourceregistry/resource.write",
    aud: String? = "http://localhost:8090/",
    iss: String? = "dev-client",
    iat: Instant? = Instant.now(),
    exp: Instant? = Instant.now().plusSeconds(60),
    jti: String? = UUID.randomUUID().toString(),
): String {
    val claims = buildJsonObject {
        scope?.let { put("scope", it) }
        aud?.let { put("aud", it) }
        iss?.let { put("iss", it) }
        iat?.let { put("iat", it.epochSecond) }
        exp?.let { put("exp", it.epochSecond) }
        jti?.let { put("jti", it) }
    }
    return "${base64Url(buildJsonObject { put("alg", "RS256") })}.${base64Url(claims)}.c2lnbmF0dXJl"
}

/** An Altinn token from the mock, the way the server gets one: a Maskinporten token, exchanged. */
internal suspend fun ApplicationTestBuilder.altinnToken(scope: String = "altinn:resourceregistry/resource.write"): String {
    val maskinporten = client.submitForm(
        "/token",
        parameters {
            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            append("assertion", grant(scope = scope))
        },
    ).json().jsonObject.getValue("access_token").jsonPrimitive.content
    return client.get("/authentication/api/v1/exchange/maskinporten") { header(HttpHeaders.Authorization, "Bearer $maskinporten") }.bodyAsText()
}

/** The claims of a JWT. */
internal fun claimsOf(jwt: String): JsonObject =
    Json.parseToJsonElement(Base64.getUrlDecoder().decode(jwt.split('.')[1]).decodeToString()).jsonObject

private fun base64Url(json: JsonObject) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())

internal suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())

/** A version of a MaskinportenSchema resource, as Altinn returns it. */
internal fun resource(
    id: String,
    versionId: Long,
    scope: String = "kartverk:$id",
    organization: String = "971040238",
    extra: Map<String, JsonElement> = emptyMap(),
): JsonObject = buildJsonObject {
    put("identifier", id)
    putJsonObject("title") { put("en", id); put("nb", id); put("nn", id) }
    put("resourceType", "MaskinportenSchema")
    put(
        "resourceReferences",
        buildJsonArray {
            add(buildJsonObject { put("referenceSource", "Default"); put("reference", scope); put("referenceType", "MaskinportenScope") })
        },
    )
    putJsonObject("hasCompetentAuthority") { put("organization", organization); put("orgcode", "kv") }
    extra.forEach { (key, value) -> put(key, value) }
    put("versionId", versionId)
    put("createdAt", "2026-01-0${versionId % 9 + 1}T00:00:00+00:00")
    put("updatedAt", "2026-02-0${versionId % 9 + 1}T00:00:00+00:00")
}
