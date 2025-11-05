package io.brokr.api.service;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.KafkaCluster;
import io.brokr.kafka.service.KafkaConnectionService;
import io.brokr.security.service.ClusterDataService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClusterApiService {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaConnectionService kafkaConnectionService;
    private final ClusterDataService clusterDataService;

    public List<KafkaCluster> listAuthorizedClusters(String organizationId, String environmentId) {
        return clusterDataService.getAuthorizedClusters(organizationId, environmentId);
    }

    public KafkaCluster getClusterById(String id) {
        return clusterRepository.findById(id)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));
    }

    public KafkaCluster createCluster(KafkaClusterInput input) {
        if (clusterRepository.existsByNameAndOrganizationId(input.getName(), input.getOrganizationId())) {
            throw new ValidationException("Cluster with this name already exists in the organization");
        }

        KafkaCluster cluster = KafkaCluster.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .bootstrapServers(input.getBootstrapServers())
                .properties(input.getProperties())
                .isActive(input.isActive())
                .description(input.getDescription())
                .organizationId(input.getOrganizationId())
                .environmentId(input.getEnvironmentId())
                .securityProtocol(input.getSecurityProtocol())
                .saslMechanism(input.getSaslMechanism())
                .saslUsername(input.getSaslUsername())
                .saslPassword(input.getSaslPassword())
                .sslTruststoreLocation(input.getSslTruststoreLocation())
                .sslTruststorePassword(input.getSslTruststorePassword())
                .sslKeystoreLocation(input.getSslKeystoreLocation())
                .sslKeystorePassword(input.getSslKeystorePassword())
                .sslKeyPassword(input.getSslKeyPassword())
                .build();

        boolean isReachable = kafkaConnectionService.testConnection(cluster);
        if (!isReachable) {
            throw new ValidationException("Failed to connect to the Kafka cluster. Please check the bootstrap servers and security settings.");
        }
        cluster.setReachable(isReachable);

        return clusterRepository.save(KafkaClusterEntity.fromDomain(cluster)).toDomain();
    }

    public KafkaCluster updateCluster(String id, KafkaClusterInput input) {
        KafkaClusterEntity entity = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));

        if (!entity.getName().equals(input.getName()) &&
                clusterRepository.existsByNameAndOrganizationId(input.getName(), entity.getOrganizationId())) {
            throw new ValidationException("Cluster with this name already exists in the organization");
        }
        
        entity.setName(input.getName());
        entity.setBootstrapServers(input.getBootstrapServers());
        entity.setProperties(input.getProperties());
        entity.setActive(input.isActive());
        entity.setDescription(input.getDescription());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setSaslMechanism(input.getSaslMechanism());
        entity.setSaslUsername(input.getSaslUsername());
        entity.setSaslPassword(input.getSaslPassword());
        entity.setSslTruststoreLocation(input.getSslTruststoreLocation());
        entity.setSslTruststorePassword(input.getSslTruststorePassword());
        entity.setSslKeystoreLocation(input.getSslKeystoreLocation());
        entity.setSslKeystorePassword(input.getSslKeystorePassword());
        entity.setSslKeyPassword(input.getSslKeyPassword());

        KafkaCluster cluster = entity.toDomain();
        boolean isReachable = kafkaConnectionService.testConnection(cluster);
        cluster.setReachable(isReachable);

        return clusterRepository.save(KafkaClusterEntity.fromDomain(cluster)).toDomain();
    }

    public boolean deleteCluster(String id) {
        if (!clusterRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cluster not found with id: " + id);
        }
        clusterRepository.deleteById(id);
        return true;
    }

    public boolean testClusterConnection(String id) {
        KafkaClusterEntity entity = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));

        KafkaCluster cluster = entity.toDomain();
        boolean reachable = kafkaConnectionService.testConnection(cluster);

        entity.setReachable(reachable);
        entity.setLastConnectionCheck(System.currentTimeMillis());
        entity.setLastConnectionError(reachable ? null : "Connection failed");
        clusterRepository.save(entity);
        return reachable;
    }
}