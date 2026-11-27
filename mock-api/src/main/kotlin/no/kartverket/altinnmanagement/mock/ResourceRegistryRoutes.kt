package no.kartverket.altinnmanagement.mock

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.SecureRandom
import java.util.HexFormat

private const val RESOURCE_PATH = "/resourceregistry/api/v1/resource"
private const val WRITE_SCOPE = "altinn:resourceregistry/resource.write"
private const val ADMIN_SCOPE = "altinn:resourceregistry/resource.admin"

/**
 * The routes of Altinn's Resource Registry. Like in Altinn, anyone may read, and a write needs an Altinn token with the
 * scope [WRITE_SCOPE] or [ADMIN_SCOPE].
 */
fun Route.resourceRegistry(registry: ResourceRegistry, tokens: Tokens) {
    get("$RESOURCE_PATH/{id}") {
        val id = call.pathParameters["id"]!!
        // Altinn's routes ignore case, and its fixed routes win over an id
        if (id.equals("search", ignoreCase = true)) return@get call.search(registry)

        val versionId = call.query("versionId")?.let { raw ->
            raw.toLongOrNull() ?: return@get call.validationProblem(mapOf("versionId" to listOf("The value '$raw' is not valid.")))
        }
        val resource = registry.get(id, versionId) ?: return@get call.notFound()
        call.respondJson(HttpStatusCode.OK, resource)
    }

    post(RESOURCE_PATH) {
        val claims = call.writer(tokens) ?: return@post
        val resource = call.receiveResource() ?: return@post
        val id = resource.text("identifier")
        // Altinn throws on these before it gets to say what is wrong
        if (id == null || resource["hasCompetentAuthority"] == null) return@post call.internalError()
        if (!call.isValid(resource, isNew = true, claims)) return@post
        if (!registry.create(resource)) return@post call.respondJson(HttpStatusCode.Conflict, JsonPrimitive("The Resource already exist: $id"))

        call.response.header(HttpHeaders.Location, "$RESOURCE_PATH/$id")
        call.respond(HttpStatusCode.Created)
    }

    put("$RESOURCE_PATH/{id}") {
        val claims = call.writer(tokens) ?: return@put
        val id = call.pathParameters["id"]!!
        if (call.request.contentType().withoutParameters() != ContentType.Application.Json) return@put call.unsupportedMediaType()
        if (registry.get(id) == null) return@put call.notFound()
        val resource = call.receiveResource() ?: return@put
        if (resource.text("identifier") != id) return@put call.respondJson(HttpStatusCode.BadRequest, JsonPrimitive("Id in path does not match ID in resource"))
        val missing = REQUIRED.filter { (field, _) -> resource[field] == null }
        if (missing.isNotEmpty()) return@put call.validationProblem(missing.associate { (_, name) -> name to listOf("The $name field is required.") })
        if (!call.isValid(resource, isNew = false, claims)) return@put

        registry.update(resource)
        call.respond(HttpStatusCode.OK)
    }

    route("$RESOURCE_PATH/{id}/policy") {
        get {
            val id = call.pathParameters["id"]!!
            if (registry.get(id) == null) return@get call.respondText("Unable to find resource", status = HttpStatusCode.NotFound)
            val policy = registry.policy(id) ?: return@get call.respondText("Unable to find requested policy", status = HttpStatusCode.NotFound)
            call.respondBytes(policy, ContentType.Text.Xml)
        }
        // Altinn takes a policy with POST and with PUT, and does the same with both
        post { writePolicy(registry, tokens) }
        put { writePolicy(registry, tokens) }
    }
}

// The fields a PUT must have. On a POST, Altinn does not check them
private val REQUIRED = listOf(
    "title" to "Title",
    "description" to "Description",
    "contactPoints" to "ContactPoints",
    "hasCompetentAuthority" to "HasCompetentAuthority",
)

private suspend fun RoutingContext.writePolicy(registry: ResourceRegistry, tokens: Tokens) {
    val claims = call.writer(tokens) ?: return
    if (!call.request.contentType().match(ContentType.MultiPart.FormData)) return call.unsupportedMediaType()
    val id = call.pathParameters["id"]!!
    var policy: ByteArray? = null
    call.receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && part.name == "policyFile") policy = part.provider().toByteArray()
        part.dispose()
    }
    val file = policy ?: return call.respondJson(HttpStatusCode.BadRequest, JsonPrimitive("The policy file can not be null"))
    val resource = registry.get(id) ?: return call.respondJson(HttpStatusCode.BadRequest, JsonPrimitive("Unknown resource"))
    // Unlike a write of the resource, Altinn checks the owner it has, not the one in the request
    if (!hasWriteAccess(resource.owner(), claims)) return call.respond(HttpStatusCode.Forbidden)
    val problem = try {
        policyProblem(file, id)
    } catch (e: UnreadablePolicyException) {
        return call.internalError()
    }
    if (problem != null) return call.respondJson(HttpStatusCode.BadRequest, JsonPrimitive(problem))

    registry.setPolicy(id, file)
    call.response.header(HttpHeaders.Location, "$id/policy")
    call.respond(HttpStatusCode.Created)
}

/** Answers 401 or 403, and returns null, unless the call has an Altinn token the mock issued, with a scope to write. */
private suspend fun ApplicationCall.writer(tokens: Tokens): JsonObject? {
    val claims = bearerClaims(tokens)
    if (claims == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    val scopes = claims.scopes()
    if (WRITE_SCOPE !in scopes && ADMIN_SCOPE !in scopes) {
        respond(HttpStatusCode.Forbidden)
        return null
    }
    return claims
}

/** The claims of the Altinn token in the request, or null when it has none the mock issued. */
internal fun ApplicationCall.bearerClaims(tokens: Tokens): JsonObject? = request.headers[HttpHeaders.Authorization]
    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
    ?.substring("Bearer ".length)?.trim()
    ?.let(tokens::altinnClaims)

/** The resource in the request as Altinn would keep it, or null after answering 415 or 400 when Altinn could not read it. */
private suspend fun ApplicationCall.receiveResource(): JsonObject? {
    if (request.contentType().withoutParameters() != ContentType.Application.Json) {
        unsupportedMediaType()
        return null
    }
    val body = try {
        Json.parseToJsonElement(receiveText()) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: run {
        validationProblem(mapOf("$" to listOf("The JSON value could not be converted to ServiceResource. Path: $")))
        return null
    }
    return try {
        ServiceResource.read(body)
    } catch (e: JsonFieldException) {
        validationProblem(mapOf(e.path to listOf(e.message!!)))
        null
    }
}

/** Altinn's checks of a resource to write, after the ones it does first. Answers when the resource fails one. */
private suspend fun ApplicationCall.isValid(resource: JsonObject, isNew: Boolean, claims: JsonObject): Boolean {
    val owner = resource.owner()
    val orgCode = try {
        Organizations.orgCode(owner)
    } catch (e: IllegalStateException) {
        internalError()
        return false
    }
    val messages = ServiceResource.validate(resource, isNew, orgCode)
    if (messages.isNotEmpty()) {
        validationProblem(messages)
        return false
    }
    // Altinn checks the owner in the request, also when it changes a resource another organisation owns
    if (!hasWriteAccess(owner, claims)) {
        respond(HttpStatusCode.Forbidden)
        return false
    }
    val unauthorizedScopes = scopesWithoutPrefix(resource, claims)
    if (unauthorizedScopes.isNotEmpty()) {
        respondJson(
            HttpStatusCode.Unauthorized,
            buildJsonObject {
                put("title", "Unauthorized")
                put("status", 401)
                put("detail", "Not authorized for creating resource with the listed scopes")
                putJsonObject("errors") { put("InvalidPrefix", JsonArray(unauthorizedScopes.map(::JsonPrimitive))) }
            },
        )
        return false
    }
    return true
}

// Altinn's own check, in its default setup: the admin scope may write anyone's resources, and otherwise the token's
// consumer must be the owner
private fun hasWriteAccess(owner: String?, claims: JsonObject): Boolean {
    if (claims.text("scope")?.contains(ADMIN_SCOPE, ignoreCase = true) == true) return true
    val consumer = claims["consumer"]?.jsonObject?.text("ID") ?: return false
    return consumer.startsWith("0192:") && consumer.substringAfter(':').equals(owner, ignoreCase = true)
}

// Altinn's check of scope prefixes in its default setup: unless the token has the admin scope, every Maskinporten scope
// of the resource must start with one of the token's consumer_prefix values and a colon
private fun scopesWithoutPrefix(resource: JsonObject, claims: JsonObject): List<String> {
    val scopes = (resource["resourceReferences"] as? JsonArray).orEmpty()
        .map { it.jsonObject }
        .filter { it.text("referenceType") == "MaskinportenScope" }
        .mapNotNull { it.text("reference") }
    if (scopes.isEmpty() || ADMIN_SCOPE in claims.scopes()) return emptyList()
    val prefixes = when (val prefix = claims["consumer_prefix"]) {
        is JsonArray -> prefix.map { it.jsonPrimitive.content }
        is JsonPrimitive -> listOf(prefix.content)
        else -> emptyList()
    }
    return scopes.filter { scope -> prefixes.none { scope.startsWith("$it:") } }
}

private suspend fun ApplicationCall.search(registry: ResourceRegistry) {
    // Like Altinn, a type's name in any case or its number. ASP.NET words its refusals of the two kinds differently
    val resourceType = query("resourceType")?.let { raw ->
        RESOURCE_TYPES.find { (name, number) -> name.equals(raw, ignoreCase = true) || "$number" == raw }?.first
            ?: return validationProblem(
                mapOf(
                    "ResourceType" to listOf(
                        if (raw.toIntOrNull() == null) "The value '$raw' is not valid for ResourceType." else "The value '$raw' is invalid.",
                    ),
                ),
            )
    }
    val filter = SearchFilter(
        id = query("id"),
        resourceType = resourceType,
        orgCode = query("orgCode"),
        organizationId = query("organizationId"),
        description = query("description"),
        keyword = query("keyword"),
        reference = query("reference"),
    )
    respondJson(HttpStatusCode.OK, JsonArray(registry.search(filter)))
}

// Altinn binds query parameters whatever their case, and treats an empty value as none
private fun ApplicationCall.query(name: String): String? = request.queryParameters.entries()
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value?.firstOrNull()?.takeIf { it.isNotEmpty() }

private fun JsonObject.owner() = this["hasCompetentAuthority"]?.jsonObject?.text("organization")

internal fun JsonObject.scopes() = text("scope")?.split(' ').orEmpty()

private fun JsonObject.text(field: String) = (this[field] as? JsonPrimitive)?.contentOrNull

// Altinn's ASP.NET answers its errors as ProblemDetails, but sends them as application/json like everything else
internal suspend fun ApplicationCall.notFound() = problem(HttpStatusCode.NotFound, "https://tools.ietf.org/html/rfc9110#section-15.5.5", "Not Found")

private suspend fun ApplicationCall.unsupportedMediaType() =
    problem(HttpStatusCode.UnsupportedMediaType, "https://tools.ietf.org/html/rfc9110#section-15.5.16", "Unsupported Media Type")

private suspend fun ApplicationCall.problem(status: HttpStatusCode, type: String, title: String) = respondJson(
    status,
    buildJsonObject {
        put("type", type)
        put("title", title)
        put("status", status.value)
        put("traceId", traceId())
    },
)

internal suspend fun ApplicationCall.validationProblem(errors: Map<String, List<String>>) = respondJson(
    HttpStatusCode.BadRequest,
    buildJsonObject {
        put("type", "https://tools.ietf.org/html/rfc9110#section-15.5.1")
        put("title", "One or more validation errors occurred.")
        put("status", 400)
        putJsonObject("errors") {
            errors.forEach { (key, messages) -> put(key, buildJsonArray { messages.forEach { add(JsonPrimitive(it)) } }) }
        }
        put("traceId", traceId())
    },
)

// What Altinn answers when it throws: the only error it sends as application/problem+json
private suspend fun ApplicationCall.internalError() = respondText(
    buildJsonObject {
        put("type", "https://tools.ietf.org/html/rfc9110#section-15.6.1")
        put("title", "An error occurred while processing your request.")
        put("status", 500)
        put("traceId", traceId())
    }.toString(),
    ContentType.Application.ProblemJson.withCharset(Charsets.UTF_8),
    HttpStatusCode.InternalServerError,
)

internal suspend fun ApplicationCall.respondJson(status: HttpStatusCode, body: JsonElement) =
    respondText(body.toString(), ContentType.Application.Json.withCharset(Charsets.UTF_8), status)

private val random = SecureRandom()

internal fun traceId(): String {
    fun hex(bytes: Int) = HexFormat.of().formatHex(ByteArray(bytes).also(random::nextBytes))
    return "00-${hex(16)}-${hex(8)}-00"
}
