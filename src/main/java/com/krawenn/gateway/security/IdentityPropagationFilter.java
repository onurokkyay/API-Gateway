package com.krawenn.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Replaces the caller's identity headers with the ones the verified token actually carries.
 *
 * <p>Two steps, and the order between them is the whole point:
 *
 * <ol>
 *   <li><b>Strip.</b> Every {@link IdentityHeaders} value that arrived from outside is removed —
 *       on every request, including the ones that never reach a token check. A client that sends
 *       {@code X-User-Id: <someone else>} alongside its own valid token must not have that header
 *       survive; without this line the gateway would authenticate one account and hand the
 *       services behind it another, which is a complete authorization bypass.
 *   <li><b>Inject.</b> If the request is authenticated, the token's {@code sub} and {@code role}
 *       are written back under the same names.
 * </ol>
 *
 * <p>Unauthenticated requests reach here only on permitted paths (login, JWKS, docs), and they
 * travel on with no identity headers at all rather than an empty one — a downstream service can
 * then treat "absent" as anonymous without having to distinguish it from "present but blank".
 */
@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {

    /**
     * Ahead of the routing filters that build the outgoing request, and after Spring Security's
     * {@code WebFilter} chain, which has already validated the token and populated the context by
     * the time any {@link GlobalFilter} runs.
     */
    public static final int ORDER = -100;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder request = exchange.getRequest().mutate();
        for (String header : IdentityHeaders.ALL) {
            request.headers(headers -> headers.remove(header));
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication.getPrincipal() instanceof Jwt)
                .map(authentication -> (Jwt) authentication.getPrincipal())
                .map(jwt -> {
                    request.header(IdentityHeaders.USER_ID, jwt.getSubject());
                    String role = jwt.getClaimAsString(TokenClaims.ROLE);
                    if (role != null) {
                        request.header(IdentityHeaders.USER_ROLE, role);
                    }
                    return request;
                })
                .defaultIfEmpty(request)
                .flatMap(mutated ->
                        chain.filter(exchange.mutate().request(mutated.build()).build()));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
