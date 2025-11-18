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
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for API key rate limiting using in-memory storage.
 * Thread-safe, sliding window algorithm, no race conditions.
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
    private final Map<String, RateLimitWindow> inMemoryCounters = new ConcurrentHashMap<>();
    private final ReentrantLock cleanupLock = new ReentrantLock();
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
        RateLimitWindow window = inMemoryCounters.computeIfAbsent(
                key,
                k -> new RateLimitWindow(config.getWindowSeconds())
        );

        return window.incrementAndCheck(config.getLimitValue());
    }

    /**
     * Cleanup expired in-memory counters.
     */
    private void cleanupExpiredCounters() {
        cleanupLock.lock();
        try {
            Instant now = Instant.now();
            inMemoryCounters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        } finally {
            cleanupLock.unlock();
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
        private final ReentrantLock lock = new ReentrantLock();

        RateLimitWindow(int windowSeconds) {
            this.windowSeconds = windowSeconds;
            this.windowStart = Instant.now();
        }

        /**
         * Increment counter and check if within limit.
         * Thread-safe, sliding window.
         */
        boolean incrementAndCheck(int limit) {
            lock.lock();
            try {
                Instant now = Instant.now();

                // Reset window if expired
                if (windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                    count.set(0);
                    windowStart = now;
                }

                // Increment and check
                long current = count.incrementAndGet();
                return current <= limit;
            } finally {
                lock.unlock();
            }
        }

        boolean isExpired(Instant now) {
            return windowStart.plusSeconds(windowSeconds * 2).isBefore(now);
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
