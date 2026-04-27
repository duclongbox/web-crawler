package com.crawler.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-domain politeness rate limiter.
 *
 * Before making a request to a domain, call {@link #acquire(String, long)}.
 * The method blocks the calling thread until the required delay has elapsed
 * since the last request to that domain, then records the current time.
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    // domain → timestamp (ms) of last access
    private final ConcurrentHashMap<String, AtomicLong> lastAccessTimes = new ConcurrentHashMap<>();

    /**
     * Blocks until it is polite to issue a request to the given domain.
     *
     * @param domain       the host to rate-limit
     * @param minDelayMs   minimum gap between requests to this domain
     */
    public void acquire(String domain, long minDelayMs) throws InterruptedException {
        AtomicLong lastAccess = lastAccessTimes.computeIfAbsent(domain, d -> new AtomicLong(0));

        while (true) {
            long last = lastAccess.get();
            long now  = System.currentTimeMillis();
            long elapsed = now - last;

            if (elapsed >= minDelayMs) {
                // Try to claim this slot atomically
                if (lastAccess.compareAndSet(last, now)) return;
                // Another thread updated it; re-evaluate
            } else {
                long waitMs = minDelayMs - elapsed;
                log.debug("Rate limiting {} for {}ms", domain, waitMs);
                Thread.sleep(waitMs);
            }
        }
    }
}
