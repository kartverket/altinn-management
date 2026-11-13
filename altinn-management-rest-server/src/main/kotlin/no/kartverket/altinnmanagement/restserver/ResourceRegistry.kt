package no.kartverket.altinnmanagement.restserver

import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.client.auth.MaskinportenAltinnTokenProvider
import no.kartverket.altinnmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import java.net.http.HttpClient
import java.time.Duration

internal class MaskinportenSchemaNotFoundException : RuntimeException("There is no MaskinportenSchema with this id")

internal fun resourceRegistryClient(config: AppConfig): ResourceRegistryClient {
    val httpClient = httpClient()
    val tokenProvider = MaskinportenAltinnTokenProvider(
        maskinportenTokenUrl = config.altinnEnvironment.maskinportenTokenUrl,
        clientId = config.maskinportenClientId,
        key = config.maskinportenKey,
        platformBaseUrl = config.altinnEnvironment.platformBaseUrl,
        httpClient = httpClient,
    )
    return ResourceRegistryClient(config.altinnEnvironment.platformBaseUrl, httpClient, tokenProvider)
}

internal fun httpClient(): OutgoingHttpClient = LoggingOutgoingHttpClient(
    JavaOutgoingHttpClient(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        REQUEST_TIMEOUT,
    ),
)

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
