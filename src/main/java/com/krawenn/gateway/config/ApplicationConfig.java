package com.krawenn.gateway.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-wide beans that are not tied to one feature. */
@Configuration
public class ApplicationConfig {

    /** Injected rather than called statically, so elapsed time is controllable in a test. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
