package no.kartverket.altinnmanagement.restserver

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import kotlinx.serialization.SerializationException
import no.kartverket.altinnmanagement.client.MaskinportenSchemaContent
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchemaRequest
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

internal class InvalidRequestException(val errors: List<FieldError>) : RuntimeException(errors.joinToString("; ") { it.message })

internal class MalformedRequestException(message: String) : RuntimeException(message)

internal fun clientCertificate(value: String?, required: Boolean): String? {
    val certificate = value?.takeIf { it.isNotBlank() }
    if (required && certificate == null) {
        throw InvalidRequestException(
            listOf(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")),
        )
    }
    return certificate
}

internal fun scope(values: List<String>?): String? {
    if (values == null) return null
    val scope = values.singleOrNull() ?: invalidScope("scope must be sent once")
    if (scope.isBlank()) invalidScope("scope must not be blank")
    if (scope.any(Char::isWhitespace)) invalidScope("scope must not contain whitespace")
    return scope
}

private fun invalidScope(message: String): Nothing =
    throw InvalidRequestException(listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, message)))

internal fun resourceId(name: String, value: String): ResourceId = try {
    ResourceId.of(value)
} catch (e: IllegalArgumentException) {
    throw InvalidRequestException(
        listOf(FieldError(name, ValidationCode.INVALID_FORMAT, "$name must be 4 or more of a-z, 0-9, _ and -")),
    )
}

// Checks what Altinn would refuse, so that a caller gets every error at once, by this API's field names
internal fun maskinportenSchemaContent(id: ResourceId, request: MaskinportenSchemaRequest): MaskinportenSchemaContent {
    val errors = buildList {
        if (request.resourceId != null && request.resourceId != id.value) {
            add(FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be the id in the path"))
        }
        if (request.scopes.isNullOrEmpty()) add(missing("scopes"))
        request.scopes?.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                add(FieldError("scopes[$index]", ValidationCode.INVALID_FORMAT, "scopes[$index] must not be blank"))
            }
        }
        addAll(textErrors("title", request.title))
        addAll(textErrors("description", request.description))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return MaskinportenSchemaContent(
        scopes = request.scopes!!,
        title = request.title!!.toClient(),
        description = request.description!!.toClient(),
        delegationDescription = request.delegationDescription?.toClient(),
        homepage = request.homepage,
        contactPoints = request.contactPoints.orEmpty().map { it.toClient() },
        keywords = request.keywords.orEmpty().map { it.toClient() },
    )
}

private fun textErrors(field: String, text: MultilingualText?): List<FieldError> {
    if (text == null) return listOf(missing(field))
    return listOf("en" to text.en, "nb" to text.nb, "nn" to text.nn)
        .filter { (_, value) -> value.isNullOrBlank() }
        .map { (language, _) -> missing("$field.$language") }
}

private fun missing(field: String) = FieldError(field, ValidationCode.MISSING, "$field is required")

internal suspend fun ApplicationCall.maskinportenSchemaRequest(): MaskinportenSchemaRequest {
    val body = strictJson(jsonBody())
    return try {
        apiJson.decodeFromString(MaskinportenSchemaRequest.serializer(), body)
    } catch (e: SerializationException) {
        throw MalformedRequestException("The body must be a JSON object with only this API's fields, each of the right type")
    }
}

private val strictJsonMapper = JsonMapper.builder()
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .build()

// The filtering service reads the body before this app does. kotlinx keeps the last of two equal names, so a body that
// gives a name twice could have it allow other scopes than the ones sent to Altinn. Jackson's reason is not given,
// since it can quote the body
private fun strictJson(body: String): String {
    try {
        strictJsonMapper.readTree(body)
    } catch (e: JacksonException) {
        throw MalformedRequestException("The body must be one JSON object, with no name given twice")
    }
    return body
}

private suspend fun ApplicationCall.jsonBody(): String {
    requireJsonContentType()
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(receive<ByteArray>())).toString()
    } catch (e: CharacterCodingException) {
        throw MalformedRequestException("The body must be UTF-8")
    }
}

private fun ApplicationCall.requireJsonContentType() {
    val contentType = try {
        request.contentType()
    } catch (e: BadContentTypeFormatException) {
        null
    }
    if (contentType?.withoutParameters() != ContentType.Application.Json) {
        throw MalformedRequestException("The body must be JSON, sent with Content-Type application/json")
    }
}
