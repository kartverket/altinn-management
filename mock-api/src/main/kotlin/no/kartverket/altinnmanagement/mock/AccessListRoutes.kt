package no.kartverket.altinnmanagement.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

private const val ACCESS_LIST_PATH = "/resourceregistry/api/v1/access-lists"

private val READ_SCOPES = setOf(
    "altinn:resourceregistry/resource.admin",
    "altinn:resourceregistry/accesslist.read",
    "altinn:resourceregistry/accesslist.write",
    "altinn:resourceregistry/pdp:accesslist.read",
)

// These may read any owner's lists
private val OWNER_EXEMPT_SCOPES = setOf("altinn:resourceregistry/resource.admin", "altinn:resourceregistry/pdp:accesslist.read")

/**
 * The read routes of Altinn's access lists. Unlike resources, they need an Altinn token with one of [READ_SCOPES], for
 * the owner in the path. The mock's tokens are all Kartverket's, so the owner must be `kv`.
 */
fun Route.accessLists(lists: AccessLists, tokens: Tokens) {
    get("$ACCESS_LIST_PATH/{owner}") {
        val owner = call.reader(tokens) ?: return@get
        val from = call.request.queryParameters["token"]?.let { raw ->
            decodeToken(raw) ?: return@get call.validationProblem(mapOf("token" to listOf("The value '$raw' is not valid.")))
        }
        val (page, next) = if (owner == KARTVERKET_ORGCODE) lists.page(from) else emptyList<Nothing>() to null
        val body = buildJsonObject {
            // Altinn leaves out a null, so the last page has no next
            putJsonObject("links") { next?.let { put("next", call.nextPageUrl(owner, it)) } }
            put("data", JsonArray(page))
        }
        call.respondJson(HttpStatusCode.OK, body)
    }

    get("$ACCESS_LIST_PATH/{owner}/{identifier}") {
        val owner = call.reader(tokens) ?: return@get
        val list = if (owner == KARTVERKET_ORGCODE) lists.get(call.pathParameters["identifier"]!!) else null
        if (list == null) return@get call.notFound()
        call.respondJson(HttpStatusCode.OK, list)
    }
}

private const val KARTVERKET_ORGCODE = "kv"

/** The owner in the path in lower case, like Altinn turns an org code into its own, or null after answering 401 or 403. */
private suspend fun ApplicationCall.reader(tokens: Tokens): String? {
    val claims = bearerClaims(tokens)
    if (claims == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    val scopes = claims.scopes()
    val owner = parameters["owner"]!!.lowercase()
    if (scopes.none { it in READ_SCOPES } || (owner != KARTVERKET_ORGCODE && scopes.none { it in OWNER_EXEMPT_SCOPES })) {
        respond(HttpStatusCode.Forbidden)
        return null
    }
    return owner
}

// Altinn's page token is the identifier the next page starts at, as a JSON string in unpadded base64url
private fun encodeToken(identifier: String) =
    Base64.getUrlEncoder().withoutPadding().encodeToString(JsonPrimitive(identifier).toString().toByteArray())

private fun decodeToken(token: String): String? = try {
    (Json.parseToJsonElement(Base64.getUrlDecoder().decode(token).decodeToString()) as? JsonPrimitive)?.takeIf { it.isString }?.content
} catch (_: IllegalArgumentException) {
    null
} catch (_: SerializationException) {
    null
}

// Altinn writes the link to the next page as an absolute URL
private fun ApplicationCall.nextPageUrl(owner: String, from: String): String {
    val origin = request.origin
    return "${origin.scheme}://${origin.serverHost}:${origin.serverPort}$ACCESS_LIST_PATH/$owner?token=${encodeToken(from)}"
}
