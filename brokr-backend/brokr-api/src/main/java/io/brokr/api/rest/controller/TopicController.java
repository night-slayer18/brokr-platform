package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.input.TopicInput;
import io.brokr.core.dto.TopicDto;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Topic;
import io.brokr.kafka.service.KafkaAdminService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/topics")
@RequiredArgsConstructor
public class TopicController {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<TopicDto> getTopics(@PathVariable String clusterId) {
        KafkaCluster cluster = getCluster(clusterId);
        return kafkaAdminService.listTopics(cluster).stream()
                .map(TopicDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{name}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public TopicDto getTopic(@PathVariable String clusterId, @PathVariable String name) {
        KafkaCluster cluster = getCluster(clusterId);
        return TopicDto.fromDomain(kafkaAdminService.getTopic(cluster, name));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public ResponseEntity<TopicDto> createTopic(@PathVariable String clusterId, @RequestBody TopicInput input) {
        KafkaCluster cluster = getCluster(clusterId);
        Topic newTopic = kafkaAdminService.createTopic(
                cluster,
                input.getName(),
                input.getPartitions(),
                input.getReplicationFactor(),
                input.getConfigs()
        );
        return new ResponseEntity<>(TopicDto.fromDomain(newTopic), HttpStatus.CREATED);
    }

    @PutMapping("/{name}/config")
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public TopicDto updateTopicConfig(@PathVariable String clusterId, @PathVariable String name, @RequestBody Map<String, String> configs) {
        KafkaCluster cluster = getCluster(clusterId);
        kafkaAdminService.updateTopicConfig(cluster, name, configs);
        return TopicDto.fromDomain(kafkaAdminService.getTopic(cluster, name));
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public ResponseEntity<Void> deleteTopic(@PathVariable String clusterId, @PathVariable String name) {
        KafkaCluster cluster = getCluster(clusterId);
        kafkaAdminService.deleteTopic(cluster, name);
        return ResponseEntity.noContent().build();
    }
}