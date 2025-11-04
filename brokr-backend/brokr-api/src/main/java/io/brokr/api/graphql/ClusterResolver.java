package io.brokr.api.graphql;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.KafkaConnect;
import io.brokr.core.model.KafkaStreamsApplication;
import io.brokr.core.model.SchemaRegistry;
import io.brokr.kafka.service.KafkaConnectionService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.ClusterDataService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.entity.KafkaConnectEntity;
import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import io.brokr.storage.entity.SchemaRegistryEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.KafkaConnectRepository;
import io.brokr.storage.repository.KafkaStreamsApplicationRepository;
import io.brokr.storage.repository.SchemaRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
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
    private final ClusterDataService clusterDataService;

    // FIX: Inject repositories for related entities
    private final SchemaRegistryRepository schemaRegistryRepository;
    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaStreamsApplicationRepository kafkaStreamsApplicationRepository;


    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId)")
    public List<KafkaCluster> clusters(@Argument String organizationId, @Argument String environmentId) {
        return clusterDataService.getAuthorizedClusters(organizationId, environmentId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster cluster(@Argument String id) {
        return clusterRepository.findById(id)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }

    // FIX: START: Add SchemaMappings for related fields
    @SchemaMapping(typeName = "KafkaCluster", field = "schemaRegistries")
    public List<SchemaRegistry> getSchemaRegistries(KafkaCluster cluster) {
        return schemaRegistryRepository.findByClusterId(cluster.getId())
                .stream()
                .map(SchemaRegistryEntity::toDomain)
                .toList();
    }

    @SchemaMapping(typeName = "KafkaCluster", field = "kafkaConnects")
    public List<KafkaConnect> getKafkaConnects(KafkaCluster cluster) {
        return kafkaConnectRepository.findByClusterId(cluster.getId())
                .stream()
                .map(KafkaConnectEntity::toDomain)
                .toList();
    }

    @SchemaMapping(typeName = "KafkaCluster", field = "kafkaStreamsApplications")
    public List<KafkaStreamsApplication> getKafkaStreamsApplications(KafkaCluster cluster) {
        // Note: This does not populate the live state.
        // The *list* view in GraphQL will show stored state.
        // The dedicated query `kafkaStreamsApplication(id: ...)` *will* show live state.
        // This is an acceptable performance trade-off.
        return kafkaStreamsApplicationRepository.findByClusterId(cluster.getId())
                .stream()
                .map(KafkaStreamsApplicationEntity::toDomain)
                .toList();
    }
    // FIX: END

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.environmentId)")
    public KafkaCluster createCluster(@Argument KafkaClusterInput input) {
        // ... (rest of method is unchanged) ...
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
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster updateCluster(@Argument String id, @Argument KafkaClusterInput input) {
        // ... (rest of method is unchanged) ...
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
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean deleteCluster(@Argument String id) {
        // ... (rest of method is unchanged) ...
        if (clusterRepository.existsById(id)) {
            clusterRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean testClusterConnection(@Argument String id) {
        // ... (rest of method is unchanged) ...
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