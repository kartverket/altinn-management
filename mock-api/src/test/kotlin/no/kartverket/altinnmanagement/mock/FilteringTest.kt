package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FilteringTest {

    private fun clientCertificate(namespace: String, app: String) =
        "By=spiffe://cluster.local/ns/tilgangsstyring/sa/altinn-management;Hash=4f1c2b;Subject=\"\";" +
            "URI=spiffe://cluster.local/ns/$namespace/sa/$app"

    private suspend fun ApplicationTestBuilder.filterFor(clientCertificate: String?): HttpResponse =
        client.post("/filtering") {
            contentType(ContentType.Application.Json)
            val body = buildJsonObject {
                putJsonObject("input") {
                    putJsonObject("headers") { clientCertificate?.let { put("x-forwarded-client-cert", it) } }
                }
            }
            setBody(body.toString())
        }

    private suspend fun HttpResponse.result(): JsonObject = json().jsonObject.getValue("result").jsonObject

    private fun JsonObject.filter(name: String): Filter = getValue(name).jsonObject.let { filter ->
        fun strings(key: String) = filter.getValue(key).jsonArray.map { it.jsonPrimitive.content }
        Filter(exact = strings("exact"), prefix = strings("prefix"))
    }

    @Test
    fun `a test app gets its scopes and resources, as the result`() = mockApi {
        filterFor(clientCertificate("tilgangsstyring", "demo")).apply {
            assertEquals(HttpStatusCode.OK, status)
            val result = result()
            assertEquals(Filter(listOf("kartverk:dokumentbestilling", "kartverk:nrl.rapportering"), listOf("kartverk:tilgangsstyring/")), result.filter("scopes"))
            assertEquals(Filter(prefix = listOf("nrl-")), result.filter("resources"))
        }
    }

    @Test
    fun `an unknown app gets empty lists`() = mockApi {
        filterFor(clientCertificate("tilgangsstyring", "unknown")).apply {
            assertEquals(HttpStatusCode.OK, status)
            val result = result()
            assertEquals(Filter(), result.filter("scopes"))
            assertEquals(Filter(), result.filter("resources"))
        }
    }

    @Test
    fun `refuses a call without a SPIFFE URI in the input`() = mockApi {
        assertEquals(HttpStatusCode.BadRequest, filterFor(null).status)
        assertEquals(HttpStatusCode.BadRequest, filterFor("By=spiffe://cluster.local/ns/tilgangsstyring/sa/demo").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/filtering") { header(CLIENT_CERTIFICATE_HEADER, clientCertificate("tilgangsstyring", "demo")) }.status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/filtering") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { putJsonObject("input") { putJsonObject("headers") { put("x-forwarded-client-cert", JsonObject(emptyMap())) } } }.toString())
            }.status,
        )
    }

    @Test
    fun `the app is the last SPIFFE URI, like OPA, not one the caller sent itself`() = mockApi {
        val certificate = "URI=spiffe://cluster.local/ns/matrikkel/sa/matrikkel-api," + clientCertificate("tilgangsstyring", "demo")

        assertEquals(listOf("nrl-"), filterFor(certificate).result().filter("resources").prefix)
    }

    @Test
    fun `the header's name is matched whatever its case`() = mockApi {
        val response = client.post("/filtering") {
            contentType(ContentType.Application.Json)
            val body = buildJsonObject {
                putJsonObject("input") {
                    putJsonObject("headers") { put(CLIENT_CERTIFICATE_HEADER, JsonPrimitive(clientCertificate("matrikkel", "matrikkel-api"))) }
                }
            }
            setBody(body.toString())
        }

        assertEquals(listOf("kv-matrikkel-brukernavn"), response.result().filter("resources").exact)
    }
}
