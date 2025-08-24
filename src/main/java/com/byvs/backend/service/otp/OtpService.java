package com.byvs.backend.service.otp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final Cache<String, String> otpCache;
    private final Map<String, RequestWindow> rateLimitMap = new ConcurrentHashMap<>();
    private final int ttlMinutes;
    private final int maxRequestsPer5m;
    private final SecureRandom random = new SecureRandom();

    public OtpService(@Value("${otp.ttl-minutes:5}") int ttlMinutes,
                      @Value("${otp.max-requests-per-5m:3}") int maxRequestsPer5m) {
        this.ttlMinutes = ttlMinutes;
        this.maxRequestsPer5m = maxRequestsPer5m;
        this.otpCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
            .maximumSize(100_000)
            .build();
    }

    public String generateAndStore(String phone) {
        enforceRateLimit(phone);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        otpCache.put(phone, otp);
        return otp;
    }

    public boolean verifyAndInvalidate(String phone, String provided) {
        String expected = otpCache.getIfPresent(phone);
        if (expected != null && expected.equals(provided)) {
            otpCache.invalidate(phone);
            return true;
        }
        return false;
    }

    private void enforceRateLimit(String phone) {
        RequestWindow window = rateLimitMap.computeIfAbsent(phone, k -> new RequestWindow());
        synchronized (window) {
            Instant now = Instant.now();
            if (window.resetAt.isBefore(now)) {
                window.reset(now.plus(Duration.ofMinutes(5)));
            }
            if (window.count >= maxRequestsPer5m) {
                throw new OtpRateLimitException("Too many OTP requests. Please try again later.");
            }
            window.count++;
        }
    }

    private static class RequestWindow {
        int count = 0;
        Instant resetAt = Instant.EPOCH;
        void reset(Instant next) {
            count = 0;
            resetAt = next;
        }
    }
}


