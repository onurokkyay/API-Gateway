package com.krawenn.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins allowed through the gateway.
 *
 * @param allowedOrigins exact origins; empty denies every cross-origin request, which is the
 *     right default for a service whose clients are known
 * @param allowedMethods HTTP methods a browser may use
 */
@ConfigurationProperties(prefix = "gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins, List<String> allowedMethods) {

    public GatewayCorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : List.copyOf(allowedMethods);
    }
}
