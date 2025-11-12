package io.brokr.api.service;

import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterHealthCheckService {

    private final KafkaClusterRepository clusterRepository;
    private final ClusterApiService clusterApiService;

    public void checkAllClusters() {
        log.info("Starting health check for all clusters...");
        clusterRepository.findAll().forEach(clusterEntity -> {
            try {
                log.debug("Checking health for cluster: {}", clusterEntity.getName());
                // Skip audit logging for scheduled health checks to avoid filling audit logs
                clusterApiService.testClusterConnection(clusterEntity.getId(), false);
            } catch (Exception e) {
                log.error("Error during health check for cluster {}: {}", clusterEntity.getName(), e.getMessage());
            }
        });
        log.info("Finished health check for all clusters.");
    }
}
