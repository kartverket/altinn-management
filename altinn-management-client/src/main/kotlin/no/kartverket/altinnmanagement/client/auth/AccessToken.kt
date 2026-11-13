package no.kartverket.altinnmanagement.client.auth

@JvmInline
public value class AccessToken private constructor(public val value: String) {
    override fun toString(): String = "AccessToken(***)"

    public companion object {
        public fun of(value: String): AccessToken = AccessToken(value)
    }
}
