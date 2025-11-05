package io.brokr.api.graphql;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.api.service.ClusterApiService;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.KafkaConnect;
import io.brokr.core.model.KafkaStreamsApplication;
import io.brokr.core.model.SchemaRegistry;
import io.brokr.storage.entity.KafkaConnectEntity;
import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import io.brokr.storage.entity.SchemaRegistryEntity;
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

@Controller
@RequiredArgsConstructor
public class ClusterResolver {

    private final ClusterApiService clusterApiService;

    // Repositories for SchemaMappings remain here as they are GraphQL-specific concerns
    private final SchemaRegistryRepository schemaRegistryRepository;
    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaStreamsApplicationRepository kafkaStreamsApplicationRepository;


    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId)")
    public List<KafkaCluster> clusters(@Argument String organizationId, @Argument String environmentId) {
        return clusterApiService.listAuthorizedClusters(organizationId, environmentId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster cluster(@Argument String id) {
        return clusterApiService.getClusterById(id);
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

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.environmentId)")
    public KafkaCluster createCluster(@Argument KafkaClusterInput input) {
        return clusterApiService.createCluster(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster updateCluster(@Argument String id, @Argument KafkaClusterInput input) {
        return clusterApiService.updateCluster(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean deleteCluster(@Argument String id) {
        return clusterApiService.deleteCluster(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean testClusterConnection(@Argument String id) {
        return clusterApiService.testClusterConnection(id);
    }
}