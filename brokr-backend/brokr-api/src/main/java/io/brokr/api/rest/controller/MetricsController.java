package io.brokr.api.rest.controller;

import io.brokr.api.service.MetricsApiService;
import io.brokr.core.model.BrokerMetrics;
import io.brokr.core.model.ClusterMetrics;
import io.brokr.core.model.ConsumerGroupMetrics;
import io.brokr.core.model.TopicMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for metrics operations.
 * Thin wrapper around MetricsApiService - no service changes needed.
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {
    
    private final MetricsApiService metricsApiService;
    
    @GetMapping("/topics/{clusterId}/{topicName}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<TopicMetrics> getTopicMetrics(
            @PathVariable String clusterId,
            @PathVariable String topicName,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        return metricsApiService.getTopicMetrics(clusterId, topicName, startTime, endTime, limit);
    }
    
    @GetMapping("/consumer-groups/{clusterId}/{groupId}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ConsumerGroupMetrics> getConsumerGroupMetrics(
            @PathVariable String clusterId,
            @PathVariable String consumerGroupId,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        return metricsApiService.getConsumerGroupMetrics(
                clusterId, consumerGroupId, startTime, endTime, limit);
    }
    
    @GetMapping("/clusters/{clusterId}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ClusterMetrics> getClusterMetrics(
            @PathVariable String clusterId,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        return metricsApiService.getClusterMetrics(clusterId, startTime, endTime, limit);
    }
    
    /**
     * Get broker metrics for all brokers in a cluster within a time range.
     */
    @GetMapping("/brokers/{clusterId}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<BrokerMetrics> getBrokerMetrics(
            @PathVariable String clusterId,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        return metricsApiService.getBrokerMetrics(clusterId, startTime, endTime, limit);
    }
    
    /**
     * Get broker metrics for a specific broker within a time range.
     */
    @GetMapping("/brokers/{clusterId}/{brokerId}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<BrokerMetrics> getBrokerMetricsByBroker(
            @PathVariable String clusterId,
            @PathVariable int brokerId,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        return metricsApiService.getBrokerMetricsByBroker(clusterId, brokerId, startTime, endTime, limit);
    }
    
    /**
     * Get the latest broker metrics for all brokers in a cluster.
     */
    @GetMapping("/brokers/{clusterId}/latest")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<BrokerMetrics> getLatestBrokerMetrics(@PathVariable String clusterId) {
        return metricsApiService.getLatestBrokerMetrics(clusterId);
    }
}
