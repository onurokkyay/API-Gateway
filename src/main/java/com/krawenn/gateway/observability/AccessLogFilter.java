package com.krawenn.gateway.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * One line per request: method, path, status, duration.
 *
 * <p>It replaces three filters that logged whole headers and whole bodies. That was not a
 * verbosity problem, it was a credential leak: {@code POST /api/auth/login} carries a password in
 * its body and answers with an access and a refresh token, and both were written at INFO. The
 * Authorization header went into the same log. Anyone who could read the gateway's log could sign
 * in as anyone who had.
 *
 * <p>Buffering was the second cost: the body filters read every JSON request and response fully
 * into memory to log them, which turns any large upload into gateway heap.
 *
 * <p>What is kept is what a gateway log is actually for -- which route was taken, whether it
 * worked, and how long it took. Query strings are dropped with the same reasoning: they are the
 * other place secrets end up by accident.
 */
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final Clock clock;

    public AccessLogFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startedAt = Instant.now(clock);
        ServerHttpRequest request = exchange.getRequest();

        return chain.filter(exchange)
                .doFinally(signal -> log.info(
                        "{} {} -> {} in {} ms",
                        request.getMethod(),
                        request.getPath().value(),
                        exchange.getResponse().getStatusCode(),
                        Duration.between(startedAt, Instant.now(clock)).toMillis()));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
