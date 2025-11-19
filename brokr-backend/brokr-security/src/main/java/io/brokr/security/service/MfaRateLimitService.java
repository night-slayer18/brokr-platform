package io.brokr.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting service for MFA operations to prevent brute force attacks.
 * Uses in-memory cache with automatic cleanup for enterprise-scale performance.
 * 
 * NOTE: This implementation uses in-memory storage. In a distributed system with multiple instances,
 * each instance maintains separate rate limit counters, which could allow rate limit bypass by distributing
 * requests across instances. For production multi-instance deployments, consider using a distributed
 * cache (e.g., Redis) for shared rate limit counters.
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
    // NOTE: In-memory storage - each instance has separate counters in distributed deployments
    private final Map<String, AttemptInfo> attemptCache = new ConcurrentHashMap<>();

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
     * Uses ConcurrentHashMap.compute() for atomic updates.
     * AtomicInteger in AttemptInfo ensures thread-safe increment operations.
     */
    public void recordFailedAttempt(String userId) {
        // compute() is atomic - ensures thread-safe creation and updates
        // AtomicInteger in AttemptInfo prevents race conditions in incrementAttempt()
        attemptCache.compute(userId, (key, existing) -> {
            if (existing == null) {
                return new AttemptInfo(LocalDateTime.now(), 1);
            }

            // Reset if window expired
            if (existing.getFirstAttemptTime().plusMinutes(windowMinutes).isBefore(LocalDateTime.now())) {
                return new AttemptInfo(LocalDateTime.now(), 1);
            }

            // Increment attempt count and update last attempt time
            // incrementAttempt() uses AtomicInteger for thread-safe increment
            existing.incrementAttempt();
            return existing;
        });
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
     * Scheduled cleanup of expired entries from cache.
     * Runs in background thread every 5 minutes to prevent memory leaks.
     * This avoids latency spikes that would occur if cleanup ran synchronously during rate limit checks.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupExpiredEntries() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(windowMinutes + lockoutMinutes);
            int beforeSize = attemptCache.size();
            attemptCache.entrySet().removeIf(entry -> {
                AttemptInfo info = entry.getValue();
                return info.getFirstAttemptTime().isBefore(cutoff) && 
                       (!info.isLockedOut() || info.getLastAttemptTime().plusMinutes(lockoutMinutes).isBefore(cutoff));
            });
            int afterSize = attemptCache.size();
            int removed = beforeSize - afterSize;
            if (removed > 0) {
                log.debug("Cleaned up {} expired rate limit entries. Cache size: {} -> {}", removed, beforeSize, afterSize);
            }
        } catch (Exception e) {
            log.error("Error during rate limit cache cleanup", e);
        }
    }

    /**
     * Inner class to track attempt information
     */
    private class AttemptInfo {
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
            // Use configurable maxAttempts instead of hardcoded value
            return attemptCount.get() >= maxAttempts;
        }
    }
}

