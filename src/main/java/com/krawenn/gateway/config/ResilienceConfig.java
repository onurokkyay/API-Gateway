package com.krawenn.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * How long a routed service is given, and when the gateway stops asking.
 *
 * <p>In Java rather than in {@code api-gateway.yml} because the reactive Resilience4J starter
 * reads no {@code resilience4j.*} configuration on its own — that binding comes from the
 * standalone Resilience4J Spring Boot starter, which is not a dependency here. The settings that
 * sat in the config repository under {@code spring.cloud.circuitbreaker.resilience4j.instances}
 * were bound by nothing and had no effect at all; every route ran on the library defaults.
 *
 * <p>The default that mattered is a <b>one-second time limiter</b>. It answered 503 from
 * {@code /fallback} to anything slower, which looks exactly like a service being down: GameAtlas's
 * release calendar, whose first request fetches a Twitch token and queries IGDB, failed on a
 * healthy estate every time its cache was cold.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Above the HTTP client's own {@code response-timeout} (10s) on purpose.
     *
     * <p>Whichever of the two is shorter decides the answer. Letting the HTTP client's timeout win
     * means a slow upstream is reported as the failure it is and recorded by the circuit breaker,
     * rather than cut off underneath it by a limiter that leaves no trace of which service was
     * slow.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    /** Ten calls before the rate means anything; a service failing 6 of them is not healthy. */
    private static final int SLIDING_WINDOW = 10;

    private static final float FAILURE_RATE_THRESHOLD = 60;

    private static final Duration WAIT_IN_OPEN_STATE = Duration.ofSeconds(30);

    private static final int CALLS_IN_HALF_OPEN_STATE = 3;

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerDefaults() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(
                        TimeLimiterConfig.custom().timeoutDuration(TIMEOUT).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(SLIDING_WINDOW)
                        .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                        .waitDurationInOpenState(WAIT_IN_OPEN_STATE)
                        .permittedNumberOfCallsInHalfOpenState(CALLS_IN_HALF_OPEN_STATE)
                        .build())
                .build());
    }
}
