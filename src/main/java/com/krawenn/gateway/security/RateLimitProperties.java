package com.krawenn.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many requests one caller may make in a minute.
 *
 * @param enabled           off only where nothing outside can reach the gateway
 * @param perMinute         for every route, per signed-in account, or per address when anonymous
 * @param signInPerMinute   for signing in, registering and password resets, per address: the routes worth
 *                          guessing at and the ones that send mail
 * @param trustForwardedFor read the client's address from the first {@code X-Forwarded-For} entry. Only
 *                          behind a load balancer that sets it: anywhere else a client writes it and picks
 *                          a fresh address for every request
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(boolean enabled, int perMinute, int signInPerMinute, boolean trustForwardedFor) {}
