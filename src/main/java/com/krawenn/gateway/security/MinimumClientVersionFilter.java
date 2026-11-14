package com.krawenn.gateway.security;

import java.util.Arrays;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Tells an app too old for the API to update, before it fails in ways nobody can read.
 *
 * <p>A web client is always the current version; an installed app is whatever version its owner last
 * updated to, and a store cannot force an update. Once the API stops speaking a contract an old app relies
 * on, that app breaks screen by screen. The mobile app sends {@code X-App-Version}; below
 * {@code gateway.client.minimum-version} every request, signing in included, answers {@code 426
 * APP_UPDATE_REQUIRED}, which the app turns into one "please update" screen.
 *
 * <p>A request without the header is served: the web client does not send one, and an app too old to send
 * it predates the rule. Ahead of the security chain, so even an expired session hears it.
 */
@Component
@Order(-200)
public class MinimumClientVersionFilter implements WebFilter {

    public static final String APP_VERSION_HEADER = "X-App-Version";

    private final int[] minimum;
    private final GatewayErrors errors;

    public MinimumClientVersionFilter(MinimumClientVersionProperties properties, GatewayErrors errors) {
        String configured = properties.minimumVersion();
        this.minimum = configured == null || configured.isBlank() ? null : parse(configured);
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (minimum == null) {
            return chain.filter(exchange);
        }
        String sent = exchange.getRequest().getHeaders().getFirst(APP_VERSION_HEADER);
        if (sent == null || sent.isBlank()) {
            return chain.filter(exchange);
        }
        int[] version = parse(sent);
        if (version != null && compare(version, minimum) < 0) {
            return errors.updateRequired(exchange);
        }
        return chain.filter(exchange);
    }

    /** {@code 1.4.2}, {@code 1.4} or {@code 1.4.2+37}; null for anything else, which is let through. */
    static int[] parse(String version) {
        String core = version.strip().split("[+-]", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length < 1 || parts.length > 3) {
            return null;
        }
        int[] numbers = new int[3];
        try {
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return numbers;
    }

    static int compare(int[] left, int[] right) {
        return Arrays.compare(left, right);
    }
}
