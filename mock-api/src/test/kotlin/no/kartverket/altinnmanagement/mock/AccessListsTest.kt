package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessListsTest {

    private val path = "/resourceregistry/api/v1/access-lists"

    private fun list(id: String): JsonObject = buildJsonObject {
        put("urn", "urn:altinn:access-list:kv:$id")
        put("identifier", id)
        put("name", id)
        put("description", "")
        put("createdAt", "2026-11-20T10:15:30.123456+00:00")
        put("updatedAt", "2026-11-20T10:15:30.123456+00:00")
    }

    private suspend fun ApplicationTestBuilder.read(url: String, scope: String = "altinn:resourceregistry/accesslist.read") =
        client.get(url) { header(HttpHeaders.Authorization, "Bearer ${altinnToken(scope)}") }

    @Test
    fun `the seed holds Kartverket's 12 lists in TT02`() = mockApi {
        val ids = read("$path/kv").json().jsonObject.getValue("data").jsonArray.map { it.jsonObject.getValue("identifier").jsonPrimitive.content }

        assertEquals(12, ids.size)
        assertEquals("erlend-kan-lage", ids.first())
    }

    @Test
    fun `gives the lists 20 at a time, by identifier, with an absolute link to the next page`() =
        mockApi(lists = AccessLists((1..45).map { list("kv-liste-%02d".format(it)) })) {
            val pages = mutableListOf<List<String>>()
            var url: String? = "$path/kv"
            while (url != null) {
                val body = read(url).json().jsonObject
                pages += body.getValue("data").jsonArray.map { it.jsonObject.getValue("identifier").jsonPrimitive.content }
                val next = body.getValue("links").jsonObject["next"]?.jsonPrimitive?.content
                next?.let { assertTrue(it.startsWith("http://localhost:80$path/kv?token="), it) }
                url = next?.let { URI(it).run { "$rawPath?$rawQuery" } }
            }

            assertEquals(listOf(20, 20, 5), pages.map { it.size })
            assertEquals((1..45).map { "kv-liste-%02d".format(it) }, pages.flatten())
        }

    @Test
    fun `starts a page at the identifier in the token, that list included`() = mockApi(lists = AccessLists(listOf(list("kv-a"), list("kv-b")))) {
        // "kv-b" as a JSON string in base64url
        val body = read("$path/kv?token=Imt2LWIi").json().jsonObject

        assertEquals(listOf("kv-b"), body.getValue("data").jsonArray.map { it.jsonObject.getValue("identifier").jsonPrimitive.content })
        assertNull(body.getValue("links").jsonObject["next"])
    }

    @Test
    fun `answers 400 to a token that is not one`() = mockApi {
        assertEquals(HttpStatusCode.BadRequest, read("$path/kv?token=not-a-token").status)
    }

    @Test
    fun `gives a list by its exact id, and 404 for one it does not have`() = mockApi {
        assertEquals("internt", read("$path/kv/internt").json().jsonObject.getValue("identifier").jsonPrimitive.content)
        assertEquals(HttpStatusCode.NotFound, read("$path/kv/INTERNT").status)
        assertEquals(HttpStatusCode.OK, read("$path/KV/internt").status)
    }

    @Test
    fun `answers 401 without a token, and 403 without a scope for lists or for another owner`() = mockApi {
        assertEquals(HttpStatusCode.Unauthorized, client.get("$path/kv").status)
        assertEquals(HttpStatusCode.Forbidden, read("$path/kv", scope = "altinn:resourceregistry/resource.write").status)
        assertEquals(HttpStatusCode.Forbidden, read("$path/skd").status)
        assertEquals(HttpStatusCode.Forbidden, read("$path/skd/liste").status)
        assertEquals(HttpStatusCode.OK, read("$path/kv", scope = "altinn:resourceregistry/accesslist.write").status)
    }
}
