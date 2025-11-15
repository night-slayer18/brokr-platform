package io.brokr.kafka.service;

import io.brokr.core.model.TopicMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for topic metrics used for throughput calculation.
 * This component is extracted to avoid circular dependencies between
 * KafkaAdminService and MetricsCollectionService.
 */
@Component
@Slf4j
public class TopicMetricsCache {
    
    // Cache for previous metrics to calculate throughput (clusterId:topicName -> previous metrics)
    private final Map<String, TopicMetrics> cache = new ConcurrentHashMap<>();
    
    /**
     * Get cached metrics for a topic
     */
    public TopicMetrics get(String clusterId, String topicName) {
        String key = buildKey(clusterId, topicName);
        return cache.get(key);
    }
    
    /**
     * Put metrics into cache
     */
    public void put(String clusterId, String topicName, TopicMetrics metrics) {
        String key = buildKey(clusterId, topicName);
        cache.put(key, metrics);
    }
    
    /**
     * Remove cached metrics for a topic (called when topic is deleted)
     */
    public void remove(String clusterId, String topicName) {
        String key = buildKey(clusterId, topicName);
        TopicMetrics removed = cache.remove(key);
        if (removed != null) {
            log.debug("Removed cached metrics for topic: {} in cluster: {}", topicName, clusterId);
        }
    }
    
    /**
     * Clear all cached metrics (for testing or maintenance)
     */
    public void clear() {
        cache.clear();
        log.debug("Cleared all cached topic metrics");
    }
    
    /**
     * Get cache size (for monitoring)
     */
    public int size() {
        return cache.size();
    }
    
    private String buildKey(String clusterId, String topicName) {
        return clusterId + ":" + topicName;
    }
}

