package com.krawenn.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param minimumVersion the oldest app version still served, as {@code major.minor.patch}; empty serves
 *     every version. Raised when a release stops speaking a contract older apps rely on
 */
@ConfigurationProperties(prefix = "gateway.client")
public record MinimumClientVersionProperties(String minimumVersion) {}
