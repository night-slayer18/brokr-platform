package io.brokr.api.graphql;

import io.brokr.api.input.SchemaRegistryInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.SchemaRegistry;
import io.brokr.kafka.service.SchemaRegistryService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.SchemaRegistryRepository;
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
public class SchemaRegistryResolver {

    private final SchemaRegistryRepository schemaRegistryRepository;
    private final KafkaClusterRepository clusterRepository;
    private final SchemaRegistryService schemaRegistryService;
    private final AuthorizationService authorizationService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#clusterId)")
    public List<SchemaRegistry> schemaRegistries(@Argument String clusterId) {
        return schemaRegistryRepository.findByClusterId(clusterId).stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#schemaRegistry.cluster.environmentId)")
    public SchemaRegistry schemaRegistry(@Argument String id) {
        return schemaRegistryRepository.findById(id)
                .map(entity -> entity.toDomain())
                .orElseThrow(() -> new RuntimeException("Schema Registry not found"));
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#schemaRegistry.cluster.environmentId)")
    public List<String> schemaRegistrySubjects(@Argument String schemaRegistryId) {
        SchemaRegistry schemaRegistry = schemaRegistryRepository.findById(schemaRegistryId)
                .map(entity -> entity.toDomain())
                .orElseThrow(() -> new RuntimeException("Schema Registry not found"));

        return schemaRegistryService.getSubjects(schemaRegistry);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#schemaRegistry.cluster.environmentId)")
    public String schemaRegistryLatestSchema(@Argument String schemaRegistryId, @Argument String subject) {
        SchemaRegistry schemaRegistry = schemaRegistryRepository.findById(schemaRegistryId)
                .map(entity -> entity.toDomain())
                .orElseThrow(() -> new RuntimeException("Schema Registry not found"));

        return schemaRegistryService.getLatestSchema(schemaRegistry, subject);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#schemaRegistry.cluster.environmentId)")
    public List<Integer> schemaRegistrySchemaVersions(@Argument String schemaRegistryId, @Argument String subject) {
        SchemaRegistry schemaRegistry = schemaRegistryRepository.findById(schemaRegistryId)
                .map(entity -> entity.toDomain())
                .orElseThrow(() -> new RuntimeException("Schema Registry not found"));

        return schemaRegistryService.getSchemaVersions(schemaRegistry, subject);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.clusterId)")
    public SchemaRegistry createSchemaRegistry(@Argument SchemaRegistryInput input) {
        if (schemaRegistryRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new RuntimeException("Schema Registry with this name already exists in the cluster");
        }

        // Verify the user has access to the cluster
        KafkaCluster cluster = clusterRepository.findById(input.getClusterId())
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));

        if (!authorizationService.hasAccessToEnvironment(cluster.getEnvironmentId())) {
            throw new RuntimeException("Access denied to this environment");
        }

        SchemaRegistry schemaRegistry = SchemaRegistry.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .url(input.getUrl())
                .clusterId(input.getClusterId())
                .securityProtocol(input.getSecurityProtocol())
                .username(input.getUsername())
                .password(input.getPassword())
                .isActive(input.isActive())
                .build();

        // Test connection before saving
        boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);
        schemaRegistry.setReachable(isReachable);

        if (!isReachable) {
            throw new RuntimeException("Failed to connect to the Schema Registry. Please check the URL and credentials.");
        }

        return schemaRegistryRepository.save(io.brokr.storage.entity.SchemaRegistryEntity.fromDomain(schemaRegistry))
                .toDomain();
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public SchemaRegistry updateSchemaRegistry(@Argument String id, @Argument SchemaRegistryInput input) {
        return schemaRegistryRepository.findById(id)
                .map(entity -> {
                    entity.setName(input.getName());
                    entity.setUrl(input.getUrl());
                    entity.setSecurityProtocol(input.getSecurityProtocol());
                    entity.setUsername(input.getUsername());
                    entity.setPassword(input.getPassword());
                    entity.setActive(input.isActive());

                    SchemaRegistry schemaRegistry = entity.toDomain();

                    // Test connection
                    boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);
                    schemaRegistry.setReachable(isReachable);

                    return schemaRegistryRepository.save(io.brokr.storage.entity.SchemaRegistryEntity.fromDomain(schemaRegistry))
                            .toDomain();
                })
                .orElseThrow(() -> new RuntimeException("Schema Registry not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public boolean deleteSchemaRegistry(@Argument String id) {
        if (schemaRegistryRepository.existsById(id)) {
            schemaRegistryRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public boolean testSchemaRegistryConnection(@Argument String id) {
        return schemaRegistryRepository.findById(id)
                .map(entity -> {
                    SchemaRegistry schemaRegistry = entity.toDomain();
                    boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);

                    // Update connection status
                    entity.setReachable(isReachable);
                    entity.setLastConnectionCheck(System.currentTimeMillis());
                    if (!isReachable) {
                        entity.setLastConnectionError("Connection failed");
                    } else {
                        entity.setLastConnectionError(null);
                    }

                    schemaRegistryRepository.save(entity);
                    return isReachable;
                })
                .orElseThrow(() -> new RuntimeException("Schema Registry not found"));
    }
}