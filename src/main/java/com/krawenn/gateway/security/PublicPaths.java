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
        // Where a platform redirects a browser after a sign-in. A top-level navigation carries no
        // Authorization header, so this cannot require a token -- the `state` the flow was started
        // with is what identifies the account, and GameAtlas issues it to a signed-in caller,
        // spends it on first use and expires it in minutes. Requiring a token here made the Xbox
        // link impossible to complete: the browser was turned away before it could deliver the
        // code it had just been given.
        "/api/me/accounts/*/callback",
        // One OpenAPI document per service, routed to /api-docs/<service>. Public because a
        // client generator reads them without a session, and because they describe the API
        // rather than anyone's data. Each service decides whether to publish one at all.
        "/api-docs/**",
        "/*/v3/api-docs/**",
        "/*/swagger-ui/**",
        "/*/swagger-ui.html",
        "/fallback/**",
    };

    private PublicPaths() {}
}
