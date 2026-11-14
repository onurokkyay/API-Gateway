package com.krawenn.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitFilterTest {

    // 20 seconds into a minute: 40 seconds until the window turns.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T12:00:20Z"), ZoneOffset.UTC);

    private final RateLimitFilter filter =
            new RateLimitFilter(new RateLimitProperties(true, 5, 2, false), new GatewayErrors(CLOCK), CLOCK);
    private final AtomicInteger routed = new AtomicInteger();

    @Test
    void signingInFromOneAddressGetsASmallBudgetAndThenWaits() {
        assertThat(call("/api/auth/login", "10.0.0.1").getResponse().getStatusCode())
                .isNull();
        assertThat(call("/api/auth/login", "10.0.0.1").getResponse().getStatusCode())
                .isNull();

        ServerWebExchange third = call("/api/auth/login", "10.0.0.1");

        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo("40");
        assertThat(routed).hasValue(2);
    }

    @Test
    void anotherAddressHasItsOwnBudget() {
        call("/api/auth/register", "10.0.0.1");
        call("/api/auth/register", "10.0.0.1");

        assertThat(call("/api/auth/register", "10.0.0.2").getResponse().getStatusCode())
                .isNull();
    }

    @Test
    void everythingElseHasTheLargerBudget() {
        for (int i = 0; i < 5; i++) {
            assertThat(call("/api/games/genres", "10.0.0.3").getResponse().getStatusCode())
                    .isNull();
        }
        assertThat(call("/api/games/genres", "10.0.0.3").getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void theWindowTurnsEveryMinute() {
        assertThat(filter.count("address:10.0.0.4", 0)).isOne();
        assertThat(filter.count("address:10.0.0.4", 59_999)).isEqualTo(2);
        assertThat(filter.count("address:10.0.0.4", 60_000)).isOne();
    }

    private ServerWebExchange call(String path, String address) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress(address, 40000))
                .build());
        filter.filter(exchange, ignored -> {
                    routed.incrementAndGet();
                    return Mono.empty();
                })
                .block();
        return exchange;
    }
}
