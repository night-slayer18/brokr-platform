package io.brokr.kafka.service;

import io.brokr.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminService {

    private final KafkaConnectionService kafkaConnectionService;

    public List<Topic> listTopics(KafkaCluster cluster) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
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
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to list topics", e);
        }
    }

    public Topic createTopic(KafkaCluster cluster, String topicName, int partitions, int replicationFactor, Map<String, String> configs) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            NewTopic newTopic = new NewTopic(topicName, partitions, (short) replicationFactor);
            if (configs != null && !configs.isEmpty()) {
                newTopic.configs(configs);
            }

            CreateTopicsResult result = adminClient.createTopics(Collections.singleton(newTopic));
            result.all().get();

            return getTopic(cluster, topicName);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create topic: {} for cluster: {}", topicName, cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to create topic", e);
        }
    }

    public Topic getTopic(KafkaCluster cluster, String topicName) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            // Kick off all async operations in parallel for better performance
            DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(Collections.singleton(topicName));

            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            DescribeConfigsResult describeConfigsResult = adminClient.describeConfigs(Collections.singleton(configResource));

            // Wait for topic description first (we need it to get partition info)
            TopicDescription description = describeTopicsResult.allTopicNames().get().get(topicName);

            // Prepare offset specs for all partitions - earliest offsets
            Map<org.apache.kafka.common.TopicPartition, OffsetSpec> earliestOffsetSpecs = new HashMap<>();
            for (var partition : description.partitions()) {
                org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topicName, partition.partition());
                earliestOffsetSpecs.put(tp, OffsetSpec.earliest());
            }

            // Prepare offset specs for all partitions - latest offsets
            Map<org.apache.kafka.common.TopicPartition, OffsetSpec> latestOffsetSpecs = new HashMap<>();
            for (var partition : description.partitions()) {
                org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topicName, partition.partition());
                latestOffsetSpecs.put(tp, OffsetSpec.latest());
            }

            // Launch both offset queries in parallel (don't wait yet!)
            ListOffsetsResult earliestOffsetsResult = adminClient.listOffsets(earliestOffsetSpecs);
            ListOffsetsResult latestOffsetsResult = adminClient.listOffsets(latestOffsetSpecs);

            // Now wait for all three async operations to complete in parallel
            // This is much faster than sequential waits!
            Config config = describeConfigsResult.all().get().get(configResource);
            Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliestOffsets =
                    earliestOffsetsResult.all().get();
            Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    latestOffsetsResult.all().get();

            // Process configs
            Map<String, String> topicConfigs = config.entries().stream()
                    .collect(Collectors.toMap(
                            ConfigEntry::name,
                            ConfigEntry::value
                    ));

            // Build partition info list
            List<io.brokr.core.model.PartitionInfo> partitionsInfo = new ArrayList<>();
            for (var partition : description.partitions()) {
                org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topicName, partition.partition());
                ListOffsetsResult.ListOffsetsResultInfo earliestInfo = earliestOffsets.get(tp);
                ListOffsetsResult.ListOffsetsResultInfo latestInfo = latestOffsets.get(tp);
                if (earliestInfo == null || latestInfo == null) {
                    log.warn("Missing offset info for partition {}:{}", topicName, partition.partition());
                    continue;
                }
                long earliestOffset = earliestInfo.offset();
                long latestOffset = latestInfo.offset();
                long size = latestOffset - earliestOffset;

                partitionsInfo.add(io.brokr.core.model.PartitionInfo.builder()
                        .id(partition.partition())
                        .leader(partition.leader().id())
                        .replicas(partition.replicas().stream().map(org.apache.kafka.common.Node::id).collect(Collectors.toList()))
                        .isr(partition.isr().stream().map(org.apache.kafka.common.Node::id).collect(Collectors.toList()))
                        .earliestOffset(earliestOffset)
                        .latestOffset(latestOffset)
                        .size(size)
                        .build());
            }

            return Topic.builder()
                    .name(topicName)
                    .partitions(description.partitions().size())
                    .replicationFactor(description.partitions().get(0).replicas().size())
                    .isInternal(description.isInternal())
                    .configs(topicConfigs)
                    .partitionsInfo(partitionsInfo)
                    .build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get topic: {} for cluster: {}", topicName, cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to get topic", e);
        }
    }

    // <<< FIX: Implemented updateTopicConfig >>>
    public void updateTopicConfig(KafkaCluster cluster, String topicName, Map<String, String> configs) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
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
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to update topic config", e);
        }
    }

    public void deleteTopic(KafkaCluster cluster, String topicName) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            DeleteTopicsResult result = adminClient.deleteTopics(Collections.singleton(topicName));
            result.all().get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete topic: {} for cluster: {}", topicName, cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to delete topic", e);
        }
    }

    public List<BrokerNode> getClusterNodes(KafkaCluster cluster) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            DescribeClusterResult result = adminClient.describeCluster();
            return result.nodes().get().stream()
                    .map(node -> BrokerNode.builder()
                            .id(node.id())
                            .host(node.host())
                            .port(node.port())
                            .rack(node.rack())
                            .build())
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to describe cluster: {}", cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to describe cluster", e);
        }
    }

    public List<ConsumerGroup> listConsumerGroups(KafkaCluster cluster) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
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
                                .state(description.groupState() != null ? description.groupState().name() : "Unknown")
                                .members(members)
                                .coordinator(description.coordinator().host())
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to list consumer groups for cluster: {}", cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to list consumer groups", e);
        }
    }

    public Optional<ConsumerGroup> getConsumerGroup(KafkaCluster cluster, String groupId) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);

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
                    .state(description.groupState() != null ? description.groupState().name() : "Unknown")
                    .members(members)
                    .coordinator(description.coordinator().host())
                    .build();

            return Optional.of(group);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get consumer group [{}]: {}", groupId, e.getMessage());
            kafkaConnectionService.removeAdminClient(cluster.getId());
            // If group doesn't exist, Kafka throws an error. We treat this as "not found".
            return Optional.empty();
        }
    }

    /**
     * Gets consumer group offsets for a single group.
     * For multiple groups, use getConsumerGroupOffsetsBatch for better performance.
     */
    public Map<String, Long> getConsumerGroupOffsets(KafkaCluster cluster, String groupId) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);

            // Get consumer group offsets
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();

            // Get end offsets using AdminClient (no need for KafkaConsumer)
            Set<org.apache.kafka.common.TopicPartition> topicPartitions = offsets.keySet();
            Map<org.apache.kafka.common.TopicPartition, OffsetSpec> latestOffsetSpecs = new HashMap<>();
            for (org.apache.kafka.common.TopicPartition tp : topicPartitions) {
                latestOffsetSpecs.put(tp, OffsetSpec.latest());
            }

            ListOffsetsResult latestOffsetsResult = adminClient.listOffsets(latestOffsetSpecs);
            Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    latestOffsetsResult.all().get();

            Map<String, Long> lagByTopic = new HashMap<>();

            for (Map.Entry<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                org.apache.kafka.common.TopicPartition tp = entry.getKey();
                long currentOffset = entry.getValue().offset();
                ListOffsetsResult.ListOffsetsResultInfo latestInfo = latestOffsets.get(tp);
                if (latestInfo == null) {
                    log.warn("Missing latest offset info for partition {}:{}", tp.topic(), tp.partition());
                    continue;
                }
                long endOffset = latestInfo.offset();
                long lag = endOffset - currentOffset;

                lagByTopic.merge(tp.topic(), lag, Long::sum);
            }

            return lagByTopic;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get consumer group offsets: {} for cluster: {}", groupId, cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to get consumer group offsets", e);
        }
    }

    /**
     * Batch method to get offsets for multiple consumer groups efficiently.
     * This reduces network calls by batching the listOffsets call for all partitions.
     *
     * @param cluster The Kafka cluster
     * @param groupIds List of consumer group IDs
     * @return Map of groupId -> topic -> lag
     */
    public Map<String, Map<String, Long>> getConsumerGroupOffsetsBatch(KafkaCluster cluster, List<String> groupIds) {
        if (groupIds.isEmpty()) {
            return new HashMap<>();
        }

        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);

            // Step 1: Get offsets for all groups in parallel
            List<CompletableFuture<Map.Entry<String, Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata>>>> groupOffsetFutures =
                    groupIds.stream()
                            .<CompletableFuture<Map.Entry<String, Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata>>>>map(groupId -> 
                                CompletableFuture.supplyAsync(() -> {
                                    try {
                                        ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
                                        Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsets =
                                                offsetsResult.partitionsToOffsetAndMetadata().get();
                                        return Map.entry(groupId, offsets);
                                    } catch (InterruptedException | ExecutionException e) {
                                        log.warn("Failed to get offsets for group {}: {}", groupId, e.getMessage());
                                        return Map.entry(groupId, new HashMap<>());
                                    }
                                }))
                            .collect(Collectors.toList());

            // Wait for all group offset requests to complete
            CompletableFuture<List<Map.Entry<String, Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata>>>> allGroupOffsetsFuture =
                    CompletableFuture.allOf(groupOffsetFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> groupOffsetFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));

            List<Map.Entry<String, Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata>>> allGroupOffsets =
                    allGroupOffsetsFuture.get();

            // Step 2: Collect all unique topic partitions from all groups
            Set<org.apache.kafka.common.TopicPartition> allTopicPartitions = new HashSet<>();
            for (Map.Entry<String, Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata>> entry : allGroupOffsets) {
                allTopicPartitions.addAll(entry.getValue().keySet());
            }

            // Step 3: Get end offsets for ALL partitions in ONE batch call
            Map<org.apache.kafka.common.TopicPartition, OffsetSpec> latestOffsetSpecs = new HashMap<>();
            for (org.apache.kafka.common.TopicPartition tp : allTopicPartitions) {
                latestOffsetSpecs.put(tp, OffsetSpec.latest());
            }

            ListOffsetsResult latestOffsetsResult = adminClient.listOffsets(latestOffsetSpecs);
            Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    latestOffsetsResult.all().get();

            // Step 4: Calculate lag for each group
            Map<String, Map<String, Long>> result = new HashMap<>();
            for (Map.Entry<String, Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata>> groupEntry : allGroupOffsets) {
                String groupId = groupEntry.getKey();
                Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsets = groupEntry.getValue();

                Map<String, Long> lagByTopic = new HashMap<>();
                for (Map.Entry<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsetEntry : offsets.entrySet()) {
                    org.apache.kafka.common.TopicPartition tp = offsetEntry.getKey();
                    long currentOffset = offsetEntry.getValue().offset();
                    ListOffsetsResult.ListOffsetsResultInfo latestInfo = latestOffsets.get(tp);
                    if (latestInfo != null) {
                        long endOffset = latestInfo.offset();
                        long lag = endOffset - currentOffset;
                        lagByTopic.merge(tp.topic(), lag, Long::sum);
                    }
                }
                result.put(groupId, lagByTopic);
            }

            return result;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get consumer group offsets batch for cluster: {}", cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to get consumer group offsets batch", e);
        }
    }

    public boolean resetConsumerGroupOffset(KafkaCluster cluster, String groupId, String topic, int partition, long offset) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);

            org.apache.kafka.common.TopicPartition topicPartition = new org.apache.kafka.common.TopicPartition(topic, partition);
            OffsetAndMetadata offsetAndMetadata = new OffsetAndMetadata(offset);

            Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsetsToReset = new HashMap<>();
            offsetsToReset.put(topicPartition, offsetAndMetadata);

            AlterConsumerGroupOffsetsResult result = adminClient.alterConsumerGroupOffsets(groupId, offsetsToReset);
            result.all().get();
            return true;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to reset offset for group [{}], topic [{}], partition [{}]: {}", groupId, topic, partition, e.getMessage(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to reset consumer group offset", e);
        }
    }
}