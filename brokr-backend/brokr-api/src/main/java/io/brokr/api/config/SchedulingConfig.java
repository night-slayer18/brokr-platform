package io.brokr.api.config;

import io.brokr.api.service.ClusterHealthCheckService;
import io.brokr.kafka.service.MetricsCollectionService;
import io.brokr.kafka.service.MessageReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@RequiredArgsConstructor
public class SchedulingConfig {

    private final ClusterHealthCheckService clusterHealthCheckService;
    private final MetricsCollectionService metricsCollectionService;
    private final MessageReplayService messageReplayService;

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scheduleClusterHealthChecks() {
        clusterHealthCheckService.checkAllClusters();
    }
    
    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    public void scheduleMetricsCollection() {
        metricsCollectionService.collectMetricsForAllClusters();
    }
    
    /**
     * Check for stuck replay jobs and handle them.
     * Runs every 5 minutes to detect jobs that have exceeded their timeout.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void checkStuckReplayJobs() {
        messageReplayService.checkAndHandleStuckJobs();
    }
}
