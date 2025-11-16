package io.brokr.security.service;

import io.brokr.storage.entity.ApiKeyUsageEntity;
import io.brokr.storage.repository.ApiKeyUsageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for tracking API key usage with batch inserts for performance.
 * Thread-safe, async processing, no bottlenecks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyUsageService {
    
    @Value("${api-key.usage-tracking.batch-size:100}")
    private int batchSize;
    
    @Value("${api-key.usage-tracking.batch-interval-seconds:5}")
    private int batchIntervalSeconds;
    
    @Value("${api-key.usage-tracking.enabled:true}")
    private boolean enabled;
    
    private final ApiKeyUsageRepository usageRepository;
    
    // Thread-safe queue for batch processing
    private final BlockingQueue<ApiKeyUsageEntity> usageQueue = new LinkedBlockingQueue<>();
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("API key usage tracking is disabled");
            return;
        }
        
        running.set(true);
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "api-key-usage-processor");
            t.setDaemon(true);
            return t;
        });
        
        executorService.submit(this::processUsageQueue);
        log.info("API key usage tracking service started");
    }
    
    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("API key usage tracking service stopped");
    }
    
    /**
     * Record API key usage (async, non-blocking).
     * Thread-safe operation.
     */
    @Async
    public void recordUsageAsync(String apiKeyId, String userId, String organizationId, HttpServletRequest request) {
        if (!enabled) {
            return;
        }
        
        try {
            ApiKeyUsageEntity usage = createUsageEntity(
                    apiKeyId,
                    userId,
                    organizationId,
                    request,
                    null // Response not available in async context
            );
            
            // Add to queue (non-blocking)
            if (!usageQueue.offer(usage)) {
                log.warn("Usage queue is full, dropping usage record for API key: {}", apiKeyId);
            }
        } catch (Exception e) {
            log.error("Failed to record API key usage", e);
        }
    }
    
    /**
     * Record API key usage with response (async, non-blocking).
     * Thread-safe operation.
     */
    @Async
    public void recordUsageAsync(
            String apiKeyId,
            String userId,
            String organizationId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!enabled) {
            return;
        }
        
        try {
            ApiKeyUsageEntity usage = createUsageEntity(
                    apiKeyId,
                    userId,
                    organizationId,
                    request,
                    response
            );
            
            // Add to queue (non-blocking)
            if (!usageQueue.offer(usage)) {
                log.warn("Usage queue is full, dropping usage record for API key: {}", apiKeyId);
            }
        } catch (Exception e) {
            log.error("Failed to record API key usage", e);
        }
    }
    
    /**
     * Process usage queue in batches (background thread).
     * Thread-safe, handles errors gracefully.
     */
    private void processUsageQueue() {
        List<ApiKeyUsageEntity> batch = new ArrayList<>(batchSize);
        
        while (running.get() || !usageQueue.isEmpty()) {
            try {
                // Collect batch of records or wait for timeout
                ApiKeyUsageEntity usage = usageQueue.poll(batchIntervalSeconds, TimeUnit.SECONDS);
                
                if (usage != null) {
                    batch.add(usage);
                }
                
                // Process batch if we have enough records or timeout occurred
                if (batch.size() >= batchSize || (usage == null && !batch.isEmpty())) {
                    processBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Process remaining batch before exiting
                if (!batch.isEmpty()) {
                    processBatch(batch);
                    batch.clear();
                }
                break;
            } catch (Exception e) {
                log.error("Error processing usage queue", e);
                // Clear batch on error to prevent memory leak
                batch.clear();
            }
        }
        
        // Process any remaining records
        if (!batch.isEmpty()) {
            processBatch(batch);
        }
    }
    
    /**
     * Process a batch of usage records.
     * Thread-safe batch insert.
     */
    @Transactional
    public void processBatch(List<ApiKeyUsageEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            usageRepository.saveAll(batch);
            log.debug("Processed {} usage records", batch.size());
        } catch (Exception e) {
            log.error("Failed to save usage batch of {} records", batch.size(), e);
            // Could implement retry logic here if needed
        }
    }
    
    /**
     * Create usage entity from request.
     */
    private ApiKeyUsageEntity createUsageEntity(
            String apiKeyId,
            String userId,
            String organizationId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        int statusCode = response != null ? response.getStatus() : 0;
        int responseTimeMs = 0; // Could measure if needed
        
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String endpoint = request.getRequestURI();
        String method = request.getMethod();
        
        return ApiKeyUsageEntity.builder()
                .apiKeyId(apiKeyId)
                .userId(userId)
                .organizationId(organizationId)
                .endpoint(endpoint)
                .method(method)
                .statusCode(statusCode)
                .responseTimeMs(responseTimeMs > 0 ? responseTimeMs : null)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(Instant.now())
                .build();
    }
    
    /**
     * Get client IP address from request.
     * Handles proxies and load balancers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Handle comma-separated IPs (X-Forwarded-For can have multiple)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * Get usage statistics for an API key.
     */
    @Transactional(readOnly = true)
    public ApiKeyUsageStatistics getUsageStatistics(
            String apiKeyId,
            Instant startTime,
            Instant endTime
    ) {
        long totalRequests = usageRepository.countByApiKeyIdAndCreatedAtBetween(
                apiKeyId, startTime, endTime
        );
        
        long errorCount = usageRepository.countErrors(apiKeyId, startTime, endTime);
        
        Double avgResponseTime = usageRepository.getAverageResponseTime(
                apiKeyId, startTime, endTime
        );
        
        List<Object[]> statusCodeCounts = usageRepository.countByApiKeyIdAndStatusCode(
                apiKeyId, startTime, endTime
        );
        
        return ApiKeyUsageStatistics.builder()
                .apiKeyId(apiKeyId)
                .startTime(startTime)
                .endTime(endTime)
                .totalRequests(totalRequests)
                .errorCount(errorCount)
                .successCount(totalRequests - errorCount)
                .errorRate(totalRequests > 0 ? (double) errorCount / totalRequests : 0.0)
                .averageResponseTimeMs(avgResponseTime != null ? avgResponseTime.intValue() : null)
                .statusCodeCounts(parseStatusCodeCounts(statusCodeCounts))
                .build();
    }
    
    private Map<Integer, Long> parseStatusCodeCounts(List<Object[]> counts) {
        Map<Integer, Long> result = new HashMap<>();
        for (Object[] count : counts) {
            result.put((Integer) count[0], (Long) count[1]);
        }
        return result;
    }
    
    /**
     * Usage statistics result.
     */
    @lombok.Data
    @lombok.Builder
    public static class ApiKeyUsageStatistics {
        private String apiKeyId;
        private Instant startTime;
        private Instant endTime;
        private long totalRequests;
        private long successCount;
        private long errorCount;
        private double errorRate;
        private Integer averageResponseTimeMs;
        private Map<Integer, Long> statusCodeCounts;
    }
}

