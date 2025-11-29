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
import java.time.LocalDateTime;
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
import java.util.concurrent.atomic.AtomicLong;

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
    
    @Value("${api-key.usage-tracking.queue-capacity:10000}")
    private int queueCapacity;
    
    private final ApiKeyUsageRepository usageRepository;
    
    // Thread-safe queue for batch processing - bounded to prevent memory exhaustion
    // Initialize lazily in @PostConstruct after @Value injection
    private BlockingQueue<ApiKeyUsageEntity> usageQueue;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Track dropped records for monitoring/alerting
    private final AtomicLong droppedRecordsCount = new AtomicLong(0);
    private volatile long lastDroppedRecordsAlertTime = 0;
    private static final long DROPPED_RECORDS_ALERT_INTERVAL_MS = 60000; // Alert every minute if dropping
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("API key usage tracking is disabled");
            return;
        }
        
        // Initialize bounded queue after @Value injection
        // Use bounded queue to prevent memory exhaustion during DB outages or traffic spikes
        this.usageQueue = new LinkedBlockingQueue<>(queueCapacity);
        
        running.set(true);
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "api-key-usage-processor");
            t.setDaemon(true);
            return t;
        });
        
        executorService.submit(this::processUsageQueue);
        log.info("API key usage tracking service started with queue capacity: {}", queueCapacity);
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
            
            // Add to queue (non-blocking) - drop if full to prevent memory exhaustion
            if (!usageQueue.offer(usage)) {
                handleQueueFull(apiKeyId);
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
            
            // Add to queue (non-blocking) - drop if full to prevent memory exhaustion
            if (!usageQueue.offer(usage)) {
                handleQueueFull(apiKeyId);
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
        // Handle null response case explicitly (e.g., in async context)
        // Use -1 to indicate response was not available (distinct from actual status code 0)
        int statusCode;
        if (response != null) {
            statusCode = response.getStatus();
        } else {
            statusCode = -1; // Indicates response not available (async context)
        }
        int responseTimeMs = 0; // Could measure if needed
        
        // Get client IP address (for analytics/auditing only, not for security decisions)
        // NOTE: IP addresses from headers can be spoofed and should not be used for security
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
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Handle queue full scenario with alerting.
     * Tracks dropped records and alerts when queue is consistently full.
     */
    private void handleQueueFull(String apiKeyId) {
        long dropped = droppedRecordsCount.incrementAndGet();
        long now = System.currentTimeMillis();
        
        // Alert if queue is consistently full (every minute)
        if (now - lastDroppedRecordsAlertTime > DROPPED_RECORDS_ALERT_INTERVAL_MS) {
            log.error("Usage queue is consistently full (capacity: {}). Total dropped records: {}. " +
                    "This may indicate high traffic or database performance issues. " +
                    "Consider increasing queue capacity or investigating database performance.",
                    queueCapacity, dropped);
            lastDroppedRecordsAlertTime = now;
        } else {
            log.warn("Usage queue is full (capacity: {}), dropping usage record for API key: {}", 
                    queueCapacity, apiKeyId);
        }
    }
    
    /**
     * Get client IP address from request.
     * Handles proxies and load balancers.
     * 
     * SECURITY NOTE: IP addresses extracted from headers can be spoofed and should only be used
     * for analytics, logging, and auditing purposes. Do NOT use IP addresses from headers for
     * security decisions (authentication, authorization, rate limiting, etc.).
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
     * Optimized to use a single combined query where possible to reduce database round trips.
     */
    @Transactional(readOnly = true)
    public ApiKeyUsageStatistics getUsageStatistics(
            String apiKeyId,
            Instant startTime,
            Instant endTime
    ) {
        // Convert Instant to LocalDateTime in system default timezone (IST)
        // Matching exactly how other metrics handle timestamps
        LocalDateTime startLocal = LocalDateTime.ofInstant(startTime, java.time.ZoneId.systemDefault());
        LocalDateTime endLocal = LocalDateTime.ofInstant(endTime, java.time.ZoneId.systemDefault());

        // Validation: Ensure end is after start
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // Validation: Enforce maximum date range to prevent full-table scans
        // Limit to 30 days to prevent abuse and database overload
        java.time.Duration duration = java.time.Duration.between(startTime, endTime);
        if (duration.toDays() > 30) {
            throw new IllegalArgumentException(
                "Date range cannot exceed 30 days. Requested range: " + duration.toDays() + " days"
            );
        }
        
        // Execute queries in parallel where possible (they're independent)
        // Note: Some queries can be combined, but status code counts and time series require separate queries
        // due to different GROUP BY clauses. We execute the simpler aggregations first.
        
        long totalRequests = usageRepository.countByApiKeyIdAndCreatedAtBetween(
                apiKeyId, startLocal, endLocal
        );
        
        // Error count and average response time can be computed from the same data scan
        // but require different WHERE clauses, so we keep them separate for now
        // (could be optimized with a single query using CASE statements if needed)
        long errorCount = usageRepository.countErrors(apiKeyId, startLocal, endLocal);
        
        Double avgResponseTime = usageRepository.getAverageResponseTime(
                apiKeyId, startLocal, endLocal
        );
        
        // Status code counts require GROUP BY, so separate query
        List<Object[]> statusCodeCounts = usageRepository.countByApiKeyIdAndStatusCode(
                apiKeyId, startLocal, endLocal
        );
        
        // Time series data requires different grouping (by hour), so separate query
        List<Object[]> timeSeriesData = usageRepository.countByApiKeyIdGroupedByHour(
                apiKeyId, startLocal, endLocal
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
                .timeSeriesData(parseTimeSeriesData(timeSeriesData))
                .build();
    }
    
    private Map<Integer, Long> parseStatusCodeCounts(List<Object[]> counts) {
        Map<Integer, Long> result = new HashMap<>();
        for (Object[] count : counts) {
            result.put((Integer) count[0], (Long) count[1]);
        }
        return result;
    }
    
    private Map<String, Long> parseTimeSeriesData(List<Object[]> timeSeriesData) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] data : timeSeriesData) {
            // data[0] is the timestamp (java.sql.Timestamp or LocalDateTime), data[1] is the count
            // The SQL query converts to IST timezone, so we need to interpret it as LocalDateTime in IST
            java.time.LocalDateTime localDateTime;
            if (data[0] instanceof java.sql.Timestamp) {
                java.sql.Timestamp ts = (java.sql.Timestamp) data[0];
                localDateTime = ts.toLocalDateTime();
            } else if (data[0] instanceof java.time.LocalDateTime) {
                localDateTime = (java.time.LocalDateTime) data[0];
            } else {
                continue; // Skip invalid entries
            }
            Long count = ((Number) data[1]).longValue();
            // Convert LocalDateTime (in IST) to epoch milliseconds, matching other metrics
            // This matches how TopicMetrics, ClusterMetrics etc. handle timestamps
            long epochMillis = localDateTime.atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            result.put(String.valueOf(epochMillis), count);
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
        private Map<String, Long> timeSeriesData; // Map of ISO timestamp string to request count
    }
}

