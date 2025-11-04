package io.brokr.kafka.service;

import io.brokr.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminService {

    private final KafkaConnectionService kafkaConnectionService;

    public List<Topic> listTopics(KafkaCluster cluster) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {
            ListTopicsResult topicsResult = adminClient.listTopics();
            Set<String> topicNames = topicsResult.names().get();

            DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(topicNames);
            Map<String, TopicDescription> topicDescriptions = describeTopicsResult.allTopicNames().get();

            // Get topic configs
            List<ConfigResource> configResources = topicNames.stream()
                    .map(topicName -> new ConfigResource(ConfigResource.Type.TOPIC, topicName))
                    .collect(Collectors.toList());

            DescribeConfigsResult describeConfigsResult = adminClient.describeConfigs(configResources);
            Map<ConfigResource, Config> configs = describeConfigsResult.all().get();

            return topicDescriptions.entrySet().stream()
                    .map(entry -> {
                        String topicName = entry.getKey();
                        TopicDescription description = entry.getValue();

                        Map<String, String> topicConfigs = configs.get(
                                        new ConfigResource(ConfigResource.Type.TOPIC, topicName)
                                ).entries().stream()
                                .collect(Collectors.toMap(
                                        ConfigEntry::name,
                                        ConfigEntry::value
                                ));

                        return Topic.builder()
                                .name(topicName)
                                .partitions(description.partitions().size())
                                .replicationFactor(description.partitions().get(0).replicas().size())
                                .isInternal(description.isInternal())
                                .configs(topicConfigs)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to list topics for cluster: {}", cluster.getName(), e);
            throw new RuntimeException("Failed to list topics", e);
        }
    }

    public Topic createTopic(KafkaCluster cluster, String topicName, int partitions, int replicationFactor, Map<String, String> configs) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {
            NewTopic newTopic = new NewTopic(topicName, partitions, (short) replicationFactor);
            if (configs != null && !configs.isEmpty()) {
                newTopic.configs(configs);
            }

            CreateTopicsResult result = adminClient.createTopics(Collections.singleton(newTopic));
            result.all().get();

            return getTopic(cluster, topicName);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create topic: {} for cluster: {}", topicName, cluster.getName(), e);
            throw new RuntimeException("Failed to create topic", e);
        }
    }

    public Topic getTopic(KafkaCluster cluster, String topicName) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {
            DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(Collections.singleton(topicName));
            TopicDescription description = describeTopicsResult.allTopicNames().get().get(topicName);

            // Get topic configs
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            DescribeConfigsResult describeConfigsResult = adminClient.describeConfigs(Collections.singleton(configResource));
            Config config = describeConfigsResult.all().get().get(configResource);

            Map<String, String> topicConfigs = config.entries().stream()
                    .collect(Collectors.toMap(
                            ConfigEntry::name,
                            ConfigEntry::value
                    ));

            return Topic.builder()
                    .name(topicName)
                    .partitions(description.partitions().size())
                    .replicationFactor(description.partitions().get(0).replicas().size())
                    .isInternal(description.isInternal())
                    .configs(topicConfigs)
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get topic: {} for cluster: {}", topicName, cluster.getName(), e);
            throw new RuntimeException("Failed to get topic", e);
        }
    }

    // <<< FIX: Implemented updateTopicConfig >>>
    public void updateTopicConfig(KafkaCluster cluster, String topicName, Map<String, String> configs) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

            // Create a list of config entries to alter
            List<AlterConfigOp> ops = configs.entrySet().stream()
                    .map(entry -> new AlterConfigOp(new ConfigEntry(entry.getKey(), entry.getValue()), AlterConfigOp.OpType.SET))
                    .toList();

            // Alter the configs
            AlterConfigsResult result = adminClient.incrementalAlterConfigs(Collections.singletonMap(resource, ops));
            result.all().get();

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to update topic config: {} for cluster: {}", topicName, cluster.getName(), e);
            throw new RuntimeException("Failed to update topic config", e);
        }
    }

    public void deleteTopic(KafkaCluster cluster, String topicName) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {
            DeleteTopicsResult result = adminClient.deleteTopics(Collections.singleton(topicName));
            result.all().get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete topic: {} for cluster: {}", topicName, cluster.getName(), e);
            throw new RuntimeException("Failed to delete topic", e);
        }
    }

    public List<ConsumerGroup> listConsumerGroups(KafkaCluster cluster) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {
            // Use the new listGroups() method
            ListGroupsResult result = adminClient.listGroups();
            Collection<GroupListing> groupListings = result.all().get(); // Returns Collection<GroupListing>

            List<String> groupIds = groupListings.stream()
                    .map(GroupListing::groupId) // Use GroupListing here
                    .collect(Collectors.toList());

            DescribeConsumerGroupsResult describeResult = adminClient.describeConsumerGroups(groupIds);
            Map<String, ConsumerGroupDescription> groupDescriptions = describeResult.all().get();

            return groupDescriptions.entrySet().stream()
                    .map(entry -> {
                        String groupId = entry.getKey();
                        ConsumerGroupDescription description = entry.getValue();

                        List<MemberInfo> members = description.members().stream()
                                .map(member -> MemberInfo.builder()
                                        .memberId(member.consumerId())
                                        .clientId(member.clientId())
                                        .host(member.host())
                                        .assignment(member.assignment().topicPartitions().stream()
                                                .map(tp -> TopicPartition.builder()
                                                        .topic(tp.topic())
                                                        .partition(tp.partition())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .build())
                                .collect(Collectors.toList());

                        return ConsumerGroup.builder()
                                .groupId(groupId)
                                .state(description.state().toString())
                                .members(members)
                                .coordinator(description.coordinator().host())
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to list consumer groups for cluster: {}", cluster.getName(), e);
            throw new RuntimeException("Failed to list consumer groups", e);
        }
    }

    public Optional<ConsumerGroup> getConsumerGroup(KafkaCluster cluster, String groupId) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {

            DescribeConsumerGroupsResult describeResult = adminClient.describeConsumerGroups(Collections.singleton(groupId));
            Map<String, ConsumerGroupDescription> groupDescriptions = describeResult.all().get();

            if (!groupDescriptions.containsKey(groupId)) {
                return Optional.empty();
            }

            ConsumerGroupDescription description = groupDescriptions.get(groupId);

            List<MemberInfo> members = description.members().stream()
                    .map(member -> MemberInfo.builder()
                            .memberId(member.consumerId())
                            .clientId(member.clientId())
                            .host(member.host())
                            .assignment(member.assignment().topicPartitions().stream()
                                    .map(tp -> TopicPartition.builder()
                                            .topic(tp.topic())
                                            .partition(tp.partition())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());

            ConsumerGroup group = ConsumerGroup.builder()
                    .groupId(groupId)
                    .state(description.state().toString())
                    .members(members)
                    .coordinator(description.coordinator().host())
                    .build();

            return Optional.of(group);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get consumer group [{}]: {}", groupId, e.getMessage());
            // If group doesn't exist, Kafka throws an error. We treat this as "not found".
            return Optional.empty();
        }
    }

    public Map<String, Long> getConsumerGroupOffsets(KafkaCluster cluster, String groupId) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());

        if (cluster.getSecurityProtocol() != null) {
            props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, cluster.getSecurityProtocol().name());

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


        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster);
             KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                     props,
                     new StringDeserializer(),
                     new StringDeserializer())) {

            // Get consumer group offsets
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();

            // Get end offsets for comparison
            Set<org.apache.kafka.common.TopicPartition> topicPartitions = offsets.keySet();
            Map<org.apache.kafka.common.TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);

            Map<String, Long> lagByTopic = new HashMap<>();

            for (Map.Entry<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                org.apache.kafka.common.TopicPartition tp = entry.getKey();
                long currentOffset = entry.getValue().offset();
                long endOffset = endOffsets.get(tp);
                long lag = endOffset - currentOffset;

                lagByTopic.merge(tp.topic(), lag, Long::sum);
            }

            return lagByTopic;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get consumer group offsets: {} for cluster: {}", groupId, cluster.getName(), e);
            throw new RuntimeException("Failed to get consumer group offsets", e);
        }
    }

    public boolean resetConsumerGroupOffset(KafkaCluster cluster, String groupId, String topic, int partition, long offset) {
        try (AdminClient adminClient = kafkaConnectionService.createAdminClient(cluster)) {

            org.apache.kafka.common.TopicPartition topicPartition = new org.apache.kafka.common.TopicPartition(topic, partition);
            OffsetAndMetadata offsetAndMetadata = new OffsetAndMetadata(offset);

            Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsetsToReset = new HashMap<>();
            offsetsToReset.put(topicPartition, offsetAndMetadata);

            AlterConsumerGroupOffsetsResult result = adminClient.alterConsumerGroupOffsets(groupId, offsetsToReset);
            result.all().get();
            return true;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to reset offset for group [{}], topic [{}], partition [{}]: {}", groupId, topic, partition, e.getMessage(), e);
            throw new RuntimeException("Failed to reset consumer group offset", e);
        }
    }
}