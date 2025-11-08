package io.brokr.api.config;

import io.brokr.api.service.ClusterHealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@RequiredArgsConstructor
public class SchedulingConfig {

    private final ClusterHealthCheckService clusterHealthCheckService;

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void scheduleClusterHealthChecks() {
        clusterHealthCheckService.checkAllClusters();
    }
}
