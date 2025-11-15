package io.brokr.kafka.service;

import io.brokr.core.model.PartitionInfo;
import io.brokr.core.model.TopicPartition;
import io.brokr.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
    private final TopicMetricsService topicMetricsService;
    private final TopicMetricsCache topicMetricsCache;
    private final ConsumerGroupMetricsService consumerGroupMetricsService;

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
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

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
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
            // Check if the error is a permanent error (like topic already exists)
            // These should NOT be retried - handle them immediately
            Throwable cause = e.getCause();
            if (cause instanceof TopicExistsException) {
                log.warn("Topic {} already exists in cluster: {} - returning existing topic", topicName, cluster.getName());
                // If topic exists, return it instead of throwing error (idempotent behavior)
                try {
                    return getTopic(cluster, topicName);
                } catch (Exception ex) {
                    log.error("Failed to get existing topic: {} for cluster: {}", topicName, cluster.getName(), ex);
                    throw new RuntimeException("Topic already exists but failed to retrieve it", e);
                }
            }
            
            // Check for other permanent errors that shouldn't be retried
            if (cause instanceof InvalidTopicException || 
                cause instanceof InvalidRequestException ||
                cause instanceof InvalidConfigurationException) {
                log.error("Invalid request for topic: {} in cluster: {} - {}", topicName, cluster.getName(), cause.getMessage());
                kafkaConnectionService.removeAdminClient(cluster.getId());
                // Throw a RuntimeException that won't be retried (not in retryable list)
                throw new RuntimeException("Invalid topic creation request: " + cause.getMessage(), e);
            }
            
            // For transient errors (network issues, timeouts, etc.), let retry logic handle it
            // These will be retried up to 3 times with exponential backoff
            log.error("Transient error creating topic: {} for cluster: {} - will retry", topicName, cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to create topic", e);
        }
    }

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
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
            List<PartitionInfo> partitionsInfo = new ArrayList<>();
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

                partitionsInfo.add(PartitionInfo.builder()
                        .id(partition.partition())
                        .leader(partition.leader().id())
                        .replicas(partition.replicas().stream().map(Node::id).collect(Collectors.toList()))
                        .isr(partition.isr().stream().map(Node::id).collect(Collectors.toList()))
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

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
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

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
    public void deleteTopic(KafkaCluster cluster, String topicName) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            DeleteTopicsResult result = adminClient.deleteTopics(Collections.singleton(topicName));
            result.all().get();
            
            try {
                // Delete topic metrics from database
                topicMetricsService.deleteMetricsForTopic(cluster.getId(), topicName);
                
                // Remove topic from consumer group metrics' topicLags
                // This ensures old consumer group metrics don't reference the deleted topic
                consumerGroupMetricsService.removeTopicFromConsumerGroupMetrics(cluster.getId(), topicName);
                
                // Clear cached metrics (for throughput calculation)
                topicMetricsCache.remove(cluster.getId(), topicName);
                
                log.info("Successfully deleted topic and cleaned up all metrics for: {} in cluster: {}", topicName, cluster.getName());
            } catch (Exception e) {
                log.error("Failed to delete metrics for topic: {} in cluster: {}", topicName, cluster.getName(), e);
            }
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnknownTopicOrPartitionException) {
                log.warn("Topic {} does not exist in cluster: {} - considering it already deleted", topicName, cluster.getName());
                return;
            }
            
            log.error("Transient error deleting topic: {} for cluster: {} - will retry", topicName, cluster.getName(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to delete topic", e);
        }
    }

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
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

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
    public List<ConsumerGroup> listConsumerGroups(KafkaCluster cluster) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            
            // Step 1: List all groups
            ListGroupsResult listGroupsResult = adminClient.listGroups();
            Collection<GroupListing> groupListings = listGroupsResult.all().get();

            if (groupListings.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> allGroupIds = groupListings.stream()
                    .map(GroupListing::groupId)
                    .collect(Collectors.toList());

            // Step 2: Filter to only consumer groups by attempting to describe each one
            // Non-consumer groups (connect, schema-registry, etc.) will throw IllegalArgumentException
            Map<String, ConsumerGroupDescription> consumerGroupDescriptions = new HashMap<>();
            
            for (String groupId : allGroupIds) {
                try {
                    // Try to describe as a consumer group
                    DescribeConsumerGroupsResult describeResult = 
                            adminClient.describeConsumerGroups(Collections.singletonList(groupId));
                    Map<String, ConsumerGroupDescription> descriptions = describeResult.all().get();
                    consumerGroupDescriptions.putAll(descriptions);
                } catch (ExecutionException e) {
                    // Check if the underlying exception is IllegalArgumentException (non-consumer group)
                    Throwable cause = e.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        log.debug("Skipping non-consumer group: {} ({})", groupId, cause.getMessage());
                    } else {
                        // Re-throw if it's a different error
                        log.warn("Failed to describe group {}: {}", groupId, e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while describing consumer groups", e);
                }
            }

            if (consumerGroupDescriptions.isEmpty()) {
                return Collections.emptyList();
            }

            // Step 3: Convert to domain model
            return consumerGroupDescriptions.entrySet().stream()
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

    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
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
    
    /**
     * Resets consumer group offsets for multiple partitions of a topic.
     * Supports both offset-based and timestamp-based reset.
     * 
     * @param cluster The Kafka cluster
     * @param groupId The consumer group ID
     * @param topic The topic name
     * @param partitions List of partition IDs (null = all partitions for the topic)
     * @param offset The offset to reset to (null if using timestamp)
     * @param timestamp The timestamp to reset to (null if using offset)
     * @return true if successful
     */
    @Retryable(
            value = {ExecutionException.class, InterruptedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
    )
    public boolean resetConsumerGroupOffsets(
            KafkaCluster cluster,
            String groupId,
            String topic,
            List<Integer> partitions,
            Long offset,
            java.time.LocalDateTime timestamp) {
        try {
            AdminClient adminClient = kafkaConnectionService.getOrCreateAdminClient(cluster);
            
            // Get topic metadata to determine partitions if not specified
            DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singletonList(topic));
            TopicDescription topicDescription = topicsResult.allTopicNames().get().get(topic);
            
            List<Integer> partitionsToReset = partitions != null ? partitions : 
                    topicDescription.partitions().stream()
                            .map(org.apache.kafka.common.TopicPartitionInfo::partition)
                            .collect(Collectors.toList());
            
            Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsetsToReset = new HashMap<>();
            
            if (timestamp != null) {
                // Convert timestamp to offsets for each partition
                long timestampMs = timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                
                Map<org.apache.kafka.common.TopicPartition, OffsetSpec> offsetSpecs = new HashMap<>();
                for (Integer partition : partitionsToReset) {
                    org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topic, partition);
                    offsetSpecs.put(tp, OffsetSpec.forTimestamp(timestampMs));
                }
                
                ListOffsetsResult offsetsResult = adminClient.listOffsets(offsetSpecs);
                Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets = offsetsResult.all().get();
                
                for (Integer partition : partitionsToReset) {
                    org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topic, partition);
                    ListOffsetsResult.ListOffsetsResultInfo info = offsets.get(tp);
                    if (info != null) {
                        offsetsToReset.put(tp, new OffsetAndMetadata(info.offset()));
                    } else {
                        log.warn("Could not get offset for partition {}:{} at timestamp {}, skipping", topic, partition, timestamp);
                    }
                }
            } else {
                // Use provided offset for all partitions
                for (Integer partition : partitionsToReset) {
                    org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topic, partition);
                    offsetsToReset.put(tp, new OffsetAndMetadata(offset != null ? offset : 0L));
                }
            }
            
            if (offsetsToReset.isEmpty()) {
                log.warn("No offsets to reset for consumer group {} and topic {}", groupId, topic);
                return false;
            }
            
            AlterConsumerGroupOffsetsResult result = adminClient.alterConsumerGroupOffsets(groupId, offsetsToReset);
            result.all().get();
            
            log.info("Successfully reset offsets for consumer group {} on topic {} ({} partitions)", 
                    groupId, topic, offsetsToReset.size());
            return true;
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to reset offsets for group [{}], topic [{}]: {}", groupId, topic, e.getMessage(), e);
            kafkaConnectionService.removeAdminClient(cluster.getId());
            throw new RuntimeException("Failed to reset consumer group offsets", e);
        }
    }
}