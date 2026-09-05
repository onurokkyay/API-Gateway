package com.krawenn.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

/**
 * The gateway is the authentication boundary: it verifies every token so no routed service has to.
 *
 * <p>Verification is asymmetric. The auth service signs with a private key and publishes the
 * public half at its JWKS endpoint; this gateway holds nothing capable of minting a token. The
 * previous implementation shared an HMAC secret, which made every holder an issuer.
 *
 * <p>Authorization stays shallow here on purpose — authenticated or not, plus the {@code ADMIN}
 * rule for management endpoints. Whether a given user may touch a given resource is a question
 * only the owning service can answer, and encoding those rules at the edge would mean editing the
 * gateway every time a service changes its mind.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayCorsProperties.class)
public class SecurityConfig {

    private final GatewayCorsProperties corsProperties;

    public SecurityConfig(GatewayCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange.pathMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .pathMatchers(PublicPaths.ALL)
                        .permitAll()
                        // Management endpoints describe the estate, not a user's data.
                        .pathMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .pathMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Maps the token's {@code role} claim onto a single {@code ROLE_} authority.
     *
     * <p>Spring's default converter reads {@code scope}/{@code scp}, which these tokens do not
     * carry. Without this the {@code ADMIN} rule above could never match.
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString(TokenClaims.ROLE);
            List<SimpleGrantedAuthority> authorities =
                    role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return Mono.just(new JwtAuthenticationToken(jwt, authorities));
        };
    }

    /**
     * The only CORS configuration in the estate. Routed services sit behind this gateway and never
     * see a browser origin; a second layer there would silently override this one in whichever
     * direction is more permissive, which is the hardest class of bug to find.
     *
     * <p>An empty origin list denies every cross-origin request rather than allowing all of them.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
