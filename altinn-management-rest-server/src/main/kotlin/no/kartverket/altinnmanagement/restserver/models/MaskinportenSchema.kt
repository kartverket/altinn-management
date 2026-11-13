package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.ContactPoint as ClientContactPoint
import no.kartverket.altinnmanagement.client.Keyword as ClientKeyword
import no.kartverket.altinnmanagement.client.MaskinportenSchema as ClientMaskinportenSchema
import no.kartverket.altinnmanagement.client.MultilingualText as ClientMultilingualText

@Serializable
@JsonSchema.Description(
    "A resource of type `MaskinportenSchema` in Altinn, with the values Altinn has. A field Altinn has no value " +
        "for is left out. `title` is its name, `description` says what it gives access to, and " +
        "`delegationDescription` explains the rights an organisation gets when the resource is delegated to it.",
)
data class MaskinportenSchema(
    @JsonSchema.Description("The resource's id in Altinn: 4 or more of `a-z`, `0-9`, `_` and `-`. It cannot be changed.")
    val resourceId: String,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description(
        "The Maskinporten scopes the resource is for. An organisation the resource is delegated to in Altinn can get " +
            "tokens with these scopes on behalf of the organisation that delegated it.",
    )
    val scopes: List<String>? = null,

    val title: MultilingualText? = null,

    val description: MultilingualText? = null,

    val delegationDescription: MultilingualText? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A web page about the resource.")
    val homepage: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Where to get help with the resource. Can be empty.")
    val contactPoints: List<ContactPoint>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Words to find the resource by in Altinn. Can be empty.")
    val keywords: List<Keyword>? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "Altinn's status for the resource, such as `Completed`, `Active`, `UnderDevelopment`, `Deprecated` or " +
            "`Withdrawn`.",
    )
    val status: String? = null,

    @JsonSchema.Type(JsonType.BOOLEAN)
    @JsonSchema.Description("If `true`, organisations can delegate the resource to others in Altinn.")
    val delegable: Boolean? = null,

    @JsonSchema.Type(JsonType.BOOLEAN)
    @JsonSchema.Description("If `true`, Altinn shows the resource to its users.")
    val visible: Boolean? = null,
) {
    companion object {
        fun from(schema: ClientMaskinportenSchema) = MaskinportenSchema(
            resourceId = schema.resourceId.value,
            scopes = schema.scopes,
            title = schema.title?.let(MultilingualText::from),
            description = schema.description?.let(MultilingualText::from),
            delegationDescription = schema.delegationDescription?.let(MultilingualText::from),
            homepage = schema.homepage,
            contactPoints = schema.contactPoints?.map(ContactPoint::from),
            keywords = schema.keywords?.map(Keyword::from),
            status = schema.status,
            delegable = schema.delegable,
            visible = schema.visible,
        )
    }
}

@Serializable
@JsonSchema.Description("A text in English, Norwegian Bokmål and Norwegian Nynorsk.")
data class MultilingualText(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("In English. Left out if missing.")
    val en: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("In Norwegian Bokmål. Left out if missing.")
    val nb: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("In Norwegian Nynorsk. Left out if missing.")
    val nn: String? = null,
) {
    companion object {
        fun from(text: ClientMultilingualText) = MultilingualText(en = text.en, nb = text.nb, nn = text.nn)
    }

    fun toClient() = ClientMultilingualText(en = en, nb = nb, nn = nn)
}

@Serializable
data class ContactPoint(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the contact point is, such as `Kundesenter`. Left out if missing.")
    val category: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("An email address. Left out, or empty, if missing.")
    val email: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A phone number. Left out, or empty, if missing.")
    val telephone: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A web page with contact information. Left out, or empty, if missing.")
    val contactPage: String? = null,
) {
    companion object {
        fun from(contactPoint: ClientContactPoint) = ContactPoint(
            category = contactPoint.category,
            email = contactPoint.email,
            telephone = contactPoint.telephone,
            contactPage = contactPoint.contactPage,
        )
    }

    fun toClient() = ClientContactPoint(category = category, email = email, telephone = telephone, contactPage = contactPage)
}

@Serializable
data class Keyword(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The word. Left out if missing.")
    val word: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The word's language, such as `nb`. Left out if missing.")
    val language: String? = null,
) {
    companion object {
        fun from(keyword: ClientKeyword) = Keyword(word = keyword.word, language = keyword.language)
    }

    fun toClient() = ClientKeyword(word = word, language = language)
}
