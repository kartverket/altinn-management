package no.kartverket.altinnmanagement.client.exception

/** Altinn would not take a resource as it was sent. [reasons] are Altinn's, like `Title: Missing title in english en`. */
public class AltinnRejectedException internal constructor(
    public val reasons: List<String>,
    statusCode: Int,
    responseBody: String?,
) : AltinnManagementException("Altinn rejected the resource", statusCode, responseBody)
