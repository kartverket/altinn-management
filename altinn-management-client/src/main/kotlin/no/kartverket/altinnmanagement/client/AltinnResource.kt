package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.client.http.Http

@Serializable
internal data class AltinnResource(
    val identifier: String? = null,
    val title: Map<String, String?>? = null,
    val description: Map<String, String?>? = null,
    val rightDescription: Map<String, String?>? = null,
    val homepage: String? = null,
    val status: String? = null,
    val contactPoints: List<AltinnContactPoint>? = null,
    val keywords: List<AltinnKeyword>? = null,
    val resourceReferences: List<AltinnResourceReference>? = null,
    val delegable: Boolean? = null,
    val visible: Boolean? = null,
    val hasCompetentAuthority: AltinnCompetentAuthority? = null,
    val resourceType: String? = null,
) {
    val isMaskinportenSchema: Boolean get() = resourceType == MASKINPORTEN_SCHEMA

    val isKartverkets: Boolean get() = hasCompetentAuthority?.organization == KARTVERKET_ORGANIZATION_NUMBER

    fun toMaskinportenSchema(id: ResourceId): MaskinportenSchema = MaskinportenSchema(
        resourceId = id,
        scopes = resourceReferences?.filter { it.referenceType == MASKINPORTEN_SCOPE }?.mapNotNull { it.reference },
        title = title?.let(::multilingualText),
        description = description?.let(::multilingualText),
        delegationDescription = rightDescription?.let(::multilingualText),
        homepage = homepage,
        contactPoints = contactPoints?.map {
            ContactPoint(category = it.category, email = it.email, telephone = it.telephone, contactPage = it.contactPage)
        },
        keywords = keywords?.map { Keyword(word = it.word, language = it.language) },
        status = status,
        delegable = delegable,
        visible = visible,
    )
}

@Serializable
internal data class AltinnContactPoint(
    val category: String? = null,
    val email: String? = null,
    val telephone: String? = null,
    val contactPage: String? = null,
)

@Serializable
internal data class AltinnKeyword(
    val word: String? = null,
    val language: String? = null,
)

@Serializable
internal data class AltinnResourceReference(
    val referenceSource: String? = null,
    val reference: String? = null,
    val referenceType: String? = null,
)

@Serializable
internal data class AltinnCompetentAuthority(
    val organization: String? = null,
)

internal const val MASKINPORTEN_SCHEMA = "MaskinportenSchema"

internal const val KARTVERKET_ORGANIZATION_NUMBER = "971040238"

private const val MASKINPORTEN_SCOPE = "MaskinportenScope"

/**
 * The resource as Altinn has it, with [content] in place of this API's fields. Altinn replaces the whole resource on a
 * change, so every other field, and every reference that is not a Maskinporten scope, is kept as it is.
 */
internal fun JsonObject.withContent(content: MaskinportenSchemaContent): JsonObject {
    val changes = Http.json.encodeToJsonElement(
        AltinnResource.serializer(),
        AltinnResource(
            title = content.title.toMap(),
            description = content.description.toMap(),
            rightDescription = content.delegationDescription?.toMap(),
            homepage = content.homepage,
            contactPoints = content.contactPoints.map { AltinnContactPoint(it.category, it.email, it.telephone, it.contactPage) },
            keywords = content.keywords.map { AltinnKeyword(it.word, it.language) },
            resourceReferences = content.scopes.map { AltinnResourceReference("Default", it, MASKINPORTEN_SCOPE) },
        ),
    ).jsonObject
    val otherReferences = (this["resourceReferences"] as? JsonArray).orEmpty()
        .filter { (it as? JsonObject)?.get("referenceType") != JsonPrimitive(MASKINPORTEN_SCOPE) }
    val references = JsonArray(changes.getValue("resourceReferences").jsonArray + otherReferences)
    return JsonObject(filterKeys { it !in REPLACED_FIELDS } + changes + ("resourceReferences" to references))
}

private val REPLACED_FIELDS = setOf(
    "title", "description", "rightDescription", "homepage", "contactPoints", "keywords", "resourceReferences",
    "versionId", "createdAt", "updatedAt",
)

private fun MultilingualText.toMap() = mapOf("en" to en, "nb" to nb, "nn" to nn).filterValues { it != null }

private fun multilingualText(texts: Map<String, String?>) = MultilingualText(en = texts["en"], nb = texts["nb"], nn = texts["nn"])
