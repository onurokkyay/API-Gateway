package com.krawenn.gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @RequestMapping("/league-fallback")
    public ResponseEntity<String> leagueFallback() {
        return ResponseEntity.status(503).body("Service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping("/auth-fallback")
    public ResponseEntity<String> authFallback() {
        return ResponseEntity.status(503).body("Service is temporarily unavailable. Please try again later.");
    }
} 