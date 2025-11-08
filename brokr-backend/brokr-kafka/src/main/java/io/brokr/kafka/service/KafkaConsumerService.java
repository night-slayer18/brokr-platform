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
                messages.add(Message.builder()
                        .partition(record.partition())
                        .offset(record.offset())
                        .timestamp(record.timestamp())
                        .key(record.key())
                        .value(record.value())
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
}