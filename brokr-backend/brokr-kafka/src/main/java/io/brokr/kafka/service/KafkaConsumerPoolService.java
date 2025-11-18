package io.brokr.kafka.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Consumer pool for long-running Kafka consumer operations.
 * <p>
 * This service maintains a pool of reusable KafkaConsumer instances to avoid
 * the overhead of creating and destroying consumers for continuous operations.
 * <p>
 * <b>Use this pool for:</b>
 * <ul>
 *   <li>Real-time topic streaming (WebSocket/SSE)</li>
 *   <li>Continuous consumer lag monitoring</li>
 *   <li>Background metric collection</li>
 *   <li>Topic tail/follow operations (like "tail -f")</li>
 *   <li>Long-running subscriptions with fixed assignments</li>
 * </ul>
 * <p>
 * <b>Do NOT use this pool for:</b>
 * <ul>
 *   <li>Ad-hoc message viewing with different partitions/offsets</li>
 *   <li>One-time queries (use try-with-resources instead)</li>
 *   <li>Operations with manual partition assignment that change frequently</li>
 * </ul>
 * <p>
 * The pool automatically cleans up idle consumers after 10 minutes to prevent
 * resource leaks. Cleanup runs every 5 minutes.
 * <p>
 * Thread-safe: Uses ConcurrentHashMap for concurrent access.
 */
@Service
@Slf4j
public class KafkaConsumerPoolService {

    private static final long IDLE_TIMEOUT_MINUTES = 10;

    private final Map<String, ConsumerEntry> consumerPool = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Cleanup idle consumers every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupIdleConsumers, 5, 5, TimeUnit.MINUTES);
        log.info("KafkaConsumerPoolService initialized");
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down KafkaConsumerPoolService, closing {} consumers", consumerPool.size());
        consumerPool.values().forEach(entry -> {
            try {
                entry.consumer.close();
            } catch (Exception e) {
                log.warn("Error closing consumer: {}", e.getMessage());
            }
        });
        consumerPool.clear();
        cleanupExecutor.shutdown();
    }

    public KafkaConsumer<String, String> getOrCreateConsumer(String clusterId, Properties props) {
        String groupId = (String) props.get("group.id");
        
        // Security: Include security-sensitive properties in pool key to prevent cross-tenant data leakage
        // Different credentials, SSL configs, or isolation settings should get separate consumer instances
        String securityContext = buildSecurityContextHash(props);
        String key = clusterId + "-" + groupId + "-" + securityContext;

        ConsumerEntry entry = consumerPool.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.lastUsed = Instant.now();
                return existing;
            }
            log.info("Creating new consumer for cluster: {}, group: {}, securityContext: {}", 
                    clusterId, groupId, securityContext);
            return new ConsumerEntry(new KafkaConsumer<>(props));
        });

        return entry.consumer;
    }
    
    /**
     * Build a hash of security-sensitive properties to include in pool key.
     * This ensures different security contexts get separate consumer instances.
     */
    private String buildSecurityContextHash(Properties props) {
        StringBuilder sb = new StringBuilder();
        // Include security protocol
        sb.append(props.getProperty("security.protocol", ""));
        // Include SASL mechanism and credentials
        sb.append("|").append(props.getProperty("sasl.mechanism", ""));
        sb.append("|").append(props.getProperty("sasl.jaas.config", ""));
        // Include SSL configs
        sb.append("|").append(props.getProperty("ssl.truststore.location", ""));
        sb.append("|").append(props.getProperty("ssl.keystore.location", ""));
        // Hash to keep it short and avoid storing credentials in key
        return String.valueOf(sb.toString().hashCode());
    }

    /**
     * Return a consumer to the pool and update its last used timestamp.
     * CRITICAL FIX: Must include security context in key to match getOrCreateConsumer.
     * 
     * @param clusterId The cluster ID
     * @param props The same Properties used to create the consumer (includes security context)
     */
    public void returnConsumer(String clusterId, Properties props) {
        String groupId = (String) props.get("group.id");
        
        // CRITICAL FIX: Include security context in key (same as getOrCreateConsumer)
        // This ensures the key matches and the consumer can be properly tracked
        String securityContext = buildSecurityContextHash(props);
        String key = clusterId + "-" + groupId + "-" + securityContext;
        
        ConsumerEntry entry = consumerPool.get(key);
        if (entry != null) {
            entry.lastUsed = Instant.now();
            log.debug("Returned consumer to pool: {}", key);
        } else {
            log.warn("Consumer not found in pool when returning: {}", key);
        }
    }
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use returnConsumer(String, Properties) instead
     */
    @Deprecated
    public void returnConsumer(String clusterId, String groupId) {
        // Best effort: search for any consumer matching cluster + group
        // This won't work correctly if multiple security contexts exist
        String prefix = clusterId + "-" + groupId + "-";
        for (String key : consumerPool.keySet()) {
            if (key.startsWith(prefix)) {
                ConsumerEntry entry = consumerPool.get(key);
                if (entry != null) {
                    entry.lastUsed = Instant.now();
                    log.debug("Returned consumer to pool (legacy): {}", key);
                    return;
                }
            }
        }
        log.warn("Consumer not found in pool when returning (legacy): {}-{}", clusterId, groupId);
    }

    private void cleanupIdleConsumers() {
        Instant cutoff = Instant.now().minusSeconds(IDLE_TIMEOUT_MINUTES * 60);
        int cleaned = 0;

        for (Map.Entry<String, ConsumerEntry> entry : consumerPool.entrySet()) {
            if (entry.getValue().lastUsed.isBefore(cutoff)) {
                try {
                    entry.getValue().consumer.close();
                    consumerPool.remove(entry.getKey());
                    cleaned++;
                } catch (Exception e) {
                    log.warn("Error closing idle consumer {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} idle consumers. Pool size: {}", cleaned, consumerPool.size());
        }
    }

    private static class ConsumerEntry {
        final KafkaConsumer<String, String> consumer;
        volatile Instant lastUsed;

        ConsumerEntry(KafkaConsumer<String, String> consumer) {
            this.consumer = consumer;
            this.lastUsed = Instant.now();
        }
    }
}