package com.krawenn.gateway.security;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Caps how many requests one caller makes in a minute, and answers {@code 429} with {@code Retry-After}
 * beyond it.
 *
 * <p>Two budgets. Signing in, registering and password resets get a small one per address: they are the
 * routes worth guessing at, two of them send mail, and the auth service's per-account lockout does not stop
 * one address trying many accounts. Everything else gets a generous one per signed-in account, or per
 * address for anonymous calls, so one runaway client cannot take the services behind it down for everyone.
 *
 * <p>Refreshing a token is not in the small budget: it needs a refresh token nobody can guess, and many people
 * behind one mobile carrier address refresh all day.
 *
 * <p>Counted in memory per gateway instance, in fixed one-minute windows. A second instance doubles what a
 * caller can make, which is acceptable at that scale; beyond it the counts belong in a shared store.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** After identity is known, before routing. */
    public static final int ORDER = IdentityPropagationFilter.ORDER + 10;

    private static final List<String> SIGN_IN = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/password/forgot",
            "/api/auth/password/verify-code",
            "/api/auth/password/reset");

    /** Windows are dropped once this many are held, so a flood of addresses cannot grow the map for good. */
    private static final int MAX_WINDOWS = 50_000;

    private final RateLimitProperties properties;
    private final GatewayErrors errors;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, GatewayErrors errors, Clock clock) {
        this.properties = properties;
        this.errors = errors;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        boolean signIn = SIGN_IN.contains(request.getPath().value());
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(name -> !signIn)
                .map(subject -> "account:" + subject)
                .defaultIfEmpty((signIn ? "sign-in:" : "address:") + address(request))
                .flatMap(key -> {
                    int limit = signIn ? properties.signInPerMinute() : properties.perMinute();
                    long now = clock.millis();
                    if (count(key, now) <= limit) {
                        return chain.filter(exchange);
                    }
                    long retryAfter = Math.max(1, (60_000 - now % 60_000 + 999) / 1_000);
                    log.warn(
                            "Rate limit reached for {} on {}",
                            key,
                            request.getPath().value());
                    return errors.tooManyRequests(exchange, retryAfter);
                });
    }

    /** The calls this key has made in the current minute, this one included. */
    long count(String key, long nowMillis) {
        long minute = nowMillis / 60_000;
        if (windows.size() > MAX_WINDOWS) {
            windows.values().removeIf(window -> window.minute() < minute);
        }
        return windows.compute(
                        key,
                        (k, old) -> old == null || old.minute() != minute
                                ? new Window(minute, 1)
                                : new Window(minute, old.count() + 1))
                .count();
    }

    private String address(ServerHttpRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].strip();
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown"
                : remote.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private record Window(long minute, long count) {}
}
