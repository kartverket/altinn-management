package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResourceRegistryClientTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val client = ResourceRegistryClient(
        AltinnEnvironment.TT02.platformBaseUrl,
        OutgoingHttpClient { request ->
            sent += request
            response(200, "application/json", resourceJson())
        },
    )

    private val id = ResourceId.of("kv-matrikkel-brukernavn")

    @Test
    fun `reads without an AccessTokenProvider`() = runBlocking {
        assertEquals(id, client.getMaskinportenSchema(id)?.resourceId)
    }

    @Test
    fun `refuses to change a resource without an AccessTokenProvider, before asking Altinn`() = runBlocking {
        val text = MultilingualText(en = "Text", nb = "Tekst", nn = "Tekst")
        val content = MaskinportenSchemaContent(listOf("kartverk:a"), text, text, text, null, emptyList(), emptyList())

        assertFailsWith<IllegalStateException> { client.updateMaskinportenSchema(id, content) }
        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }
}
