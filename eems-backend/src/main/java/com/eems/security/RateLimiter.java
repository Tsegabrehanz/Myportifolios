package com.eems.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A basic in-memory sliding-window rate limiter, keyed by an arbitrary
 * string (e.g. a user's email). Deliberately simple - no external
 * dependency (Bucket4j, Redis) - which also means it only works
 * correctly for a single backend instance; a horizontally-scaled
 * deployment would need a shared store (Redis) instead, since each
 * instance would otherwise track its own independent counter.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, List<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    /**
     * @return true if this attempt is allowed (and is now recorded),
     *         false if the key has already hit maxAttempts within window.
     */
    public synchronized boolean tryConsume(String key, int maxAttempts, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        List<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new java.util.ArrayList<>());
        attempts.removeIf(t -> t.isBefore(cutoff));

        if (attempts.size() >= maxAttempts) {
            return false;
        }
        attempts.add(Instant.now());
        return true;
    }
}
