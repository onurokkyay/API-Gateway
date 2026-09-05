package com.krawenn.gateway.security;

/**
 * Paths reachable without a token.
 *
 * <p>Kept as narrow as it can be: everything here is a door in the authentication boundary, and
 * the auth endpoints are exactly the ones a caller has no token for yet. {@code /api/auth/me} is
 * deliberately absent -- it answers "who am I", which requires being someone.
 */
public final class PublicPaths {

    public static final String[] ALL = {
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/logout",
        "/.well-known/**",
        "/*/v3/api-docs/**",
        "/*/swagger-ui/**",
        "/*/swagger-ui.html",
        "/fallback/**",
    };

    private PublicPaths() {}
}
