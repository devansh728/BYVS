package com.byvs.backend.service.referral;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {
    private final Cache<String, RateLimitCounter> rateLimitCache;

    public RateLimitService() {
        this.rateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build();
    }

    public void checkRateLimit(String key, int maxAttempts, Duration period) {
        RateLimitCounter counter = rateLimitCache.get(key, k -> new RateLimitCounter());

        synchronized (counter) {
            if (counter.getCount() >= maxAttempts &&
                    Instant.now().isBefore(counter.getLastAttempt().plus(period))) {
                throw new IllegalArgumentException("Too many attempts. Please try again later.");
            }
            counter.increment();
        }
    }

    @Getter
    private static class RateLimitCounter {
        private int count = 0;
        private Instant lastAttempt = Instant.now();

        public void increment() {
            count++;
            lastAttempt = Instant.now();
        }
    }
}