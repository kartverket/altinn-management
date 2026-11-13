package no.kartverket.altinnmanagement.client

public data class MaskinportenSchema(
    public val resourceId: ResourceId,
    public val scopes: List<String>?,
    public val title: MultilingualText?,
    public val description: MultilingualText?,
    public val delegationDescription: MultilingualText?,
    public val homepage: String?,
    public val contactPoints: List<ContactPoint>?,
    public val keywords: List<Keyword>?,
    public val status: String?,
    public val delegable: Boolean?,
    public val visible: Boolean?,
)

public data class MaskinportenSchemaContent(
    public val scopes: List<String>,
    public val title: MultilingualText,
    public val description: MultilingualText,
    public val delegationDescription: MultilingualText?,
    public val homepage: String?,
    public val contactPoints: List<ContactPoint>,
    public val keywords: List<Keyword>,
)

public data class MultilingualText(
    public val en: String?,
    public val nb: String?,
    public val nn: String?,
)

public data class ContactPoint(
    public val category: String?,
    public val email: String?,
    public val telephone: String?,
    public val contactPage: String?,
)

public data class Keyword(
    public val word: String?,
    public val language: String?,
)
