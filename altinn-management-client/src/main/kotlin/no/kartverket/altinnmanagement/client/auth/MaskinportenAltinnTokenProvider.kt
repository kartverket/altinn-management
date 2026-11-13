package no.kartverket.altinnmanagement.client.auth

import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import java.net.URI
import java.time.Clock
import java.time.Duration

/**
 * Gets a token from Maskinporten with a JWT grant that [MaskinportenKey] signs, exchanges it for an Altinn token, and
 * reuses both until shortly before they expire.
 */
public class MaskinportenAltinnTokenProvider internal constructor(
    private val maskinportenClient: MaskinportenClient,
    private val exchanger: AltinnTokenExchanger,
    clock: Clock = Clock.systemUTC(),
    refreshLeeway: Duration = TokenCache.DEFAULT_REFRESH_LEEWAY,
) : AccessTokenProvider {

    public constructor(
        maskinportenTokenUrl: URI,
        clientId: String,
        key: MaskinportenKey,
        platformBaseUrl: URI,
        httpClient: OutgoingHttpClient,
    ) : this(
        MaskinportenClient(MaskinportenConfig(maskinportenTokenUrl.toString(), clientId, key), httpClient),
        AltinnTokenExchanger(platformBaseUrl.toString(), httpClient),
    )

    private val cache = TokenCache<AltinnToken>(clock, refreshLeeway)

    override suspend fun accessToken(): AccessToken = AccessToken.of(
        cache.get { exchanger.exchange(maskinportenClient.getToken()) }.value,
    )

    override suspend fun refused(token: AccessToken): Unit = cache.clear(token.value)
}
