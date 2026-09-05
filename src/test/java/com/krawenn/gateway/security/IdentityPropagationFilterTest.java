package com.krawenn.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * The header rules the whole estate rests on.
 *
 * <p>Every service behind this gateway trusts {@code X-User-Id} without being able to check it.
 * That trust is only as good as these three tests: a forged header must never survive, a real one
 * must be written, and an anonymous request must carry neither.
 */
class IdentityPropagationFilterTest {

    private static final String USER_ID = "01a0715b-e151-75f9-a8c6-40b9d20b7449";

    private final IdentityPropagationFilter filter = new IdentityPropagationFilter();

    /** Captures the request as the filter handed it on, which is the thing under test. */
    private static final class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }

        HttpHeaders headers() {
            return captured.getRequest().getHeaders();
        }
    }

    private static Jwt token(String subject, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim(TokenClaims.ROLE, role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }

    @Test
    void aForgedIdentityHeaderIsRemovedEvenWhenTheTokenIsValid() {
        // The attack this gateway exists to stop: a real account presenting someone else's id.
        // Spring Cloud Gateway forwards unknown headers by default, so without the strip step
        // the gateway would authenticate one account and hand the service behind it another.
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/me/games")
                .header(IdentityHeaders.USER_ID, "01999999-0000-7000-8000-000000000000")
                .header(IdentityHeaders.USER_ROLE, "ADMIN"));
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(authenticated(token(USER_ID, "USER"))))
                .verifyComplete();

        assertThat(chain.headers().get(IdentityHeaders.USER_ID)).containsExactly(USER_ID);
        assertThat(chain.headers().get(IdentityHeaders.USER_ROLE)).containsExactly("USER");
    }

    @Test
    void anAnonymousRequestCarriesNoIdentityAtAll() {
        // Absent rather than blank: a downstream service can then treat "no header" as anonymous
        // without having to tell it apart from "header present but empty".
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").header(IdentityHeaders.USER_ID, "smuggled"));
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.headers().containsHeader(IdentityHeaders.USER_ID)).isFalse();
        assertThat(chain.headers().containsHeader(IdentityHeaders.USER_ROLE)).isFalse();
    }

    @Test
    void anAuthenticationThatIsNotAJwtPropagatesNothing() {
        // Defence against a future filter putting some other principal in the context: identity
        // headers are only ever written from a verified token.
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/me/games"));
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new TestingAuthenticationToken("someone", "credentials"))))
                .verifyComplete();

        assertThat(chain.headers().containsHeader(IdentityHeaders.USER_ID)).isFalse();
    }

    private static Context authenticated(Jwt jwt) {
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaims().get(TokenClaims.ROLE)))));
    }

    @Test
    void everyDeclaredIdentityHeaderIsStripped() {
        // Guards the list itself: adding a header to IdentityHeaders.ALL without teaching the
        // filter to strip it would open the same hole again.
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/me/games");
        for (String header : IdentityHeaders.ALL) {
            builder.header(header, "forged");
        }
        MockServerWebExchange exchange = MockServerWebExchange.from((MockServerHttpRequest) builder.build());
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = chain.headers();
        for (String header : IdentityHeaders.ALL) {
            assertThat(headers.get(header)).as(header).isNull();
        }
    }
}
