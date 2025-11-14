package io.brokr.api.graphql;

import io.brokr.api.service.MetricsApiService;
import io.brokr.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MetricsResolver {
    
    private final MetricsApiService metricsApiService;
    
    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<TopicMetrics> topicMetrics(
            @Argument String clusterId,
            @Argument String topicName,
            @Argument Map<String, Object> timeRange,
            @Argument Integer limit) {
        
        int queryLimit = (limit != null && limit > 0) ? limit : 1000;
        long startTime = ((Number) timeRange.get("startTime")).longValue();
        long endTime = ((Number) timeRange.get("endTime")).longValue();
        
        return metricsApiService.getTopicMetrics(clusterId, topicName, startTime, endTime, queryLimit);
    }
    
    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ConsumerGroupMetrics> consumerGroupMetrics(
            @Argument String clusterId,
            @Argument String consumerGroupId,
            @Argument Map<String, Object> timeRange,
            @Argument Integer limit) {
        
        int queryLimit = (limit != null && limit > 0) ? limit : 1000;
        long startTime = ((Number) timeRange.get("startTime")).longValue();
        long endTime = ((Number) timeRange.get("endTime")).longValue();
        
        return metricsApiService.getConsumerGroupMetrics(clusterId, consumerGroupId, startTime, endTime, queryLimit);
    }
    
    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ClusterMetrics> clusterMetrics(
            @Argument String clusterId,
            @Argument Map<String, Object> timeRange,
            @Argument Integer limit) {
        
        int queryLimit = (limit != null && limit > 0) ? limit : 1000;
        long startTime = ((Number) timeRange.get("startTime")).longValue();
        long endTime = ((Number) timeRange.get("endTime")).longValue();
        
        return metricsApiService.getClusterMetrics(clusterId, startTime, endTime, queryLimit);
    }
    
    // Schema mappings for timestamp conversion
    @SchemaMapping(typeName = "TopicMetrics", field = "timestamp")
    public Long timestamp(TopicMetrics metrics) {
        if (metrics.getTimestamp() == null) {
            return null;
        }
        return metrics.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    @SchemaMapping(typeName = "ConsumerGroupMetrics", field = "timestamp")
    public Long timestamp(ConsumerGroupMetrics metrics) {
        if (metrics.getTimestamp() == null) {
            return null;
        }
        return metrics.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    @SchemaMapping(typeName = "ClusterMetrics", field = "timestamp")
    public Long timestamp(ClusterMetrics metrics) {
        if (metrics.getTimestamp() == null) {
            return null;
        }
        return metrics.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

