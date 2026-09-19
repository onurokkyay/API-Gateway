package com.krawenn.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/**
 * The gateway's refusals, which used to be the one place in the estate that answered no body at all.
 */
class GatewayErrorsTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    private final GatewayErrors errors = new GatewayErrors(Clock.fixed(NOW, ZoneOffset.UTC));

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private static String bodyOf(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test
    void aRequestWithNoUsableIdentityGetsTheSameEnvelopeAsEverythingElse() {
        MockServerWebExchange exchange = exchangeFor("/api/me");

        errors.commence(exchange, new AuthenticationCredentialsNotFoundException("no token"))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(bodyOf(exchange))
                .isEqualTo("{\"timestamp\":\"2026-09-19T10:00:00Z\",\"status\":401,"
                        + "\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication is required\","
                        + "\"path\":\"/api/me\"}");
    }

    @Test
    void theBearerChallengeSurvivesTheBodyBeingAdded() {
        // A standard client may read it; adding a body is the fix, removing the header would be a
        // second change nobody asked for.
        MockServerWebExchange exchange = exchangeFor("/api/me");

        errors.commence(exchange, new AuthenticationCredentialsNotFoundException("no token"))
                .block();

        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer");
    }

    @Test
    void aVerifiedIdentityWithoutTheRoleIsForbiddenAndSaysSo() {
        MockServerWebExchange exchange = exchangeFor("/actuator/metrics");

        errors.handle(exchange, new AccessDeniedException("no ADMIN")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(exchange)).contains("\"code\":\"FORBIDDEN\"", "\"path\":\"/actuator/metrics\"");
    }

    @Test
    void theReasonNeverSaysWhichWayTheTokenFailed() {
        // Missing, expired and forged are the same answer to a caller: the difference is a hint to
        // whoever is probing, and it belongs in the access log instead.
        MockServerWebExchange exchange = exchangeFor("/api/me");

        errors.commence(exchange, new AuthenticationCredentialsNotFoundException("signature mismatch on kid 7f3"))
                .block();

        assertThat(bodyOf(exchange)).doesNotContain("signature", "kid");
    }

    @Test
    void thePathTravelsSoAClientCanTellWhichCallWasRefused() {
        MockServerWebExchange exchange = exchangeFor("/api/auth/me");

        errors.commence(exchange, new AuthenticationCredentialsNotFoundException("no token"))
                .block();

        assertThat(bodyOf(exchange)).contains("\"path\":\"/api/auth/me\"");
    }
}
