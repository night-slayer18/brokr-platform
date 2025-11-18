package io.brokr.security.service;

import io.brokr.storage.entity.ApiKeyRateLimitEntity;
import io.brokr.storage.repository.ApiKeyRateLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for retrieving API key rate limit configurations.
 * Separated from ApiKeyRateLimitService to enable Spring cache proxying.
 * When methods are called from within the same bean, Spring's proxy doesn't intercept,
 * so caching doesn't work. By separating into a different bean, caching works correctly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyRateLimitConfigService {
    
    @Value("${api-key.rate-limit.default.per-second:10}")
    private int defaultPerSecond;
    
    @Value("${api-key.rate-limit.default.per-minute:100}")
    private int defaultPerMinute;
    
    @Value("${api-key.rate-limit.default.per-hour:1000}")
    private int defaultPerHour;
    
    @Value("${api-key.rate-limit.default.per-day:10000}")
    private int defaultPerDay;
    
    private final ApiKeyRateLimitRepository rateLimitRepository;
    
    /**
     * Get rate limit configurations for an API key (cached).
     * Cache evicted when configs are updated.
     * This method is in a separate bean so Spring's cache proxy can intercept it.
     */
    @Cacheable(value = "rateLimitConfigs", key = "#apiKeyId", unless = "#result.isEmpty()")
    public List<ApiKeyRateLimitEntity> getRateLimitConfigs(String apiKeyId) {
        List<ApiKeyRateLimitEntity> configs = rateLimitRepository.findByApiKeyId(apiKeyId);
        
        // If no custom config, use defaults
        if (configs.isEmpty()) {
            configs = getDefaultRateLimits(apiKeyId);
        }
        
        return configs;
    }
    
    /**
     * Get default rate limits for an API key.
     */
    private List<ApiKeyRateLimitEntity> getDefaultRateLimits(String apiKeyId) {
        return List.of(
                createRateLimitEntity(apiKeyId, "per_second", defaultPerSecond, 1),
                createRateLimitEntity(apiKeyId, "per_minute", defaultPerMinute, 60),
                createRateLimitEntity(apiKeyId, "per_hour", defaultPerHour, 3600),
                createRateLimitEntity(apiKeyId, "per_day", defaultPerDay, 86400)
        );
    }
    
    private ApiKeyRateLimitEntity createRateLimitEntity(
            String apiKeyId,
            String limitType,
            int limitValue,
            int windowSeconds
    ) {
        return ApiKeyRateLimitEntity.builder()
                .apiKeyId(apiKeyId)
                .limitType(limitType)
                .limitValue(limitValue)
                .windowSeconds(windowSeconds)
                .build();
    }
    
    /**
     * Evict cache for a specific API key.
     * Called when rate limit configs are updated.
     */
    @CacheEvict(value = "rateLimitConfigs", key = "#apiKeyId")
    public void evictCache(String apiKeyId) {
        // Cache eviction handled by annotation
    }
}

