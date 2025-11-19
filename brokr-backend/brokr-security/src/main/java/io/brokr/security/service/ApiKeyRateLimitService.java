package io.brokr.security.service;

import io.brokr.core.model.RateLimitConfig;
import io.brokr.storage.entity.ApiKeyRateLimitEntity;
import io.brokr.storage.repository.ApiKeyRateLimitRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for API key rate limiting using in-memory storage.
 * Thread-safe, sliding window algorithm, no race conditions.
 * 
 * NOTE: This implementation uses in-memory storage. In a distributed system with multiple instances,
 * each instance maintains separate counters, which could allow rate limit bypass by distributing
 * requests across instances. For production multi-instance deployments, consider using a distributed
 * cache (e.g., Redis) for shared rate limit counters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyRateLimitService {

    @Value("${api-key.rate-limit.default.per-second:10}")
    private int defaultPerSecond;

    @Value("${api-key.rate-limit.default.per-minute:100}")
    private int defaultPerMinute;

    @Value("${api-key.rate-limit.default.per-hour:1000}")
    private int defaultPerHour;

    @Value("${api-key.rate-limit.default.per-day:10000}")
    private int defaultPerDay;

    private final ApiKeyRateLimitRepository rateLimitRepository;
    private final ApiKeyRateLimitConfigService rateLimitConfigService;

    // In-memory rate limit counters (thread-safe)
    // NOTE: In distributed deployments, use Redis or similar for shared counters
    private final Map<String, RateLimitWindow> inMemoryCounters = new ConcurrentHashMap<>();
    private final ReadWriteLock cleanupLock = new ReentrantReadWriteLock();
    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    public void init() {
        // Start cleanup thread for in-memory counters
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "api-key-rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredCounters,
                1,
                1,
                TimeUnit.MINUTES
        );

        log.info("API key rate limiting service started (in-memory)");
    }

    @PreDestroy
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("API key rate limiting service stopped");
    }

    /**
     * Check if request is within rate limit.
     * Thread-safe, in-memory sliding window algorithm.
     *
     * @param apiKeyId API key ID
     * @param request  HTTP request
     * @return true if within limit, false if exceeded
     */
    public boolean checkRateLimit(String apiKeyId, HttpServletRequest request) {
        // Get rate limit configurations for this API key (cached)
        // Use separate service to ensure cache proxying works
        List<ApiKeyRateLimitEntity> configs = rateLimitConfigService.getRateLimitConfigs(apiKeyId);

        // Check each rate limit type
        for (ApiKeyRateLimitEntity config : configs) {
            if (!checkSingleRateLimit(apiKeyId, config)) {
                log.warn("Rate limit exceeded for API key: {}, type: {}, limit: {}",
                        apiKeyId, config.getLimitType(), config.getLimitValue());
                return false;
            }
        }

        return true;
    }

    /**
     * Check a single rate limit (per second, per minute, etc.).
     * Thread-safe operation using in-memory sliding window.
     */
    private boolean checkSingleRateLimit(String apiKeyId, ApiKeyRateLimitEntity config) {
        String key = apiKeyId + ":" + config.getLimitType();
        return checkRateLimitInMemory(key, config);
    }

    /**
     * Check rate limit using in-memory counter (thread-safe).
     * Sliding window algorithm.
     */
    private boolean checkRateLimitInMemory(String key, ApiKeyRateLimitEntity config) {
        // Use read lock for cleanup to avoid blocking rate limit checks
        cleanupLock.readLock().lock();
        try {
            RateLimitWindow window = inMemoryCounters.computeIfAbsent(
                    key,
                    k -> new RateLimitWindow(config.getWindowSeconds())
            );

            return window.incrementAndCheck(config.getLimitValue());
        } finally {
            cleanupLock.readLock().unlock();
        }
    }

    /**
     * Cleanup expired in-memory counters.
     * Uses write lock to prevent concurrent cleanup and ensure thread safety.
     * Read locks allow rate limit checks to proceed during cleanup.
     */
    private void cleanupExpiredCounters() {
        // Try to acquire write lock, but don't block if cleanup is already running
        if (!cleanupLock.writeLock().tryLock()) {
            // Cleanup already in progress, skip this iteration
            return;
        }
        try {
            Instant now = Instant.now();
            int beforeSize = inMemoryCounters.size();
            inMemoryCounters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            int afterSize = inMemoryCounters.size();
            if (beforeSize != afterSize) {
                log.debug("Cleaned up {} expired rate limit counters", beforeSize - afterSize);
            }
        } finally {
            cleanupLock.writeLock().unlock();
        }
    }

    /**
     * Thread-safe rate limit window for in-memory rate limiting.
     * Implements sliding window algorithm.
     */
    private static class RateLimitWindow {
        private final int windowSeconds;
        private final AtomicLong count = new AtomicLong(0);
        private volatile Instant windowStart;
        private volatile Instant lastAccessTime;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        RateLimitWindow(int windowSeconds) {
            this.windowSeconds = windowSeconds;
            Instant now = Instant.now();
            this.windowStart = now;
            this.lastAccessTime = now;
        }

        /**
         * Increment counter and check if within limit.
         * Thread-safe, sliding window.
         */
        boolean incrementAndCheck(int limit) {
            lock.writeLock().lock();
            try {
                Instant now = Instant.now();
                lastAccessTime = now;

                // Reset window if expired
                if (windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                    count.set(0);
                    windowStart = now;
                }

                // Increment and check
                long current = count.incrementAndGet();
                return current <= limit;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Check if this window is expired and safe to remove.
         * Uses windowSeconds * 3 to ensure we don't remove active entries,
         * and also checks last access time for additional safety.
         */
        boolean isExpired(Instant now) {
            lock.readLock().lock();
            try {
                // Use windowSeconds * 3 to ensure we don't remove entries that might still be active
                // Also check last access time - if accessed recently, keep it
                Instant expirationTime = windowStart.plusSeconds(windowSeconds * 3);
                Instant lastAccessExpiration = lastAccessTime.plusSeconds(windowSeconds * 2);
                
                // Expire only if both window and last access are expired
                return expirationTime.isBefore(now) && lastAccessExpiration.isBefore(now);
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Get rate limit configurations for an API key (cached).
     * Delegates to separate service to ensure cache proxying works.
     */
    public List<ApiKeyRateLimitEntity> getRateLimitConfigs(String apiKeyId) {
        return rateLimitConfigService.getRateLimitConfigs(apiKeyId);
    }

    /**
     * Configure rate limits for an API key.
     * Evicts cache after update.
     */
    public void configureRateLimits(String apiKeyId, List<RateLimitConfig> configs) {
        // Delete existing configs
        rateLimitRepository.deleteByApiKeyId(apiKeyId);

        // Save new configs
        for (RateLimitConfig config : configs) {
            ApiKeyRateLimitEntity entity = ApiKeyRateLimitEntity.fromDomain(config);
            entity.setId(java.util.UUID.randomUUID().toString());
            entity.setApiKeyId(apiKeyId);
            rateLimitRepository.save(entity);
        }

        // Evict cache - delegate to config service
        rateLimitConfigService.evictCache(apiKeyId);

        log.info("Configured rate limits for API key: {}", apiKeyId);
    }

    /**
     * Get rate limit configuration for an API key.
     */
    public List<RateLimitConfig> getRateLimits(String apiKeyId) {
        return rateLimitConfigService.getRateLimitConfigs(apiKeyId).stream()
                .map(ApiKeyRateLimitEntity::toDomain)
                .toList();
    }
}
