package no.kartverket.altinnmanagement.mock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.TreeMap

/** Kartverket's access lists in memory, each as Altinn returns it, in the order Altinn pages them: by identifier. */
class AccessLists(lists: List<JsonObject>) {
    private val lists = TreeMap(lists.associateBy { it.getValue("identifier").jsonPrimitive.content })

    /** The list [id]. The id must match exactly, case included. */
    fun get(id: String): JsonObject? = lists[id]

    /** Up to [PAGE_SIZE] lists from the identifier [from] on, [from] included, and the identifier the next page starts at. */
    fun page(from: String?): Pair<List<JsonObject>, String?> {
        val rest = (if (from == null) lists else lists.tailMap(from, true)).values.take(PAGE_SIZE + 1)
        val next = rest.getOrNull(PAGE_SIZE)?.getValue("identifier")?.jsonPrimitive?.content
        return rest.take(PAGE_SIZE) to next
    }

    companion object {
        const val PAGE_SIZE = 20

        fun seeded(): AccessLists {
            val json = AccessLists::class.java.getResource("/access-lists.json")!!.readText()
            return AccessLists(Json.parseToJsonElement(json).jsonArray.map { it.jsonObject })
        }
    }
}
