package io.brokr.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting service for MFA operations to prevent brute force attacks.
 * Uses in-memory cache with automatic cleanup for enterprise-scale performance.
 */
@Slf4j
@Service
public class MfaRateLimitService {

    @Value("${mfa.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${mfa.rate-limit.window-minutes:15}")
    private int windowMinutes;

    @Value("${mfa.rate-limit.lockout-minutes:30}")
    private int lockoutMinutes;

    // In-memory rate limit tracking: userId -> AttemptInfo
    private final Map<String, AttemptInfo> attemptCache = new ConcurrentHashMap<>();
    
    // Cleanup threshold - clean up old entries when cache size exceeds this
    private static final int CLEANUP_THRESHOLD = 10000;

    /**
     * Check if user is rate limited (too many failed attempts)
     */
    public boolean isRateLimited(String userId) {
        AttemptInfo info = attemptCache.get(userId);
        if (info == null) {
            return false;
        }

        // Check if lockout period has expired
        if (info.isLockedOut() && info.getLastAttemptTime().plusMinutes(lockoutMinutes).isBefore(LocalDateTime.now())) {
            attemptCache.remove(userId);
            return false;
        }

        // Check if window has expired
        if (info.getFirstAttemptTime().plusMinutes(windowMinutes).isBefore(LocalDateTime.now())) {
            attemptCache.remove(userId);
            return false;
        }

        return info.isLockedOut() || info.getAttemptCount() >= maxAttempts;
    }

    /**
     * Record a failed MFA attempt
     */
    public void recordFailedAttempt(String userId) {
        attemptCache.compute(userId, (key, existing) -> {
            if (existing == null) {
                return new AttemptInfo(LocalDateTime.now(), 1);
            }

            // Reset if window expired
            if (existing.getFirstAttemptTime().plusMinutes(windowMinutes).isBefore(LocalDateTime.now())) {
                return new AttemptInfo(LocalDateTime.now(), 1);
            }

            // Increment attempt count and update last attempt time
            existing.incrementAttempt();
            return existing;
        });

        // Periodic cleanup to prevent memory leaks
        if (attemptCache.size() > CLEANUP_THRESHOLD) {
            cleanupExpiredEntries();
        }
    }

    /**
     * Record a successful MFA attempt (clear rate limit)
     */
    public void recordSuccessfulAttempt(String userId) {
        attemptCache.remove(userId);
    }

    /**
     * Get remaining attempts before lockout
     */
    public int getRemainingAttempts(String userId) {
        AttemptInfo info = attemptCache.get(userId);
        if (info == null) {
            return maxAttempts;
        }
        return Math.max(0, maxAttempts - info.getAttemptCount());
    }

    /**
     * Clean up expired entries from cache
     */
    private void cleanupExpiredEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(windowMinutes + lockoutMinutes);
        attemptCache.entrySet().removeIf(entry -> {
            AttemptInfo info = entry.getValue();
            return info.getFirstAttemptTime().isBefore(cutoff) && 
                   (!info.isLockedOut() || info.getLastAttemptTime().plusMinutes(lockoutMinutes).isBefore(cutoff));
        });
    }

    /**
     * Inner class to track attempt information
     */
    private static class AttemptInfo {
        private final LocalDateTime firstAttemptTime;
        private volatile LocalDateTime lastAttemptTime;
        private final AtomicInteger attemptCount;

        public AttemptInfo(LocalDateTime firstAttemptTime, int initialCount) {
            this.firstAttemptTime = firstAttemptTime;
            this.lastAttemptTime = LocalDateTime.now();
            this.attemptCount = new AtomicInteger(initialCount);
        }

        public LocalDateTime getFirstAttemptTime() {
            return firstAttemptTime;
        }

        public LocalDateTime getLastAttemptTime() {
            return lastAttemptTime;
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }

        public void incrementAttempt() {
            this.attemptCount.incrementAndGet();
            this.lastAttemptTime = LocalDateTime.now();
        }

        public boolean isLockedOut() {
            return attemptCount.get() >= 5; // Lockout threshold
        }
    }
}

