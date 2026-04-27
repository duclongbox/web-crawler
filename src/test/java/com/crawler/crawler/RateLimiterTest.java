package com.crawler.crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void firstAcquireIsImmediate() throws InterruptedException {
        RateLimiter rl = new RateLimiter();
        long start = System.currentTimeMillis();
        rl.acquire("example.com", 500);
        assertTrue(System.currentTimeMillis() - start < 100, "First acquire should not block");
    }

    @Test
    void secondAcquireRespectsDelay() throws InterruptedException {
        RateLimiter rl = new RateLimiter();
        long delayMs = 300;
        rl.acquire("example.com", delayMs);
        long start = System.currentTimeMillis();
        rl.acquire("example.com", delayMs);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= delayMs - 20, "Second acquire should wait ~" + delayMs + "ms, was " + elapsed + "ms");
    }

    @Test
    void differentDomainsAreIndependent() throws InterruptedException {
        RateLimiter rl = new RateLimiter();
        rl.acquire("a.com", 500);
        long start = System.currentTimeMillis();
        rl.acquire("b.com", 500);  // different domain — should not block
        assertTrue(System.currentTimeMillis() - start < 100, "Different domains should not share rate limit");
    }
}
