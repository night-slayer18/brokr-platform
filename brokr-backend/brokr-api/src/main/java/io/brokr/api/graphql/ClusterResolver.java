package io.brokr.api.graphql;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.kafka.service.KafkaConnectionService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.ClusterDataService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ClusterResolver {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaConnectionService kafkaConnectionService;
    private final AuthorizationService authorizationService;
    private final ClusterDataService clusterDataService; // FIX: Inject new service

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId)")
    public List<KafkaCluster> clusters(@Argument String organizationId, @Argument String environmentId) {
        return clusterDataService.getAuthorizedClusters(organizationId, environmentId);
    }

    @QueryMapping
    // <<< FIX: Changed check from hasAccessToEnvironment to hasAccessToCluster
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster cluster(@Argument String id) {
        return clusterRepository.findById(id)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.environmentId)")
    public KafkaCluster createCluster(@Argument KafkaClusterInput input) {
        if (clusterRepository.existsByNameAndOrganizationId(input.getName(), input.getOrganizationId())) {
            throw new RuntimeException("Cluster with this name already exists in the organization");
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

        // Test connection before saving
        boolean isReachable = kafkaConnectionService.testConnection(cluster);
        cluster.setReachable(isReachable);

        if (!isReachable) {
            throw new RuntimeException("Failed to connect to the Kafka cluster. Please check the bootstrap servers and security settings.");
        }

        return clusterRepository.save(io.brokr.storage.entity.KafkaClusterEntity.fromDomain(cluster))
                .toDomain();
    }

    @MutationMapping
    // <<< FIX: Changed check from hasAccessToEnvironment to hasAccessToCluster
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster updateCluster(@Argument String id, @Argument KafkaClusterInput input) {
        return clusterRepository.findById(id)
                .map(entity -> {
                    entity.setName(input.getName());
                    entity.setBootstrapServers(input.getBootstrapServers());
                    entity.setProperties(input.getProperties());
                    entity.setActive(input.isActive());
                    entity.setDescription(input.getDescription());

                    // Update security settings
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

                    // Test connection
                    boolean isReachable = kafkaConnectionService.testConnection(cluster);
                    cluster.setReachable(isReachable);

                    return clusterRepository.save(io.brokr.storage.entity.KafkaClusterEntity.fromDomain(cluster))
                            .toDomain();
                })
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }

    @MutationMapping
    // <<< FIX: Changed check from hasAccessToEnvironment to hasAccessToCluster
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean deleteCluster(@Argument String id) {
        if (clusterRepository.existsById(id)) {
            clusterRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @MutationMapping
    // <<< FIX: Changed check from hasAccessToEnvironment to hasAccessToCluster
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean testClusterConnection(@Argument String id) {
        return clusterRepository.findById(id)
                .map(entity -> {
                    KafkaCluster cluster = entity.toDomain();
                    boolean isReachable = kafkaConnectionService.testConnection(cluster);

                    // Update connection status
                    entity.setReachable(isReachable);
                    entity.setLastConnectionCheck(System.currentTimeMillis());
                    if (!isReachable) {
                        entity.setLastConnectionError("Connection failed");
                    } else {
                        entity.setLastConnectionError(null);
                    }

                    clusterRepository.save(entity);
                    return isReachable;
                })
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }
}