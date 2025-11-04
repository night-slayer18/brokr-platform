package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.exception.ValidationException;
import io.brokr.api.input.KafkaClusterInput;
import io.brokr.core.dto.KafkaClusterDto;
import io.brokr.core.model.KafkaCluster;
import io.brokr.kafka.service.KafkaConnectionService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.ClusterDataService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaConnectionService kafkaConnectionService;
    private final AuthorizationService authorizationService;
    private final ClusterDataService clusterDataService; // FIX: Inject new service

    // Note: GET /api/v1/organizations/{orgId}/clusters is also a
    // valid REST pattern, but we follow the resolver's simpler logic.
    @GetMapping
    @PreAuthorize("isAuthenticated()") // FIX: Use simpler auth, service handles the rest
    public List<KafkaClusterDto> getClusters(
            @RequestParam(required = false) String organizationId, // FIX: Make optional
            @RequestParam(required = false) String environmentId) {

        // FIX: All logic is now in the service.
        List<KafkaCluster> clusters = clusterDataService.getAuthorizedClusters(organizationId, environmentId);

        // FIX: Convert models to DTOs for the REST response
        return clusters.stream()
                .map(KafkaClusterDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaClusterDto getCluster(@PathVariable String id) {
        return clusterRepository.findById(id)
                .map(KafkaClusterEntity::toDomain)
                .map(KafkaClusterDto::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.environmentId)")
    public ResponseEntity<KafkaClusterDto> createCluster(@RequestBody KafkaClusterInput input) {
        if (clusterRepository.existsByNameAndOrganizationId(input.getName(), input.getOrganizationId())) {
            throw new ValidationException("Cluster with this name already exists in the organization");
        }

        // Logic from ClusterResolver
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

        KafkaCluster savedCluster = clusterRepository.save(KafkaClusterEntity.fromDomain(cluster)).toDomain();
        return new ResponseEntity<>(KafkaClusterDto.fromDomain(savedCluster), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaClusterDto updateCluster(@PathVariable String id, @RequestBody KafkaClusterInput input) {
        KafkaClusterEntity entity = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));

        // Logic from ClusterResolver
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

        KafkaCluster updatedCluster = clusterRepository.save(KafkaClusterEntity.fromDomain(cluster)).toDomain();
        return KafkaClusterDto.fromDomain(updatedCluster);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public ResponseEntity<Void> deleteCluster(@PathVariable String id) {
        if (!clusterRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cluster not found with id: " + id);
        }
        clusterRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public ResponseEntity<Boolean> testClusterConnection(@PathVariable String id) {
        // Logic from ClusterResolver
        boolean isReachable = clusterRepository.findById(id)
                .map(entity -> {
                    KafkaCluster cluster = entity.toDomain();
                    boolean reachable = kafkaConnectionService.testConnection(cluster);

                    entity.setReachable(reachable);
                    entity.setLastConnectionCheck(System.currentTimeMillis());
                    entity.setLastConnectionError(reachable ? null : "Connection failed");
                    clusterRepository.save(entity);
                    return reachable;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));

        return ResponseEntity.ok(isReachable);
    }
}