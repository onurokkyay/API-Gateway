package com.krawenn.gateway.security;

/**
 * Non-standard claims the auth service puts in an access token.
 *
 * <p>Mirrors {@code AccessTokenService} in that service. Duplicated rather than shared: a gateway
 * that depended on the auth service's jar would couple two deployables that are meant to be
 * replaceable independently, and the contract is three strings.
 */
public final class TokenClaims {

    /** {@code USER} or {@code ADMIN}. */
    public static final String ROLE = "role";

    /** Display name. Never an identity — usernames change; {@code sub} does not. */
    public static final String PREFERRED_USERNAME = "preferred_username";

    private TokenClaims() {}
}
