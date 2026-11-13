package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable

@Serializable
@JsonSchema.Description(
    "A whole MaskinportenSchema, to replace the one in Altinn. A field that is left out is removed, and the fields Altinn " +
        "has that are not here are kept as they are. `status`, `delegable` and `visible` cannot be changed here, so " +
        "leave them out, like any field not listed.",
)
@JsonSchema.Required("scopes", "title", "description")
data class MaskinportenSchemaRequest(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Optional. If it is sent, it must be the id in the path.")
    val resourceId: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description(
        "Required. The Maskinporten scopes the resource is for, at least one. Altinn only accepts Kartverket's scopes.",
    )
    val scopes: List<String>? = null,

    @JsonSchema.Required("en", "nb", "nn")
    @JsonSchema.Description("Required, in `en`, `nb` and `nn`.")
    val title: MultilingualText? = null,

    @JsonSchema.Required("en", "nb", "nn")
    @JsonSchema.Description("Required, in `en`, `nb` and `nn`.")
    val description: MultilingualText? = null,

    @JsonSchema.Description(
        "Required, in `en`, `nb` and `nn`, when the resource is `delegable`, since Altinn rejects the resource without it. " +
            "Optional otherwise.",
    )
    val delegationDescription: MultilingualText? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Optional. A web page about the resource.")
    val homepage: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Optional. Where to get help with the resource.")
    val contactPoints: List<ContactPoint>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Optional. Words to find the resource by in Altinn.")
    val keywords: List<Keyword>? = null,
)
