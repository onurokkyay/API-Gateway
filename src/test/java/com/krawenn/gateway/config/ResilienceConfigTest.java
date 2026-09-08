package com.krawenn.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import reactor.core.publisher.Mono;

/**
 * The budget a routed service is given.
 *
 * <p>This exists because the settings it replaces were bound by nothing: they sat in the config
 * repository under a property path the reactive Resilience4J starter does not read, so every route
 * silently ran on the library's one-second default and anything slower came back as 503 from
 * {@code /fallback} — indistinguishable from the service being down.
 *
 * <p>Asserted by running something through the breaker rather than by reading the registries back:
 * the registries hold the library defaults until a call applies the factory's configuration, so a
 * test that inspects them passes with or without this class.
 */
class ResilienceConfigTest {

    private static final String ROUTE = "gameatlasCircuitBreaker";

    private static ReactiveCircuitBreaker configuredBreaker() {
        ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                null,
                new Resilience4JConfigurationProperties());
        new ResilienceConfig().circuitBreakerDefaults().customize(factory);
        return factory.create(ROUTE);
    }

    @Test
    void aServiceSlowerThanASecondIsWaitedFor() {
        // One second is not a budget for a request that reaches a third party. GameAtlas's release
        // calendar spends a Twitch token fetch and an IGDB query whenever its cache is cold, and
        // under the default every one of those answered 503 on a perfectly healthy estate.
        Mono<String> slowUpstream = Mono.delay(Duration.ofMillis(1500)).thenReturn("answered");

        String answer = configuredBreaker()
                .run(slowUpstream, failure -> Mono.just("fell back"))
                .block(Duration.ofSeconds(10));

        assertThat(answer).isEqualTo("answered");
    }

    @Test
    void aServiceThatFailsStillFallsBack() {
        // The budget is longer, not absent: a failing upstream must still end up at /fallback
        // rather than surface as whatever its exception would have rendered as.
        Mono<String> broken = Mono.error(new IllegalStateException("connection refused"));

        String answer = configuredBreaker()
                .run(broken, failure -> Mono.just("fell back"))
                .block(Duration.ofSeconds(5));

        assertThat(answer).isEqualTo("fell back");
    }
}
