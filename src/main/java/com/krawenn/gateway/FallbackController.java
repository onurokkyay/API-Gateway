package com.krawenn.gateway;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a caller gets when a routed service is down or too slow.
 *
 * <p>One handler for every route: the answer is the same regardless of which service is missing,
 * and naming the failing service in the body would tell an unauthenticated caller how the estate
 * is put together. The route id stays in the gateway's own log instead.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, String>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "code", "SERVICE_UNAVAILABLE",
                        "message", "The service is temporarily unavailable. Please try again shortly."));
    }
}
