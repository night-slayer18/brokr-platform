package io.brokr.kafka.service;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Message;
import io.brokr.core.model.SecurityProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {
    
    private final KafkaConnectionService kafkaConnectionService;
    private final MessageFilterService messageFilterService;

    private static final int FETCH_MIN_BYTES = 1024;
    private static final int FETCH_MAX_WAIT_MS = 100;
    private static final int DEFAULT_MAX_POLL_RECORDS = 5000;
    private static final int SESSION_TIMEOUT_MS = 10000;
    private static final int HEARTBEAT_INTERVAL_MS = 3000;
    private static final int MAX_EMPTY_POLLS = 3;
    private static final long POLL_TIMEOUT_MS = 500;

    public List<Message> consumeMessages(KafkaCluster cluster, String topic, List<Integer> partitions, String offset, Integer limit) {
        long startTime = System.currentTimeMillis();
        Properties props = buildConsumerProperties(cluster, limit);

        List<Message> messages;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> topicPartitions = assignPartitions(consumer, topic, partitions);

            if (topicPartitions.isEmpty()) {
                log.warn("No partitions found for topic {} in cluster {}", topic, cluster.getName());
                return Collections.emptyList();
            }

            // Handle "latest" by seeking backwards from end
            boolean isLatest = "latest".equalsIgnoreCase(offset);
            if (isLatest) {
                seekToLatestMessages(consumer, topicPartitions, limit);
            } else {
                seekToOffset(consumer, topicPartitions, offset);
            }

            messages = pollMessages(consumer, limit, topic, cluster.getName());

            // Reverse messages when fetching latest so newest appear first
            if (isLatest && !messages.isEmpty()) {
                Collections.reverse(messages);
            }

        } catch (Exception e) {
            log.error("Failed to consume messages from topic {} in cluster {}: {}",
                    topic, cluster.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to consume messages: " + e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Fetched {} messages from topic {} in {}ms", messages.size(), topic, duration);

        return messages;
    }

    private Properties buildConsumerProperties(KafkaCluster cluster, Integer limit) {
        Properties props = new Properties();

        // Basic consumer configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "brokr-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Performance optimizations - CRITICAL for pagination performance
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, String.valueOf(FETCH_MIN_BYTES));
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, String.valueOf(FETCH_MAX_WAIT_MS));

        // Dynamic max poll records based on user's limit
        int maxPollRecords = Math.max(limit, DEFAULT_MAX_POLL_RECORDS);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));

        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(SESSION_TIMEOUT_MS));
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, String.valueOf(HEARTBEAT_INTERVAL_MS));

        // Buffer sizes - optimized for monitoring platform with variable message sizes
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, String.valueOf(52428800)); // 50MB - unchanged
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, String.valueOf(2097152)); // 2MB - handle larger messages
        props.put(ConsumerConfig.CHECK_CRCS_CONFIG, "false"); // Skip CRC checks for speed

        // Apply security configuration
        applySecurityConfiguration(props, cluster);

        return props;
    }

    /**
     * Builds consumer properties specifically for replay operations.
     * Uses a unique consumer group ID per job to prevent conflicts in concurrent scenarios.
     * Since we use manual partition assignment, the group ID is mainly for coordination,
     * but unique IDs prevent rebalancing issues when multiple replays run concurrently.
     * 
     * @param cluster The Kafka cluster
     * @param batchSize Batch size for processing
     * @param jobId Optional job ID to create unique consumer group (null = use fixed ID)
     */
    private Properties buildConsumerPropertiesForReplay(KafkaCluster cluster, Integer batchSize, String jobId) {
        Properties props = new Properties();

        // Basic consumer configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        // Use unique consumer group ID per job to prevent conflicts in concurrent scenarios
        // This prevents consumer group rebalancing when multiple replays run simultaneously
        // Format: brokr-replay-{jobId} or brokr-replay-{uuid} if jobId not provided
        String groupId = jobId != null ? "brokr-replay-" + jobId : "brokr-replay-" + UUID.randomUUID();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Performance optimizations
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, String.valueOf(FETCH_MIN_BYTES));
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, String.valueOf(FETCH_MAX_WAIT_MS));

        // Use batch size for max poll records
        int maxPollRecords = Math.max(batchSize, DEFAULT_MAX_POLL_RECORDS);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));

        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(SESSION_TIMEOUT_MS));
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, String.valueOf(HEARTBEAT_INTERVAL_MS));

        // Buffer sizes
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, String.valueOf(52428800)); // 50MB
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, String.valueOf(2097152)); // 2MB
        props.put(ConsumerConfig.CHECK_CRCS_CONFIG, "false");

        // Apply security configuration
        applySecurityConfiguration(props, cluster);

        return props;
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

    private List<TopicPartition> assignPartitions(KafkaConsumer<String, String> consumer,
                                                  String topic,
                                                  List<Integer> partitions) {
        List<TopicPartition> topicPartitions;

        if (partitions != null && !partitions.isEmpty()) {
            topicPartitions = partitions.stream()
                    .map(p -> new TopicPartition(topic, p))
                    .collect(Collectors.toList());
        } else {
            consumer.subscribe(Collections.singletonList(topic));
            consumer.poll(Duration.ofMillis(100)); // Initial poll for assignment
            topicPartitions = new ArrayList<>(consumer.assignment());
        }

        if (!topicPartitions.isEmpty()) {
            consumer.assign(topicPartitions);
        }

        return topicPartitions;
    }

    private void seekToLatestMessages(KafkaConsumer<String, String> consumer,
                                      List<TopicPartition> topicPartitions,
                                      int limit) {
        // First, seek to end to get the latest offsets
        consumer.seekToEnd(topicPartitions);

        // For each partition, calculate how far back we need to seek
        for (TopicPartition tp : topicPartitions) {
            long endOffset = consumer.position(tp);
            long beginOffset = 0;

            try {
                Map<TopicPartition, Long> beginOffsets = consumer.beginningOffsets(Collections.singletonList(tp));
                beginOffset = beginOffsets.getOrDefault(tp, 0L);
            } catch (Exception e) {
                log.warn("Could not get beginning offset for partition {}, using 0", tp.partition());
            }

            // Calculate the offset to start reading from
            // We want to read 'limit' messages backwards from the end
            long targetOffset = Math.max(beginOffset, endOffset - limit);

            log.debug("Partition {}: seeking to offset {} (end={}, begin={}, limit={})",
                    tp.partition(), targetOffset, endOffset, beginOffset, limit);

            consumer.seek(tp, targetOffset);
        }
    }

    private void seekToOffset(KafkaConsumer<String, String> consumer,
                              List<TopicPartition> topicPartitions,
                              String offset) {
        if (offset == null || "earliest".equalsIgnoreCase(offset)) {
            consumer.seekToBeginning(topicPartitions);
        } else {
            try {
                long numericOffset = Long.parseLong(offset);
                for (TopicPartition tp : topicPartitions) {
                    consumer.seek(tp, numericOffset);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid offset value: {}. Defaulting to beginning.", offset);
                consumer.seekToBeginning(topicPartitions);
            }
        }
    }

    private List<Message> pollMessages(KafkaConsumer<String, String> consumer,
                                       Integer limit,
                                       String topic,
                                       String clusterName) {
        List<Message> messages = new ArrayList<>(limit);
        int recordsRead = 0;
        int emptyPollCount = 0;
        int pollCount = 0;

        while (recordsRead < limit && emptyPollCount < MAX_EMPTY_POLLS) {
            pollCount++;
            ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS));

            if (consumerRecords.isEmpty()) {
                emptyPollCount++;
                log.debug("Empty poll #{} for topic {}", emptyPollCount, topic);
                continue;
            }

            emptyPollCount = 0; // Reset on successful poll

            for (ConsumerRecord<String, String> record : consumerRecords) {
                // Extract headers
                Map<String, String> headers = new HashMap<>();
                if (record.headers() != null) {
                    record.headers().forEach(header -> {
                        headers.put(header.key(), new String(header.value()));
                    });
                }
                
                messages.add(Message.builder()
                        .partition(record.partition())
                        .offset(record.offset())
                        .timestamp(record.timestamp())
                        .key(record.key())
                        .value(record.value())
                        .headers(headers)
                        .build());

                recordsRead++;
                if (recordsRead >= limit) {
                    break;
                }
            }
        }

        log.info("Fetched {} messages from topic {} in cluster {} using {} polls",
                recordsRead, topic, clusterName, pollCount);

        return messages;
    }
    
    /**
     * Streams messages for replay: Consumes and processes messages in batches without loading all into memory.
     * CRITICAL: This method is optimized for enterprise-scale operations to prevent OutOfMemoryError.
     * 
     * @param cluster The Kafka cluster
     * @param topic The source topic
     * @param partitions List of partition IDs (null = all partitions)
     * @param startOffset Starting offset (null if using timestamp)
     * @param startTimestamp Starting timestamp (null if using offset)
     * @param endOffset Ending offset (null for no end limit)
     * @param endTimestamp Ending timestamp (null for no end limit)
     * @param filter Message filter (null = no filtering)
     * @param maxMessages Maximum messages to consume (safety limit)
     * @param batchSize Batch size for processing (default: 10000)
     * @param messageProcessor Callback to process each batch of messages (returns true to continue, false to stop)
     * @param progressCallback Callback for progress updates (null = no updates)
     * @return Total number of messages processed
     */
    public long streamMessagesForReplay(
            KafkaCluster cluster,
            String topic,
            List<Integer> partitions,
            Long startOffset,
            java.time.LocalDateTime startTimestamp,
            Long endOffset,
            java.time.LocalDateTime endTimestamp,
            io.brokr.core.model.MessageFilter filter,
            long maxMessages,
            int batchSize,
            java.util.function.Function<List<Message>, Boolean> messageProcessor,
            java.util.function.Consumer<ReplayProgress> progressCallback) {
        
        // Delegate to overloaded method with null jobId for backward compatibility
        return streamMessagesForReplay(cluster, topic, partitions, startOffset, startTimestamp,
                endOffset, endTimestamp, filter, maxMessages, batchSize, messageProcessor, progressCallback, null);
    }
    
    /**
     * Streams messages for replay with job ID for unique consumer group.
     */
    public long streamMessagesForReplay(
            KafkaCluster cluster,
            String topic,
            List<Integer> partitions,
            Long startOffset,
            java.time.LocalDateTime startTimestamp,
            Long endOffset,
            java.time.LocalDateTime endTimestamp,
            io.brokr.core.model.MessageFilter filter,
            long maxMessages,
            int batchSize,
            java.util.function.Function<List<Message>, Boolean> messageProcessor,
            java.util.function.Consumer<ReplayProgress> progressCallback,
            String jobId) {
        
        Properties props = buildConsumerPropertiesForReplay(cluster, batchSize, jobId);
        
        long messagesProcessed = 0;
        long messagesMatched = 0;
        int emptyPollCount = 0;
        List<Message> batch = new ArrayList<>(batchSize);
        boolean shouldContinue = true;
        
        // Track end offsets for each partition to detect when we've reached the end
        Map<org.apache.kafka.common.TopicPartition, Long> endOffsets = null;
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<org.apache.kafka.common.TopicPartition> topicPartitions;
            
            if (partitions != null && !partitions.isEmpty()) {
                // Specific partitions requested - use them directly
                topicPartitions = partitions.stream()
                        .map(p -> new org.apache.kafka.common.TopicPartition(topic, p))
                        .collect(Collectors.toList());
            } else {
                // All partitions - get partition count from AdminClient to avoid consumer group coordination delay
                try {
                    org.apache.kafka.clients.admin.AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
                    org.apache.kafka.clients.admin.DescribeTopicsResult describeResult = adminClient.describeTopics(Collections.singletonList(topic));
                    org.apache.kafka.clients.admin.TopicDescription topicDescription = describeResult.allTopicNames().get().get(topic);
                    
                    topicPartitions = topicDescription.partitions().stream()
                            .map(p -> new org.apache.kafka.common.TopicPartition(topic, p.partition()))
                            .collect(Collectors.toList());
                    
                    log.debug("Found {} partitions for topic {} using AdminClient", topicPartitions.size(), topic);
                } catch (Exception e) {
                    log.error("Failed to get partitions for topic {} using AdminClient: {}", topic, e.getMessage(), e);
                    // Fallback: try using consumer subscription (slower but more reliable)
                    consumer.subscribe(Collections.singletonList(topic));
                    // Poll multiple times until we get assignment (with timeout)
                    int maxPolls = 10;
                    int pollCount = 0;
                    while (consumer.assignment().isEmpty() && pollCount < maxPolls) {
                        consumer.poll(Duration.ofMillis(500));
                        pollCount++;
                    }
                    topicPartitions = new ArrayList<>(consumer.assignment());
                    if (topicPartitions.isEmpty()) {
                        log.warn("No partitions found for topic {} in cluster {} after {} polls", topic, cluster.getName(), pollCount);
                        return 0;
                    }
                    log.debug("Found {} partitions for topic {} using consumer subscription (fallback)", topicPartitions.size(), topic);
                }
            }
            
            if (topicPartitions.isEmpty()) {
                log.warn("No partitions found for topic {} in cluster {}", topic, cluster.getName());
                return 0;
            }
            
            // Manually assign partitions (avoids consumer group coordination)
            consumer.unsubscribe(); // CRITICAL: Clear any previous subscription (from fallback above) before manual assignment
            consumer.assign(topicPartitions);
            
            // Get end offsets for all partitions to detect when we've reached the end
            // This is critical for "no end limit" scenarios
            // Only get end offsets if no specific endOffset or endTimestamp is specified
            if (endOffset == null && endTimestamp == null) {
                endOffsets = consumer.endOffsets(topicPartitions);
                log.debug("End offsets for topic {}: {}", topic, endOffsets);
            } else {
                log.debug("Using user-specified end condition (endOffset={}, endTimestamp={})", endOffset, endTimestamp);
            }
            
            // Convert timestamp to offset if needed
            if (startTimestamp != null) {
                Map<org.apache.kafka.common.TopicPartition, Long> offsets = convertTimestampToOffsets(cluster, topic, topicPartitions, startTimestamp);
                for (Map.Entry<org.apache.kafka.common.TopicPartition, Long> entry : offsets.entrySet()) {
                    consumer.seek(entry.getKey(), entry.getValue());
                }
            } else if (startOffset != null) {
                for (org.apache.kafka.common.TopicPartition tp : topicPartitions) {
                    consumer.seek(tp, startOffset);
                    log.debug("Seeking partition {} to offset {}", tp.partition(), startOffset);
                }
            } else {
                consumer.seekToBeginning(topicPartitions);
                log.debug("Seeking all partitions to beginning");
            }
            
            
            // Stream messages in batches
            while (messagesMatched < maxMessages && shouldContinue) {
                ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS));
                
                if (consumerRecords.isEmpty()) {
                    // Check if we've reached the end based on user-specified endOffset or partition end
                    boolean shouldStop = false;
                    
                    if (endOffset != null) {
                        // Check if all partitions have reached the user-specified end offset
                        boolean allPartitionsReachedEndOffset = true;
                        for (org.apache.kafka.common.TopicPartition tp : topicPartitions) {
                            long currentPosition = consumer.position(tp);
                            if (currentPosition <= endOffset) {
                                allPartitionsReachedEndOffset = false;
                                break;
                            }
                        }
                        if (allPartitionsReachedEndOffset) {
                            log.debug("All partitions have reached end offset {} for topic {}", endOffset, topic);
                            shouldStop = true;
                        }
                    } else if (endOffsets != null) {
                        // Check if we've reached the end of all partitions (no end limit specified)
                        // endOffset is the next offset to be written, so currentPosition >= endOffset means we've consumed all messages
                        boolean allPartitionsReachedEnd = true;
                        for (org.apache.kafka.common.TopicPartition tp : topicPartitions) {
                            long currentPosition = consumer.position(tp);
                            Long partitionEndOffset = endOffsets.get(tp);
                            if (partitionEndOffset != null && currentPosition < partitionEndOffset) {
                                allPartitionsReachedEnd = false;
                                break;
                            }
                        }
                        if (allPartitionsReachedEnd) {
                            log.debug("Reached end of all partitions for topic {}", topic);
                            shouldStop = true;
                        }
                    }
                    
                    if (shouldStop) {
                        // Process remaining batch before returning
                        if (!batch.isEmpty()) {
                            shouldContinue = messageProcessor.apply(batch);
                            messagesMatched += batch.size();
                            batch.clear();
                        }
                        break;
                    }
                    
                    emptyPollCount++;
                    // Only stop if we've had too many empty polls AND we're not at the end
                    // This handles the case where there are no messages yet but more might come
                    if (emptyPollCount >= MAX_EMPTY_POLLS) {
                        log.debug("Stopping after {} empty polls for topic {}", emptyPollCount, topic);
                        // Process remaining batch before returning
                        if (!batch.isEmpty()) {
                            shouldContinue = messageProcessor.apply(batch);
                            messagesMatched += batch.size();
                            batch.clear();
                        }
                        break;
                    }
                    continue;
                }
                
                emptyPollCount = 0;
                
                for (ConsumerRecord<String, String> record : consumerRecords) {
                    org.apache.kafka.common.TopicPartition recordTp = 
                        new org.apache.kafka.common.TopicPartition(record.topic(), record.partition());
                    
                    // Check if we've reached the end of this partition (only when no user-specified endOffset)
                    // endOffset is the next offset to be written, so if record.offset() >= endOffset, we've passed the end
                    if (endOffsets != null && endOffset == null && endTimestamp == null) {
                        Long partitionEndOffset = endOffsets.get(recordTp);
                        if (partitionEndOffset != null && record.offset() >= partitionEndOffset) {
                            // We've passed the end of this partition, skip this record
                            // But continue processing other partitions
                            continue;
                        }
                    }
                    
                    // Check end conditions - process messages up to and INCLUDING endOffset
                    // If endOffset is 800, we process 0-800 (inclusive), stop when we see 801
                    if (endOffset != null && record.offset() > endOffset) {
                        log.debug("Reached end offset {} (current offset: {}), stopping partition {}", 
                                endOffset, record.offset(), record.partition());
                        // Don't process this record, but process remaining batch
                        // Break from record loop to check if all partitions are done
                        break;
                    }
                    if (endTimestamp != null) {
                        java.time.LocalDateTime recordTimestamp = java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(record.timestamp()),
                            java.time.ZoneId.systemDefault()
                        );
                        if (recordTimestamp.isAfter(endTimestamp)) {
                            // Process remaining batch before returning
                            if (!batch.isEmpty()) {
                                messageProcessor.apply(batch);
                                messagesMatched += batch.size();
                            }
                            return messagesMatched;
                        }
                    }
                    
                    // Extract headers
                    Map<String, String> headers = new HashMap<>();
                    if (record.headers() != null) {
                        record.headers().forEach(header -> {
                            headers.put(header.key(), new String(header.value()));
                        });
                    }
                    
                    Message message = Message.builder()
                            .partition(record.partition())
                            .offset(record.offset())
                            .timestamp(record.timestamp())
                            .key(record.key())
                            .value(record.value())
                            .headers(headers)
                            .build();
                    
                    messagesProcessed++;
                    
                    // Apply filter if provided
                    if (filter == null || messageFilterService.matches(message, filter)) {
                        batch.add(message);
                        messagesMatched++;
                        
                        // Process batch when it reaches batchSize
                        if (batch.size() >= batchSize) {
                            shouldContinue = messageProcessor.apply(batch);
                            batch.clear(); // Clear batch to free memory
                            
                            if (!shouldContinue) {
                                break;
                            }
                        }
                        
                        if (messagesMatched >= maxMessages) {
                            break;
                        }
                    }
                    
                    // Progress callback
                    if (progressCallback != null && messagesProcessed % 1000 == 0) {
                        progressCallback.accept(ReplayProgress.builder()
                                .messagesProcessed(messagesProcessed)
                                .messagesMatched(messagesMatched)
                                .build());
                    }
                }
                
                // If we broke from the record loop due to endOffset, check if we should continue
                // For multi-partition scenarios, we might need to continue processing other partitions
                if (endOffset != null) {
                    // Check if all partitions have reached the end offset
                    boolean allPartitionsDone = true;
                    for (org.apache.kafka.common.TopicPartition tp : topicPartitions) {
                        long currentPosition = consumer.position(tp);
                        // If current position is still <= endOffset, we haven't finished this partition
                        if (currentPosition <= endOffset) {
                            allPartitionsDone = false;
                            break;
                        }
                    }
                    if (allPartitionsDone) {
                        log.debug("All partitions have reached end offset {}", endOffset);
                        // Process remaining batch before returning
                        if (!batch.isEmpty() && shouldContinue) {
                            shouldContinue = messageProcessor.apply(batch);
                            messagesMatched += batch.size();
                            batch.clear();
                        }
                        break; // Exit the main while loop
                    }
                }
            }
            
            // Process remaining batch
            if (!batch.isEmpty() && shouldContinue) {
                shouldContinue = messageProcessor.apply(batch);
                messagesMatched += batch.size();
            }
            
        } catch (Exception e) {
            log.error("Failed to stream messages for replay from topic {} in cluster {}: {}",
                    topic, cluster.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to stream messages for replay: " + e.getMessage(), e);
        }
        
        return messagesMatched;
    }
    
    /**
     * Converts timestamp to offsets for all partitions using AdminClient.
     */
    private Map<org.apache.kafka.common.TopicPartition, Long> convertTimestampToOffsets(
            KafkaCluster cluster,
            String topic,
            List<org.apache.kafka.common.TopicPartition> topicPartitions,
            java.time.LocalDateTime timestamp) {
        
        try {
            org.apache.kafka.clients.admin.AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            
            // Convert LocalDateTime to epoch milliseconds
            long timestampMs = timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            // Build offset specs for all partitions
            Map<org.apache.kafka.common.TopicPartition, org.apache.kafka.clients.admin.OffsetSpec> offsetSpecs = new HashMap<>();
            for (org.apache.kafka.common.TopicPartition kafkaTp : topicPartitions) {
                offsetSpecs.put(kafkaTp, org.apache.kafka.clients.admin.OffsetSpec.forTimestamp(timestampMs));
            }
            
            // Get offsets
            org.apache.kafka.clients.admin.ListOffsetsResult result = adminClient.listOffsets(offsetSpecs);
            Map<org.apache.kafka.common.TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> offsets = result.all().get();
            
            // Return map of Kafka TopicPartition to offset
            Map<org.apache.kafka.common.TopicPartition, Long> resultMap = new HashMap<>();
            for (org.apache.kafka.common.TopicPartition kafkaTp : topicPartitions) {
                org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo info = offsets.get(kafkaTp);
                if (info != null) {
                    resultMap.put(kafkaTp, info.offset());
                } else {
                    log.warn("Could not get offset for partition {}:{} at timestamp {}", topic, kafkaTp.partition(), timestamp);
                    resultMap.put(kafkaTp, 0L);  // Default to beginning
                }
            }
            
            return resultMap;
        } catch (Exception e) {
            log.error("Failed to convert timestamp to offsets for topic {}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to convert timestamp to offsets", e);
        }
    }
    
    /**
     * Progress tracking for replay operations
     */
    @lombok.Data
    @lombok.Builder
    public static class ReplayProgress {
        private long messagesProcessed;
        private long messagesMatched;
        private double throughput;  // Messages per second
    }
    
}