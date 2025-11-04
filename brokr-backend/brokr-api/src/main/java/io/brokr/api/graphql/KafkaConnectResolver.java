package io.brokr.api.graphql;

import io.brokr.api.input.KafkaConnectInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.KafkaConnect;
import io.brokr.kafka.service.KafkaConnectService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.KafkaConnectRepository;
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
public class KafkaConnectResolver {

    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaClusterRepository clusterRepository;
    private final KafkaConnectService kafkaConnectService;
    private final AuthorizationService authorizationService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaConnect> kafkaConnects(@Argument String clusterId) {
        return kafkaConnectRepository.findByClusterId(clusterId).stream()
                .map(entity -> entity.toDomain())
                .toList();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnect kafkaConnect(@Argument String id) {
        return kafkaConnectRepository.findById(id)
                .map(entity -> entity.toDomain())
                .orElseThrow(() -> new RuntimeException("Kafka Connect not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public KafkaConnect createKafkaConnect(@Argument KafkaConnectInput input) {
        if (kafkaConnectRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new RuntimeException("Kafka Connect with this name already exists in the cluster");
        }

        // Verify the user has access to the cluster
        KafkaCluster cluster = clusterRepository.findById(input.getClusterId())
                .map(entity -> entity.toDomain())
                .orElseThrow(() -> new RuntimeException("Cluster not found"));

        if (!authorizationService.hasAccessToEnvironment(cluster.getEnvironmentId())) {
            throw new RuntimeException("Access denied to this environment");
        }

        KafkaConnect kafkaConnect = KafkaConnect.builder()
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
        boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);
        kafkaConnect.setReachable(isReachable);

        if (!isReachable) {
            throw new RuntimeException("Failed to connect to Kafka Connect. Please check the URL and credentials.");
        }

        return kafkaConnectRepository.save(io.brokr.storage.entity.KafkaConnectEntity.fromDomain(kafkaConnect))
                .toDomain();
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnect updateKafkaConnect(@Argument String id, @Argument KafkaConnectInput input) {
        return kafkaConnectRepository.findById(id)
                .map(entity -> {
                    entity.setName(input.getName());
                    entity.setUrl(input.getUrl());
                    entity.setSecurityProtocol(input.getSecurityProtocol());
                    entity.setUsername(input.getUsername());
                    entity.setPassword(input.getPassword());
                    entity.setActive(input.isActive());

                    KafkaConnect kafkaConnect = entity.toDomain();

                    // Test connection
                    boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);
                    kafkaConnect.setReachable(isReachable);

                    return kafkaConnectRepository.save(io.brokr.storage.entity.KafkaConnectEntity.fromDomain(kafkaConnect))
                            .toDomain();
                })
                .orElseThrow(() -> new RuntimeException("Kafka Connect not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public boolean deleteKafkaConnect(@Argument String id) {
        if (kafkaConnectRepository.existsById(id)) {
            kafkaConnectRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public boolean testKafkaConnectConnection(@Argument String id) {
        return kafkaConnectRepository.findById(id)
                .map(entity -> {
                    KafkaConnect kafkaConnect = entity.toDomain();
                    boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);

                    // Update connection status
                    entity.setReachable(isReachable);
                    entity.setLastConnectionCheck(System.currentTimeMillis());
                    if (!isReachable) {
                        entity.setLastConnectionError("Connection failed");
                    } else {
                        entity.setLastConnectionError(null);
                    }

                    kafkaConnectRepository.save(entity);
                    return isReachable;
                })
                .orElseThrow(() -> new RuntimeException("Kafka Connect not found"));
    }
}