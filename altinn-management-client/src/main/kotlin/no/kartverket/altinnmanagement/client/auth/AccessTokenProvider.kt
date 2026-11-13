package no.kartverket.altinnmanagement.client.auth

public fun interface AccessTokenProvider {
    public suspend fun accessToken(): AccessToken

    /** Altinn refused [token], so the next call must not get it again. */
    public suspend fun refused(token: AccessToken) {}
}
