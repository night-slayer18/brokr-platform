package io.brokr.kafka.service;

import io.brokr.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.config.ConfigResource;
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

            // The rest of your method remains the same
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

    public Map<String, Long> getConsumerGroupOffsets(KafkaCluster cluster, String groupId) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        if (cluster.getProperties() != null) {
            props.putAll(cluster.getProperties());
        }

        try (AdminClient adminClient = AdminClient.create(props);
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
}