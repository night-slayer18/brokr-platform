package io.brokr.kafka.service;

import io.brokr.core.model.*;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MetricsCollectionService {
    
    private static final int METRICS_COLLECTION_TIMEOUT_SECONDS = 300; // 5 minutes per cluster
    private static final int MAX_TOPICS_PER_BATCH = 1000; // Process topics in batches for very large clusters
    private static final int MAX_CONSUMER_GROUPS_PER_BATCH = 500; // Process consumer groups in batches
    
    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;
    private final TopicMetricsService topicMetricsService;
    private final ConsumerGroupMetricsService consumerGroupMetricsService;
    private final ClusterMetricsService clusterMetricsService;
    private final Executor metricsExecutor;
    
    // Cache for previous metrics to calculate throughput (clusterId:topicName -> previous metrics)
    private final Map<String, TopicMetrics> previousTopicMetricsCache = new ConcurrentHashMap<>();
    
    public MetricsCollectionService(
            KafkaClusterRepository clusterRepository,
            KafkaAdminService kafkaAdminService,
            TopicMetricsService topicMetricsService,
            ConsumerGroupMetricsService consumerGroupMetricsService,
            ClusterMetricsService clusterMetricsService,
            @Qualifier("metricsCollectionExecutor") Executor metricsExecutor) {
        this.clusterRepository = clusterRepository;
        this.kafkaAdminService = kafkaAdminService;
        this.topicMetricsService = topicMetricsService;
        this.consumerGroupMetricsService = consumerGroupMetricsService;
        this.clusterMetricsService = clusterMetricsService;
        this.metricsExecutor = metricsExecutor;
    }
    
    /**
     * Collect metrics for all active and reachable clusters
     * This is called by the scheduled job
     * Uses dedicated executor for metrics collection
     */
    @Async("metricsCollectionExecutor")
    public CompletableFuture<Void> collectMetricsForAllClusters() {
        log.info("Starting metrics collection for all clusters");
        
        try {
            List<KafkaCluster> clusters = clusterRepository.findAll()
                    .stream()
                    .map(KafkaClusterEntity::toDomain)
                    .filter(KafkaCluster::isActive)
                    .filter(KafkaCluster::isReachable)
                    .collect(Collectors.toList());
            
            if (clusters.isEmpty()) {
                log.debug("No active and reachable clusters found for metrics collection");
                return CompletableFuture.completedFuture(null);
            }
            
            log.info("Collecting metrics for {} clusters", clusters.size());
            
            // Collect metrics for all clusters in parallel
            List<CompletableFuture<Void>> futures = clusters.stream()
                    .map(this::collectMetricsForCluster)
                    .collect(Collectors.toList());
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> log.info("Completed metrics collection for {} clusters", clusters.size()))
                    .exceptionally(ex -> {
                        log.error("Error during metrics collection", ex);
                        return null;
                    });
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to collect metrics for all clusters", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Collect metrics for a single cluster
     * Uses batch operations to avoid N+1 queries
     * Uses dedicated executor for metrics collection
     */
    @Async("metricsCollectionExecutor")
    public CompletableFuture<Void> collectMetricsForCluster(KafkaCluster cluster) {
        try {
            log.debug("Collecting metrics for cluster: {}", cluster.getName());
            
            // Collect topic and consumer group metrics in parallel
            CompletableFuture<List<TopicMetrics>> topicMetricsFuture = 
                    CompletableFuture.supplyAsync(() -> collectTopicMetrics(cluster), metricsExecutor);
            
            CompletableFuture<List<ConsumerGroupMetrics>> consumerGroupMetricsFuture = 
                    CompletableFuture.supplyAsync(() -> collectConsumerGroupMetrics(cluster), metricsExecutor);
            
            // Wait for topic and consumer group metrics to complete first
            try {
                CompletableFuture.allOf(topicMetricsFuture, consumerGroupMetricsFuture)
                        .get(METRICS_COLLECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                
                // Get topic and consumer group metrics
                List<TopicMetrics> topicMetrics = topicMetricsFuture.get();
                List<ConsumerGroupMetrics> consumerGroupMetrics = consumerGroupMetricsFuture.get();
                
                // Calculate cluster metrics AFTER topic metrics are collected (to aggregate throughput)
                ClusterMetrics clusterMetrics = collectClusterMetrics(cluster, topicMetrics);
                
                // Batch save operations - optimized for large datasets
                if (!topicMetrics.isEmpty()) {
                    // Save in batches if very large
                    if (topicMetrics.size() > MAX_TOPICS_PER_BATCH) {
                        int batches = (topicMetrics.size() + MAX_TOPICS_PER_BATCH - 1) / MAX_TOPICS_PER_BATCH;
                        log.debug("Saving {} topic metrics in {} batches for cluster: {}", 
                                topicMetrics.size(), batches, cluster.getName());
                        for (int i = 0; i < topicMetrics.size(); i += MAX_TOPICS_PER_BATCH) {
                            int end = Math.min(i + MAX_TOPICS_PER_BATCH, topicMetrics.size());
                            topicMetricsService.saveAllMetrics(topicMetrics.subList(i, end));
                        }
                    } else {
                        topicMetricsService.saveAllMetrics(topicMetrics);
                    }
                    log.debug("Saved {} topic metrics for cluster: {}", topicMetrics.size(), cluster.getName());
                }
                
                if (!consumerGroupMetrics.isEmpty()) {
                    // Save in batches if very large
                    if (consumerGroupMetrics.size() > MAX_CONSUMER_GROUPS_PER_BATCH) {
                        int batches = (consumerGroupMetrics.size() + MAX_CONSUMER_GROUPS_PER_BATCH - 1) / MAX_CONSUMER_GROUPS_PER_BATCH;
                        log.debug("Saving {} consumer group metrics in {} batches for cluster: {}", 
                                consumerGroupMetrics.size(), batches, cluster.getName());
                        for (int i = 0; i < consumerGroupMetrics.size(); i += MAX_CONSUMER_GROUPS_PER_BATCH) {
                            int end = Math.min(i + MAX_CONSUMER_GROUPS_PER_BATCH, consumerGroupMetrics.size());
                            consumerGroupMetricsService.saveAllMetrics(consumerGroupMetrics.subList(i, end));
                        }
                    } else {
                        consumerGroupMetricsService.saveAllMetrics(consumerGroupMetrics);
                    }
                    log.debug("Saved {} consumer group metrics for cluster: {}", 
                            consumerGroupMetrics.size(), cluster.getName());
                }
                
                if (clusterMetrics != null) {
                    clusterMetricsService.saveMetrics(clusterMetrics);
                    log.debug("Saved cluster metrics for cluster: {}", cluster.getName());
                }
            } catch (TimeoutException e) {
                log.error("Metrics collection timeout for cluster: {} after {} seconds", 
                        cluster.getName(), METRICS_COLLECTION_TIMEOUT_SECONDS);
                // Cancel futures to free resources
                topicMetricsFuture.cancel(true);
                consumerGroupMetricsFuture.cancel(true);
            } catch (Exception e) {
                log.error("Failed to save metrics for cluster: {}", cluster.getName(), e);
            }
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to collect metrics for cluster: {}", cluster.getName(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Collect topic metrics for a cluster - batch operation
     */
    private List<TopicMetrics> collectTopicMetrics(KafkaCluster cluster) {
        try {
            // Batch fetch all topics in one call
            List<Topic> topics = kafkaAdminService.listTopics(cluster);
            
            if (topics.isEmpty()) {
                return Collections.emptyList();
            }
            
            LocalDateTime now = LocalDateTime.now();
            List<TopicMetrics> metricsList = new ArrayList<>();
            
            // Process all topics and calculate metrics
            for (Topic topic : topics) {
                try {
                    TopicMetrics metrics = calculateTopicMetrics(cluster, topic, now);
                    if (metrics != null) {
                        metricsList.add(metrics);
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate metrics for topic: {} in cluster: {}", 
                            topic.getName(), cluster.getName(), e);
                    // Continue with other topics
                }
            }
            
            return metricsList;
        } catch (Exception e) {
            log.error("Failed to collect topic metrics for cluster: {}", cluster.getName(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Calculate topic metrics - stores current state and calculates throughput
     * Throughput is calculated by comparing with previous metrics snapshot
     */
    private TopicMetrics calculateTopicMetrics(KafkaCluster cluster, Topic topic, LocalDateTime timestamp) {
        try {
            // Get detailed topic info with partition data
            Topic detailedTopic = kafkaAdminService.getTopic(cluster, topic.getName());
            
            if (detailedTopic == null || detailedTopic.getPartitionsInfo() == null) {
                return null;
            }
            
            Map<String, Object> partitionSizes = new HashMap<>();
            Map<String, Object> partitionOffsets = new HashMap<>();
            
            long totalSize = 0;
            long totalLatestOffset = 0;
            
            for (PartitionInfo partition : detailedTopic.getPartitionsInfo()) {
                String partitionKey = String.valueOf(partition.getId());
                long size = partition.getSize();
                long latestOffset = partition.getLatestOffset();
                
                partitionSizes.put(partitionKey, size);
                partitionOffsets.put(partitionKey, Map.of(
                        "earliest", partition.getEarliestOffset(),
                        "latest", latestOffset
                ));
                totalSize += size;
                totalLatestOffset += latestOffset;
            }
            
            // Calculate throughput by comparing with previous metrics
            // First try cache, then fall back to database
            String cacheKey = cluster.getId() + ":" + topic.getName();
            TopicMetrics previousMetrics = previousTopicMetricsCache.get(cacheKey);
            
            // If not in cache, try to get from database (for restarts or first run after restart)
            if (previousMetrics == null) {
                previousMetrics = topicMetricsService.getLatestMetrics(cluster.getId(), topic.getName());
                if (previousMetrics != null) {
                    previousTopicMetricsCache.put(cacheKey, previousMetrics);
                }
            }
            
            long messagesPerSecondIn = 0L;
            long bytesPerSecondIn = 0L;
            
            if (previousMetrics != null && previousMetrics.getPartitionOffsets() != null) {
                // Calculate time difference in seconds
                long timeDiffSeconds = java.time.Duration.between(previousMetrics.getTimestamp(), timestamp).getSeconds();
                
                if (timeDiffSeconds > 0) {
                    // Calculate total previous latest offset
                    long previousTotalLatestOffset = 0;
                    if (previousMetrics.getPartitionOffsets() != null) {
                        for (Object offsetMap : previousMetrics.getPartitionOffsets().values()) {
                            if (offsetMap instanceof Map) {
                                Map<String, Object> offsetData = (Map<String, Object>) offsetMap;
                                Object latest = offsetData.get("latest");
                                if (latest instanceof Number) {
                                    previousTotalLatestOffset += ((Number) latest).longValue();
                                }
                            }
                        }
                    }
                    
                    // Messages In/sec = (current total latest offset - previous total latest offset) / time difference
                    long messagesDelta = totalLatestOffset - previousTotalLatestOffset;
                    if (messagesDelta > 0) {
                        messagesPerSecondIn = messagesDelta / timeDiffSeconds;
                    }
                    
                    // Bytes In/sec = (current total size - previous total size) / time difference
                    long previousSize = previousMetrics.getTotalSizeBytes() != null ? previousMetrics.getTotalSizeBytes() : 0;
                    long sizeDelta = totalSize - previousSize;
                    if (sizeDelta > 0) {
                        bytesPerSecondIn = sizeDelta / timeDiffSeconds;
                    }
                }
            }
            
            // Build current metrics
            TopicMetrics currentMetrics = TopicMetrics.builder()
                    .id(UUID.randomUUID().toString())
                    .clusterId(cluster.getId())
                    .topicName(topic.getName())
                    .totalSizeBytes(totalSize)
                    .partitionCount(topic.getPartitions())
                    .partitionSizes(partitionSizes)
                    .partitionOffsets(partitionOffsets)
                    .messagesPerSecondIn(messagesPerSecondIn)
                    .messagesPerSecondOut(0L) // Consumer throughput requires consumer group data
                    .bytesPerSecondIn(bytesPerSecondIn)
                    .bytesPerSecondOut(0L) // Consumer throughput requires consumer group data
                    .timestamp(timestamp)
                    .build();
            
            // Update cache with current metrics for next calculation
            previousTopicMetricsCache.put(cacheKey, currentMetrics);
            
            return currentMetrics;
        } catch (Exception e) {
            log.warn("Failed to calculate metrics for topic: {} in cluster: {}", 
                    topic.getName(), cluster.getName(), e);
            return null;
        }
    }
    
    /**
     * Collect consumer group metrics for a cluster - batch operation
     */
    private List<ConsumerGroupMetrics> collectConsumerGroupMetrics(KafkaCluster cluster) {
        try {
            // Batch fetch all consumer groups in one call
            List<ConsumerGroup> consumerGroups = kafkaAdminService.listConsumerGroups(cluster);
            
            if (consumerGroups.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Batch fetch offsets for all consumer groups
            List<String> groupIds = consumerGroups.stream()
                    .map(ConsumerGroup::getGroupId)
                    .collect(Collectors.toList());
            
            // Use batch method to get offsets for all groups efficiently
            Map<String, Map<String, Long>> groupOffsets = kafkaAdminService.getConsumerGroupOffsetsBatch(cluster, groupIds);
            
            LocalDateTime now = LocalDateTime.now();
            List<ConsumerGroupMetrics> metricsList = new ArrayList<>();
            
            // Calculate metrics for each consumer group
            for (ConsumerGroup group : consumerGroups) {
                try {
                    Map<String, Long> topicLags = groupOffsets.getOrDefault(group.getGroupId(), Collections.emptyMap());
                    ConsumerGroupMetrics metrics = calculateConsumerGroupMetrics(cluster, group, topicLags, now);
                    if (metrics != null) {
                        metricsList.add(metrics);
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate metrics for consumer group: {} in cluster: {}", 
                            group.getGroupId(), cluster.getName(), e);
                    // Continue with other groups
                }
            }
            
            return metricsList;
        } catch (Exception e) {
            log.error("Failed to collect consumer group metrics for cluster: {}", cluster.getName(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Calculate consumer group metrics
     */
    private ConsumerGroupMetrics calculateConsumerGroupMetrics(KafkaCluster cluster, ConsumerGroup group,
                                                               Map<String, Long> topicLags, LocalDateTime timestamp) {
        if (topicLags == null || topicLags.isEmpty()) {
            // Still create metrics with zero lag if no offsets available
            topicLags = Collections.emptyMap();
        }
        
        long totalLag = topicLags.values().stream().mapToLong(Long::longValue).sum();
        OptionalLong maxLag = topicLags.values().stream().mapToLong(Long::longValue).max();
        OptionalLong minLag = topicLags.values().stream().mapToLong(Long::longValue).min();
        double avgLag = topicLags.isEmpty() ? 0 : totalLag / (double) topicLags.size();
        
        int memberCount = group.getMembers() != null ? group.getMembers().size() : 0;
        
        return ConsumerGroupMetrics.builder()
                .id(UUID.randomUUID().toString())
                .clusterId(cluster.getId())
                .consumerGroupId(group.getGroupId())
                .totalLag(totalLag)
                .maxLag(maxLag.isPresent() ? maxLag.getAsLong() : 0)
                .minLag(minLag.isPresent() ? minLag.getAsLong() : 0)
                .avgLag((long) avgLag)
                .topicLags(topicLags)
                .memberCount(memberCount)
                .activeMemberCount(memberCount) // Assuming all members are active if they exist
                .timestamp(timestamp)
                .build();
    }
    
    /**
     * Collect cluster-level metrics
     * Aggregates topic metrics to calculate cluster-wide throughput
     */
    private ClusterMetrics collectClusterMetrics(KafkaCluster cluster, List<TopicMetrics> topicMetrics) {
        try {
            // Batch fetch all topics to calculate aggregates
            List<Topic> topics = kafkaAdminService.listTopics(cluster);
            int totalPartitions = topics.stream().mapToInt(Topic::getPartitions).sum();
            
            // Get broker nodes
            List<BrokerNode> brokers = kafkaAdminService.getClusterNodes(cluster);
            
            Map<String, Object> brokerDetails = new HashMap<>();
            if (brokers != null && !brokers.isEmpty()) {
                brokerDetails.put("count", brokers.size());
                brokerDetails.put("nodes", brokers.stream()
                        .map(b -> Map.of(
                                "id", b.getId(),
                                "host", b.getHost() != null ? b.getHost() : "",
                                "port", b.getPort()
                        ))
                        .collect(Collectors.toList()));
            }
            
            // Aggregate throughput from all topic metrics
            long totalMessagesPerSecond = 0L;
            long totalBytesPerSecond = 0L;
            
            if (topicMetrics != null && !topicMetrics.isEmpty()) {
                for (TopicMetrics topicMetric : topicMetrics) {
                    if (topicMetric.getMessagesPerSecondIn() != null) {
                        totalMessagesPerSecond += topicMetric.getMessagesPerSecondIn();
                    }
                    if (topicMetric.getBytesPerSecondIn() != null) {
                        totalBytesPerSecond += topicMetric.getBytesPerSecondIn();
                    }
                }
            }
            
            return ClusterMetrics.builder()
                    .id(UUID.randomUUID().toString())
                    .clusterId(cluster.getId())
                    .brokerCount(brokers != null ? brokers.size() : 0)
                    .activeBrokerCount(brokers != null ? brokers.size() : 0)
                    .totalTopics(topics.size())
                    .totalPartitions(totalPartitions)
                    .totalMessagesPerSecond(totalMessagesPerSecond)
                    .totalBytesPerSecond(totalBytesPerSecond)
                    .isHealthy(cluster.isReachable())
                    .connectionErrorCount(0) // Can be enhanced to track actual errors
                    .brokerDetails(brokerDetails)
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to collect cluster metrics for cluster: {}", cluster.getName(), e);
            return null;
        }
    }
}

