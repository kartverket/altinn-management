package no.kartverket.altinnmanagement.client.auth

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.MutableClock
import no.kartverket.altinnmanagement.client.support.NOW
import no.kartverket.altinnmanagement.client.support.TOKEN_PATH
import no.kartverket.altinnmanagement.client.support.bothTokens
import no.kartverket.altinnmanagement.client.support.maskinportenAltinnTokenProvider
import no.kartverket.altinnmanagement.client.support.recording
import no.kartverket.altinnmanagement.client.support.signedJwt
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskinportenAltinnTokenProviderTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val exchangePath = AltinnTokenExchanger.EXCHANGE_PATH

    private fun sentTo(path: String) = sent.filter { it.url.path == path }

    @Test
    fun `fetches a Maskinporten token and exchanges it for an Altinn token`() = runBlocking {
        val token = maskinportenAltinnTokenProvider(recording(sent, bothTokens())).accessToken()

        assertEquals("Bearer mp-token", sentTo(exchangePath).single().headers["Authorization"])
        assertEquals(signedJwt(NOW.plusSeconds(300)), token.value)
    }

    @Test
    fun `serves both tokens from cache on later calls`() = runBlocking {
        val provider = maskinportenAltinnTokenProvider(recording(sent, bothTokens()))

        repeat(3) { provider.accessToken() }

        assertEquals(1, sentTo(TOKEN_PATH).size)
        assertEquals(1, sentTo(exchangePath).size)
    }

    @Test
    fun `exchanges again when the Altinn token is about to expire`() = runBlocking {
        val clock = MutableClock()
        val provider = maskinportenAltinnTokenProvider(recording(sent, bothTokens(Duration.ofSeconds(300))), clock)

        provider.accessToken()
        clock.advance(Duration.ofSeconds(300) - TokenCache.DEFAULT_REFRESH_LEEWAY)
        provider.accessToken()

        assertEquals(2, sentTo(exchangePath).size)
    }

    @Test
    fun `exchanges again after Altinn refuses the token`() = runBlocking {
        val provider = maskinportenAltinnTokenProvider(recording(sent, bothTokens()))

        provider.refused(provider.accessToken())
        provider.accessToken()

        assertEquals(2, sentTo(exchangePath).size)
    }

    @Test
    fun `keeps a newer token when an older one is refused`() = runBlocking {
        val provider = maskinportenAltinnTokenProvider(recording(sent, bothTokens()))

        provider.refused(AccessToken.of("an-older-token"))
        provider.accessToken()

        assertEquals(1, sentTo(exchangePath).size)
    }
}
