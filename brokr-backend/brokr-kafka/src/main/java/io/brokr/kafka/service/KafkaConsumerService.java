package io.brokr.kafka.service;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Message;
import io.brokr.core.model.SecurityProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.*;
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

    public List<Message> consumeMessages(KafkaCluster cluster, String topic, List<Integer> partitions, Long offset, Integer limit) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "brokr-consumer-" + UUID.randomUUID()); // Unique group ID
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Start from earliest if no offset is provided
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // We manually control offsets

        // Apply security configuration
        if (cluster.getSecurityProtocol() != null) {
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
        }

        // Add any additional properties
        if (cluster.getProperties() != null) {
            props.putAll(cluster.getProperties());
        }

        List<Message> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> topicPartitions;

            if (partitions != null && !partitions.isEmpty()) {
                topicPartitions = partitions.stream()
                        .map(p -> new TopicPartition(topic, p))
                        .collect(Collectors.toList());
            } else {
                // If no partitions specified, get all partitions for the topic
                consumer.subscribe(Collections.singletonList(topic));
                consumer.poll(Duration.ofMillis(100)); // Ensure assignment
                topicPartitions = new ArrayList<>(consumer.assignment());
            }

            if (topicPartitions.isEmpty()) {
                log.warn("No partitions found for topic {} in cluster {}", topic, cluster.getName());
                return Collections.emptyList();
            }

            consumer.assign(topicPartitions);

            // Seek to specific offset if provided, otherwise to earliest
            if (offset != null) {
                for (TopicPartition tp : topicPartitions) {
                    consumer.seek(tp, offset);
                }
            } else {
                consumer.seekToBeginning(topicPartitions);
            }

            int recordsRead = 0;
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5 seconds timeout for polling

            while (recordsRead < limit && (System.currentTimeMillis() - startTime) < timeout) {
                ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(100));
                if (consumerRecords.isEmpty()) {
                    // If no records are returned, and we've already sought, we might be at the end
                    // or there are no new records. Break to avoid busy-waiting.
                    if (offset != null) break;
                    continue;
                }

                for (ConsumerRecord<String, String> record : consumerRecords) {
                    messages.add(Message.builder()
                            .partition(record.partition())
                            .offset(record.offset())
                            .timestamp(record.timestamp())
                            .key(record.key())
                            .value(record.value())
                            .build());
                    recordsRead++;
                    if (recordsRead >= limit) break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to consume messages from topic {} in cluster {}: {}", topic, cluster.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to consume messages: " + e.getMessage());
        }
        return messages;
    }
}
