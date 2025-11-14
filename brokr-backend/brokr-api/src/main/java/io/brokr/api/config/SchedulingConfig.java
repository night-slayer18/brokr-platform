package io.brokr.api.config;

import io.brokr.api.service.ClusterHealthCheckService;
import io.brokr.kafka.service.MetricsCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@RequiredArgsConstructor
public class SchedulingConfig {

    private final ClusterHealthCheckService clusterHealthCheckService;
    private final MetricsCollectionService metricsCollectionService;

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scheduleClusterHealthChecks() {
        clusterHealthCheckService.checkAllClusters();
    }
    
    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    public void scheduleMetricsCollection() {
        metricsCollectionService.collectMetricsForAllClusters();
    }
}
