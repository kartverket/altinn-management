package no.kartverket.altinnmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.exception.AltinnManagementException
import no.kartverket.altinnmanagement.client.exception.AltinnRejectedException
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ProblemType

internal fun StatusPagesConfig.errorResponses() {
    exception<BadRequestException> { call, cause ->
        val causes = generateSequence<Throwable>(cause) { it.cause }.joinToString(" caused by ") { it.javaClass.name }
        call.application.log.warn("Malformed request: $causes")
        call.respondProblem(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest)
    }
    exception<InvalidRequestException> { call, cause ->
        call.respondProblem(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = cause.errors)
    }
    exception<MalformedRequestException> { call, cause ->
        call.respondProblem(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, cause.message)
    }
    exception<MaskinportenSchemaNotFoundException> { call, cause ->
        call.respondProblem(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, cause.message)
    }
    exception<AltinnRejectedException> { call, cause ->
        call.application.log.warn("Altinn rejected the resource: statusCode=${cause.statusCode}")
        val reasons = cause.reasons.joinToString("; ")
        call.respondProblem(
            ProblemType.UPSTREAM_REJECTED,
            HttpStatusCode.BadRequest,
            if (reasons.isEmpty()) "Altinn rejected the resource" else "Altinn rejected the resource: $reasons",
        )
    }
    exception<AltinnManagementException> { call, cause ->
        call.application.log.error("Call to Maskinporten or Altinn failed: statusCode=${cause.statusCode}", cause)
        call.respondProblem(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed")
    }
    exception<ExternalFilteringException> { call, cause ->
        call.application.log.error("Call to the filtering service failed", cause)
        call.respondProblem(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed")
    }
    exception<Throwable> { call, cause ->
        call.application.log.error("Unhandled exception", cause)
        call.respondProblem(ProblemType.INTERNAL_ERROR, HttpStatusCode.InternalServerError)
    }
    status(HttpStatusCode.NotFound) { call, status ->
        call.respondProblem(ProblemType.NOT_FOUND, status, "There is no endpoint at this path")
    }
    status(HttpStatusCode.MethodNotAllowed) { call, status ->
        call.respondProblem(ProblemType.METHOD_NOT_ALLOWED, status, "The endpoint at this path does not accept this method")
    }
}

private suspend fun ApplicationCall.respondProblem(
    type: ProblemType,
    status: HttpStatusCode,
    detail: String? = null,
    errors: List<FieldError>? = null,
) = respondText(
    Json.encodeToString(ErrorResponse.serializer(), ErrorResponse.of(type, status, detail, errors)),
    ContentType.Application.ProblemJson,
    status,
)
