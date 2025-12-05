package io.brokr.api.rest.controller;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.api.service.ClusterApiService;
import io.brokr.core.dto.KafkaClusterDto;
import io.brokr.core.model.KafkaCluster;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterApiService clusterApiService;

    @GetMapping
    @PreAuthorize("isAuthenticated()") // Service handles the fine-grained auth
    public List<KafkaClusterDto> getClusters(
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String environmentId) {

        List<KafkaCluster> clusters = clusterApiService.listAuthorizedClusters(organizationId, environmentId);

        return clusters.stream()
                .map(KafkaClusterDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaClusterDto getCluster(@PathVariable String id) {
        return KafkaClusterDto.fromDomain(clusterApiService.getClusterById(id));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.environmentId)")
    public ResponseEntity<KafkaClusterDto> createCluster(@RequestBody KafkaClusterInput input) {
        KafkaCluster savedCluster = clusterApiService.createCluster(input);
        return new ResponseEntity<>(KafkaClusterDto.fromDomain(savedCluster), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaClusterDto updateCluster(@PathVariable String id, @RequestBody KafkaClusterInput input) {
        KafkaCluster updatedCluster = clusterApiService.updateCluster(id, input);
        return KafkaClusterDto.fromDomain(updatedCluster);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public ResponseEntity<Void> deleteCluster(@PathVariable String id) {
        clusterApiService.deleteCluster(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public ResponseEntity<Boolean> testClusterConnection(@PathVariable String id) {
        boolean isReachable = clusterApiService.testClusterConnection(id);
        return ResponseEntity.ok(isReachable);
    }
    
    /**
     * Test JMX connection to brokers in a cluster.
     * Returns true if at least one broker is reachable via JMX.
     */
    @PostMapping("/{id}/test-jmx-connection")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public ResponseEntity<Boolean> testJmxConnection(@PathVariable String id) {
        boolean isReachable = clusterApiService.testJmxConnection(id);
        return ResponseEntity.ok(isReachable);
    }
}