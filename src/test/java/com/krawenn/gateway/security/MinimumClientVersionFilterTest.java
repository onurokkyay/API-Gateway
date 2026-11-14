package com.krawenn.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class MinimumClientVersionFilterTest {

    private final GatewayErrors errors =
            new GatewayErrors(Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC));
    private final MinimumClientVersionFilter filter =
            new MinimumClientVersionFilter(new MinimumClientVersionProperties("1.4.0"), errors);

    @Test
    void anAppOlderThanTheMinimumIsToldToUpdateEvenWhenSigningIn() {
        assertThat(status("/api/auth/login", "1.3.9")).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
        assertThat(status("/api/me/games", "0.9")).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
    }

    @Test
    void theMinimumAndAnythingNewerAreServedBuildSuffixesIncluded() {
        assertThat(status("/api/me", "1.4.0")).isNull();
        assertThat(status("/api/me", "1.4.0+37")).isNull();
        assertThat(status("/api/me", "1.10.0")).isNull();
        assertThat(status("/api/me", "2")).isNull();
    }

    @Test
    void theWebClientAndAnythingUnreadableAreServed() {
        assertThat(status("/api/me", null)).isNull();
        assertThat(status("/api/me", "beta")).isNull();
    }

    @Test
    void withNoMinimumEveryVersionIsServed() {
        MinimumClientVersionFilter off = new MinimumClientVersionFilter(new MinimumClientVersionProperties(""), errors);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/me")
                .header("X-App-Version", "0.0.1")
                .build());
        AtomicBoolean passed = new AtomicBoolean();

        off.filter(exchange, ignored -> {
                    passed.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(passed).isTrue();
    }

    private HttpStatus status(String path, String version) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (version != null) {
            request.header(MinimumClientVersionFilter.APP_VERSION_HEADER, version);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        filter.filter(exchange, ignored -> Mono.empty()).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }
}
