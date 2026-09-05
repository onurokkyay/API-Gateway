package com.krawenn.gateway.security;

/**
 * The headers this gateway uses to tell a routed service who the caller is.
 *
 * <p>Public because they are a contract: every service behind the gateway reads them, and none
 * of them can verify a token itself. That trust is only sound while two things hold — the
 * gateway strips these headers from every inbound request (see {@code IdentityPropagationFilter}),
 * and no routed service is reachable except through the gateway. If a service's port is published
 * to a network anyone else can reach, these headers become an unauthenticated login form.
 */
public final class IdentityHeaders {

    /** The authenticated account id: the token's {@code sub}, a UUIDv7. */
    public static final String USER_ID = "X-User-Id";

    /** The authenticated account's role, {@code USER} or {@code ADMIN}. */
    public static final String USER_ROLE = "X-User-Role";

    /** Everything the gateway refuses to forward from a client. */
    public static final String[] ALL = {USER_ID, USER_ROLE};

    private IdentityHeaders() {}
}
