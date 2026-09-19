package com.krawenn.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The gateway's own refusals, in the shape every service behind it uses.
 *
 * <p>Spring's default entry point answers a bare {@code 401} with no body: correct by the letter of
 * RFC 6750 and a contradiction of what this estate promises, which is one error shape everywhere
 * including from the security filters. A client that reads {@code code} to decide what to do found
 * an empty body exactly where authentication failed — the one refusal it most needs to tell apart
 * from a routed service's answer. The mobile client reported it (2026-09-13, item 3) after reading
 * the README's claim and measuring the opposite.
 *
 * <p>{@code UNAUTHENTICATED} rather than {@code UNAUTHORIZED}: the request carried no usable
 * identity, which is what the auth service calls the same condition. {@code FORBIDDEN} is the other
 * half — a verified identity without the role a path requires, which on this gateway means
 * {@code /actuator/**}.
 *
 * <p>{@code WWW-Authenticate: Bearer} is kept on the 401. Adding a body is the fix; dropping the
 * header a standard client may read would be a second, unasked-for change.
 */
@Component
public class GatewayErrors implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    /**
     * Its own, because this application has no {@code ObjectMapper} bean: it routes bodies through
     * without reading them, so nothing else here needs one. Five fields need no configuration.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Clock clock;

    public GatewayErrors(Clock clock) {
        this.clock = clock;
    }

    /** No usable identity: absent, expired, or a signature this gateway does not accept. */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
    }

    /** A verified identity that may not have this path. */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }

    /**
     * Writes the envelope the services behind this gateway write.
     *
     * <p>The reason is deliberately generic. Whether a token was missing, expired or forged is the
     * same answer to the caller and a hint to anyone probing; the gateway's access log carries the
     * detail for whoever is allowed to have it.
     */
    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now(clock).toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());

        byte[] json;
        try {
            json = JSON.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            // Five strings and an int cannot fail to serialize; if they ever do, an empty 401 is
            // still a 401 and is better than a 500 that says the gateway broke.
            return response.setComplete();
        }
        DataBuffer buffer = response.bufferFactory().wrap(json);
        response.getHeaders().setContentLength(json.length);
        return response.writeWith(Mono.just(buffer));
    }
}
