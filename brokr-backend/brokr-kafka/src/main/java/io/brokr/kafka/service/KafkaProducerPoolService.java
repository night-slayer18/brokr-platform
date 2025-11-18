package io.brokr.kafka.service;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.SecurityProtocol;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Producer pool for Kafka producer operations.
 * <p>
 * This service maintains a pool of reusable KafkaProducer instances per cluster
 * to avoid the overhead of creating and destroying producers for each operation.
 * This is critical for enterprise-scale replay operations where many concurrent
 * replays might be running.
 * <p>
 * <b>Benefits:</b>
 * <ul>
 *   <li>Reduces connection overhead (producers are expensive to create)</li>
 *   <li>Prevents connection exhaustion under high load</li>
 *   <li>Improves performance by reusing connections</li>
 *   <li>Thread-safe for concurrent access</li>
 * </ul>
 * <p>
 * The pool automatically cleans up idle producers after 30 minutes to prevent
 * resource leaks. Cleanup runs every 10 minutes.
 * <p>
 * Thread-safe: Uses ConcurrentHashMap for concurrent access.
 */
@Service
@Slf4j
public class KafkaProducerPoolService {
    
    private static final long IDLE_TIMEOUT_MINUTES = 30;
    
    private final Map<String, ProducerEntry> producerPool = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    // No constructor dependency needed - we create producers directly to avoid circular dependency
    
    @PostConstruct
    public void init() {
        // Cleanup idle producers every 10 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupIdleProducers, 10, 10, TimeUnit.MINUTES);
        log.info("KafkaProducerPoolService initialized");
    }
    
    @PreDestroy
    public void destroy() {
        log.info("Shutting down KafkaProducerPoolService, closing {} producers", producerPool.size());
        producerPool.values().forEach(entry -> {
            try {
                entry.producer.close();
            } catch (Exception e) {
                log.warn("Error closing producer: {}", e.getMessage());
            }
        });
        producerPool.clear();
        cleanupExecutor.shutdown();
    }
    
    /**
     * Gets or creates a producer for the given cluster.
     * Producers are reused per cluster to prevent connection exhaustion.
     * 
     * @param cluster The Kafka cluster
     * @return A KafkaProducer instance (reused if available)
     */
    public KafkaProducer<String, String> getOrCreateProducer(KafkaCluster cluster) {
        String clusterId = cluster.getId();
        
        ProducerEntry entry = producerPool.compute(clusterId, (k, existing) -> {
            if (existing != null) {
                existing.lastUsed = Instant.now();
                return existing;
            }
            log.info("Creating new producer for cluster: {}", clusterId);
            // Use reflection to access the private createProducer method
            // Or we can make it package-private
            KafkaProducer<String, String> producer = createProducerForCluster(cluster);
            return new ProducerEntry(producer);
        });
        
        return entry.producer;
    }
    
    /**
     * Creates a producer for a cluster.
     * This method contains the producer creation logic to avoid circular dependencies.
     */
    private KafkaProducer<String, String> createProducerForCluster(KafkaCluster cluster) {
        Properties props = new Properties();
        
        // Basic producer configuration
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                org.apache.kafka.common.serialization.StringSerializer.class.getName());
        
        // Performance optimizations for enterprise-scale
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BATCH_SIZE_CONFIG, 32768);  // 32KB batch size
        props.put(org.apache.kafka.clients.producer.ProducerConfig.LINGER_MS_CONFIG, 10);  // Wait to batch
        props.put(org.apache.kafka.clients.producer.ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");  // Compress for efficiency
        props.put(org.apache.kafka.clients.producer.ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG, 3);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "1");  // Wait for leader acknowledgment
        
        // Buffer settings
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);  // 64MB buffer
        
        // Apply security configuration
        applySecurityConfiguration(props, cluster);
        
        return new KafkaProducer<>(props);
    }
    
    private void applySecurityConfiguration(Properties props, KafkaCluster cluster) {
        if (cluster.getSecurityProtocol() == null) {
            return;
        }
        
        props.put(org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, cluster.getSecurityProtocol().name());
        
        if (cluster.getSecurityProtocol() == SecurityProtocol.SASL_PLAINTEXT ||
            cluster.getSecurityProtocol() == SecurityProtocol.SASL_SSL) {
            if (cluster.getSaslMechanism() != null) {
                props.put(org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM, cluster.getSaslMechanism());
            }
            // Build JAAS config from username/password if available
            // Security: Sanitize credentials to prevent injection attacks
            if (cluster.getSaslUsername() != null && cluster.getSaslPassword() != null) {
                String sanitizedUsername = sanitizeJaasValue(cluster.getSaslUsername());
                String sanitizedPassword = sanitizeJaasValue(cluster.getSaslPassword());
                String jaasConfig = String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    sanitizedUsername, sanitizedPassword);
                props.put(org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            }
        }
        
        if (cluster.getSecurityProtocol() == SecurityProtocol.SSL ||
            cluster.getSecurityProtocol() == SecurityProtocol.SASL_SSL) {
            // SSL configuration
            if (cluster.getSslTruststoreLocation() != null) {
                props.put(org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, cluster.getSslTruststoreLocation());
            }
            if (cluster.getSslTruststorePassword() != null) {
                props.put(org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, cluster.getSslTruststorePassword());
            }
            if (cluster.getSslKeystoreLocation() != null) {
                props.put(org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, cluster.getSslKeystoreLocation());
            }
            if (cluster.getSslKeystorePassword() != null) {
                props.put(org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, cluster.getSslKeystorePassword());
            }
            if (cluster.getSslKeyPassword() != null) {
                props.put(org.apache.kafka.common.config.SslConfigs.SSL_KEY_PASSWORD_CONFIG, cluster.getSslKeyPassword());
            }
        }
        
        // Add any additional properties
        if (cluster.getProperties() != null) {
            props.putAll(cluster.getProperties());
        }
    }
    
    /**
     * Marks a producer as recently used (for cleanup tracking).
     */
    public void markProducerUsed(String clusterId) {
        ProducerEntry entry = producerPool.get(clusterId);
        if (entry != null) {
            entry.lastUsed = Instant.now();
        }
    }
    
    /**
     * Sanitize JAAS config values to prevent injection attacks.
     * Escapes quotes, backslashes, and semicolons that could break the JAAS config.
     */
    private String sanitizeJaasValue(String value) {
        if (value == null) {
            return "";
        }
        // Escape backslashes first, then quotes, then semicolons
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace(";", "\\;");
    }
    
    private void cleanupIdleProducers() {
        Instant cutoff = Instant.now().minusSeconds(IDLE_TIMEOUT_MINUTES * 60);
        int cleaned = 0;
        
        for (Map.Entry<String, ProducerEntry> entry : producerPool.entrySet()) {
            if (entry.getValue().lastUsed.isBefore(cutoff)) {
                try {
                    entry.getValue().producer.close();
                    producerPool.remove(entry.getKey());
                    cleaned++;
                } catch (Exception e) {
                    log.warn("Error closing idle producer {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        
        if (cleaned > 0) {
            log.info("Cleaned up {} idle producers. Pool size: {}", cleaned, producerPool.size());
        }
    }
    
    private static class ProducerEntry {
        final KafkaProducer<String, String> producer;
        volatile Instant lastUsed;
        
        ProducerEntry(KafkaProducer<String, String> producer) {
            this.producer = producer;
            this.lastUsed = Instant.now();
        }
    }
}

