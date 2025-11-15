package io.brokr.kafka.service;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Message;
import io.brokr.core.model.MessageTransformation;
import io.brokr.core.model.SecurityProtocol;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Service for producing messages to Kafka topics.
 * Optimized for enterprise-scale replay/reprocessing operations.
 */
@Service
@Slf4j
public class KafkaProducerService {
    
    private final KafkaProducerPoolService producerPoolService;
    
    private static final int BATCH_SIZE = 1000;  // Batch size for efficient production
    private static final int LINGER_MS = 10;  // Wait 10ms to batch messages
    private static final int MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = 5;
    private static final int RETRIES = 3;
    private static final int REQUEST_TIMEOUT_MS = 30000;
    
    public KafkaProducerService(KafkaProducerPoolService producerPoolService) {
        this.producerPoolService = producerPoolService;
    }
    
    /**
     * Produces a single message to a topic.
     * 
     * @param cluster The Kafka cluster
     * @param topic The target topic
     * @param message The message to produce
     * @param transformation Optional transformation to apply
     * @return Future with RecordMetadata
     */
    public Future<RecordMetadata> produceMessage(
            KafkaCluster cluster,
            String topic,
            Message message,
            MessageTransformation transformation) {
        
        try (KafkaProducer<String, String> producer = createProducer(cluster)) {
            ProducerRecord<String, String> record = buildProducerRecord(topic, message, transformation);
            return producer.send(record);
        }
    }
    
    /**
     * Produces multiple messages to a topic in batches.
     * Optimized for enterprise-scale operations.
     * 
     * @param cluster The Kafka cluster
     * @param topic The target topic
     * @param messages List of messages to produce
     * @param transformation Optional transformation to apply to all messages
     * @return List of futures with RecordMetadata
     */
    public List<Future<RecordMetadata>> produceMessages(
            KafkaCluster cluster,
            String topic,
            List<Message> messages,
            MessageTransformation transformation) {
        
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Future<RecordMetadata>> futures = new ArrayList<>();
        
        try (KafkaProducer<String, String> producer = createProducer(cluster)) {
            // Process in batches for better performance
            for (int i = 0; i < messages.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, messages.size());
                List<Message> batch = messages.subList(i, end);
                
                for (Message message : batch) {
                    ProducerRecord<String, String> record = buildProducerRecord(topic, message, transformation);
                    Future<RecordMetadata> future = producer.send(record);
                    futures.add(future);
                }
                
                // Flush batch to ensure delivery
                producer.flush();
                
                log.debug("Produced batch of {} messages to topic {}", batch.size(), topic);
            }
        }
        
        return futures;
    }
    
    /**
     * Produces messages asynchronously with progress tracking.
     * 
     * @param cluster The Kafka cluster
     * @param topic The target topic
     * @param messages List of messages to produce
     * @param transformation Optional transformation
     * @param progressCallback Callback for progress updates
     * @return CompletableFuture that completes when all messages are produced
     */
    public CompletableFuture<Void> produceMessagesAsync(
            KafkaCluster cluster,
            String topic,
            List<Message> messages,
            MessageTransformation transformation,
            java.util.function.Consumer<Long> progressCallback) {
        
        return CompletableFuture.runAsync(() -> {
            try (KafkaProducer<String, String> producer = createProducer(cluster)) {
                long produced = 0;
                
                for (int i = 0; i < messages.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, messages.size());
                    List<Message> batch = messages.subList(i, end);
                    
                    for (Message message : batch) {
                        ProducerRecord<String, String> record = buildProducerRecord(topic, message, transformation);
                        producer.send(record, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Failed to produce message to topic {}: {}", topic, exception.getMessage());
                            }
                        });
                    }
                    
                    producer.flush();
                    produced += batch.size();
                    
                    if (progressCallback != null) {
                        progressCallback.accept(produced);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to produce messages to topic {}: {}", topic, e.getMessage(), e);
                throw new RuntimeException("Failed to produce messages", e);
            }
        });
    }
    
    /**
     * Produces a batch of messages synchronously (for streaming replay).
     * Optimized for enterprise-scale operations with minimal memory footprint.
     * 
     * @param cluster The Kafka cluster
     * @param topic The target topic
     * @param messages Batch of messages to produce (should be small batch, e.g., 10000)
     * @param transformation Optional transformation
     * @return Number of messages produced
     */
    public long produceBatch(
            KafkaCluster cluster,
            String topic,
            List<Message> messages,
            MessageTransformation transformation) {
        
        if (messages.isEmpty()) {
            return 0;
        }
        
        // Use pooled producer instead of creating new one
        // This prevents connection exhaustion under high concurrent load
        KafkaProducer<String, String> producer = producerPoolService.getOrCreateProducer(cluster);
        try {
            long produced = 0;
            
            for (Message message : messages) {
                ProducerRecord<String, String> record = buildProducerRecord(topic, message, transformation);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("Failed to produce message to topic {} partition {} offset {}: {}", 
                                topic, metadata != null ? metadata.partition() : "?", 
                                metadata != null ? metadata.offset() : "?", exception.getMessage());
                    }
                });
                produced++;
            }
            
            // Flush to ensure all messages are sent
            producer.flush();
            
            // Mark producer as used for cleanup tracking
            producerPoolService.markProducerUsed(cluster.getId());
            
            return produced;
        } catch (Exception e) {
            log.error("Failed to produce batch to topic {}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to produce batch", e);
        }
        // Note: We don't close the producer here - it's managed by the pool
    }
    
    /**
     * Builds a ProducerRecord from a Message, applying transformation if provided.
     */
    private ProducerRecord<String, String> buildProducerRecord(
            String topic,
            Message message,
            MessageTransformation transformation) {
        
        String key = message.getKey();
        String value = message.getValue();
        List<Header> headers = new ArrayList<>();
        
        // Apply transformation if provided
        if (transformation != null) {
            // Transform key
            if (transformation.getKeyTransformation() != null) {
                MessageTransformation.KeyTransformation keyTransform = transformation.getKeyTransformation();
                switch (keyTransform.getType()) {
                    case REMOVE:
                        key = null;
                        break;
                    case MODIFY:
                        key = keyTransform.getNewValue();
                        break;
                    case KEEP:
                    default:
                        // Keep original key
                        break;
                }
            }
            
            // Transform value
            if (transformation.getValueTransformation() != null) {
                MessageTransformation.ValueTransformation valueTransform = transformation.getValueTransformation();
                switch (valueTransform.getType()) {
                    case MODIFY:
                        value = valueTransform.getNewValue();
                        break;
                    case FORMAT_CONVERSION:
                        // Format conversion would be implemented here
                        // For now, keep original value
                        log.warn("Format conversion not yet implemented for target format: {}", 
                                valueTransform.getTargetFormat());
                        break;
                    case KEEP:
                    default:
                        // Keep original value
                        break;
                }
            }
            
            // Transform headers
            if (message.getHeaders() != null) {
                Map<String, String> transformedHeaders = new HashMap<>(message.getHeaders());
                
                // Remove headers
                if (transformation.getHeaderRemovals() != null) {
                    transformation.getHeaderRemovals().forEach(transformedHeaders::remove);
                }
                
                // Add/modify headers
                if (transformation.getHeaderAdditions() != null) {
                    transformedHeaders.putAll(transformation.getHeaderAdditions());
                }
                
                // Convert to Kafka headers
                transformedHeaders.forEach((k, v) -> 
                    headers.add(new RecordHeader(k, v.getBytes()))
                );
            }
        } else {
            // No transformation - use original headers
            if (message.getHeaders() != null) {
                message.getHeaders().forEach((k, v) -> 
                    headers.add(new RecordHeader(k, v.getBytes()))
                );
            }
        }
        
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, message.getTimestamp(), key, value, headers);
        return record;
    }
    
    /**
     * Creates a KafkaProducer with optimized configuration for enterprise-scale operations.
     */
    private KafkaProducer<String, String> createProducer(KafkaCluster cluster) {
        Properties props = new Properties();
        
        // Basic producer configuration
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Performance optimizations for enterprise-scale
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);  // 32KB batch size
        props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);  // Wait to batch
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");  // Compress for efficiency
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION);
        props.put(ProducerConfig.RETRIES_CONFIG, RETRIES);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS);
        props.put(ProducerConfig.ACKS_CONFIG, "1");  // Wait for leader acknowledgment
        
        // Buffer settings
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);  // 64MB buffer
        
        // Apply security configuration
        applySecurityConfiguration(props, cluster);
        
        return new KafkaProducer<>(props);
    }
    
    private void applySecurityConfiguration(Properties props, KafkaCluster cluster) {
        if (cluster.getSecurityProtocol() == null) {
            return;
        }
        
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, cluster.getSecurityProtocol().name());
        
        // Configure SASL if needed
        if (cluster.getSecurityProtocol() != SecurityProtocol.PLAINTEXT &&
                cluster.getSecurityProtocol() != SecurityProtocol.SSL &&
                cluster.getSaslMechanism() != null) {
            
            props.put(SaslConfigs.SASL_MECHANISM, cluster.getSaslMechanism());
            
            if (cluster.getSaslUsername() != null && cluster.getSaslPassword() != null) {
                props.put(SaslConfigs.SASL_JAAS_CONFIG,
                        String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                                cluster.getSaslUsername(), cluster.getSaslPassword()));
            }
        }
        
        // Configure SSL if needed
        if (cluster.getSecurityProtocol() == SecurityProtocol.SSL ||
                cluster.getSecurityProtocol() == SecurityProtocol.SASL_SSL) {
            
            if (cluster.getSslTruststoreLocation() != null) {
                props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, cluster.getSslTruststoreLocation());
            }
            if (cluster.getSslTruststorePassword() != null) {
                props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, cluster.getSslTruststorePassword());
            }
            if (cluster.getSslKeystoreLocation() != null) {
                props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, cluster.getSslKeystoreLocation());
            }
            if (cluster.getSslKeystorePassword() != null) {
                props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, cluster.getSslKeystorePassword());
            }
            if (cluster.getSslKeyPassword() != null) {
                props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, cluster.getSslKeyPassword());
            }
        }
        
        // Add any additional properties
        if (cluster.getProperties() != null) {
            props.putAll(cluster.getProperties());
        }
    }
}

