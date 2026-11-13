package no.kartverket.altinnmanagement.client

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.client.auth.AccessTokenProvider
import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.exception.AltinnRejectedException
import no.kartverket.altinnmanagement.client.http.Http
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import java.net.URI

/**
 * Reads Altinn's resources without a token, and changes them with a token from [accessTokenProvider], such as a
 * [no.kartverket.altinnmanagement.client.auth.MaskinportenAltinnTokenProvider]. A client that only reads needs none.
 */
public class ResourceRegistryClient(
    platformBaseUrl: URI,
    private val httpClient: OutgoingHttpClient,
    private val accessTokenProvider: AccessTokenProvider? = null,
) {
    private val platformBaseUrl = platformBaseUrl.toString()

    public suspend fun getMaskinportenSchema(id: ResourceId): MaskinportenSchema? {
        val (_, resource) = getResource(id) ?: return null
        return if (resource.isMaskinportenSchema) resource.toMaskinportenSchema(id) else null
    }

    /**
     * Replaces [content] in the MaskinportenSchema with the id [id], and keeps the rest of it as it is in Altinn. Returns
     * the MaskinportenSchema as it was sent to Altinn, or null when Kartverket has no MaskinportenSchema with this id.
     * Throws [AltinnRejectedException] when Altinn would not take the change, and [IllegalStateException] when this
     * client has no [AccessTokenProvider].
     */
    public suspend fun updateMaskinportenSchema(id: ResourceId, content: MaskinportenSchemaContent): MaskinportenSchema? {
        val accessTokenProvider = checkNotNull(accessTokenProvider) { "Changing a resource needs an AccessTokenProvider" }
        val (json, resource) = getResource(id) ?: return null
        if (!resource.isMaskinportenSchema || !resource.isKartverkets) return null
        val changed = json.withContent(content)
        val token = accessTokenProvider.accessToken()
        val request = OutgoingRequest(
            method = HttpMethod.PUT,
            url = resourceUrl(id),
            headers = mapOf("Authorization" to "Bearer ${token.value}", "Content-Type" to "application/json"),
            body = changed.toString().encodeToByteArray(),
        )
        try {
            Http.sendExpectingOk(httpClient, request, TARGET, ::AltinnException)
        } catch (e: AltinnException) {
            rejection(e)?.let { throw it }
            if (e.statusCode == 401) accessTokenProvider.refused(token)
            throw e
        }
        return Http.json.decodeFromJsonElement(AltinnResource.serializer(), changed).toMaskinportenSchema(id)
    }

    public suspend fun listMaskinportenSchemas(): List<MaskinportenSchema> {
        val request = OutgoingRequest(
            method = HttpMethod.GET,
            url = Http.url(
                platformBaseUrl,
                "$RESOURCE_PATH/search?organizationId=$KARTVERKET_ORGANIZATION_NUMBER&resourceType=$MASKINPORTEN_SCHEMA",
            ),
            headers = mapOf("Accept" to "application/json"),
        )
        val response = Http.sendExpectingOk(httpClient, request, TARGET, ::AltinnException)
        val body = response.body.decodeToString()
        return try {
            Http.json.decodeFromString(ListSerializer(AltinnResource.serializer()), body).map { resource ->
                val id = requireNotNull(resource.identifier) { "A resource has no identifier" }
                resource.toMaskinportenSchema(ResourceId.of(id))
            }
        } catch (e: IllegalArgumentException) {
            throw AltinnException("Failed to parse the resources from $TARGET: ${e.message}", response.statusCode, body, e)
        }
    }

    // Whole, since a change must send back what this client does not read
    private suspend fun getResource(id: ResourceId): Pair<JsonObject, AltinnResource>? {
        if (id.value in FIXED_ROUTES) return null
        val request = OutgoingRequest(HttpMethod.GET, resourceUrl(id), mapOf("Accept" to "application/json"))
        val response = try {
            Http.sendExpectingOk(httpClient, request, TARGET, ::AltinnException)
        } catch (e: AltinnException) {
            if (e.statusCode == 404) return null
            throw e
        }
        val body = response.body.decodeToString()
        return try {
            val json = Http.json.parseToJsonElement(body).jsonObject
            json to Http.json.decodeFromJsonElement(AltinnResource.serializer(), json)
        } catch (e: IllegalArgumentException) {
            throw AltinnException("Failed to parse the resource from $TARGET: ${e.message}", response.statusCode, body, e)
        }
    }

    private fun resourceUrl(id: ResourceId) = Http.url(platformBaseUrl, "$RESOURCE_PATH/${id.value}")

    internal companion object {
        const val RESOURCE_PATH = "/resourceregistry/api/v1/resource"

        private val FIXED_ROUTES = setOf("resourcelist", "export", "search", "updated", "changes", "orgs")

        private const val TARGET = "Altinn Resource Registry"

        /**
         * Altinn answers 400 to a resource it will not take, and 401 with `InvalidPrefix` to scopes that do not start
         * with one of Kartverket's prefixes. Any other error is not about the resource.
         */
        private fun rejection(e: AltinnException): AltinnRejectedException? {
            val body = e.responseBody?.let { runCatching { Http.json.parseToJsonElement(it) }.getOrNull() }
            val errors = (body as? JsonObject)?.get("errors") as? JsonObject
            val isRejected = e.statusCode == 400 || (e.statusCode == 401 && errors?.containsKey("InvalidPrefix") == true)
            if (!isRejected) return null

            // Altinn answers with ProblemDetails, whose errors are messages by field, or with a JSON string
            val reasons = errors?.map { (field, messages) -> "$field: ${texts(messages).joinToString(", ")}" }
                ?: listOfNotNull((body as? JsonPrimitive)?.content)
            return AltinnRejectedException(reasons, e.statusCode, e.responseBody)
        }

        private fun texts(element: JsonElement): List<String> =
            (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
    }
}
