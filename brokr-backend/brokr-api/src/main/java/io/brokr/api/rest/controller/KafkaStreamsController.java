package io.brokr.api.rest.controller;

import io.brokr.api.input.KafkaStreamApplicationInput;
import io.brokr.core.dto.KafkaStreamsApplicationDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.KafkaStreamsApplication;
import io.brokr.core.model.StreamsState;
import io.brokr.kafka.service.KafkaStreamsService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.KafkaStreamsApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KafkaStreamsController {

    private final KafkaStreamsApplicationRepository streamsRepository;
    private final KafkaClusterRepository clusterRepository;
    private final KafkaStreamsService kafkaStreamsService;

    @GetMapping("/clusters/{clusterId}/kafka-streams")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaStreamsApplicationDto> getKafkaStreamsApplications(@PathVariable String clusterId) {
        KafkaCluster cluster = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));

        return streamsRepository.findByClusterId(clusterId).stream()
                .map(entity -> {
                    KafkaStreamsApplication app = entity.toDomain();
                    app.setState(kafkaStreamsService.getState(app, cluster));
                    app.setThreads(kafkaStreamsService.getThreads(app, cluster));
                    return KafkaStreamsApplicationDto.fromDomain(app);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/kafka-streams/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplicationDto getKafkaStreamsApplication(@PathVariable String id) {
        KafkaStreamsApplication app = streamsRepository.findById(id)
                .map(KafkaStreamsApplicationEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Streams App not found with id: " + id));

        KafkaCluster cluster = clusterRepository.findById(app.getClusterId())
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found for this application"));

        app.setState(kafkaStreamsService.getState(app, cluster));
        app.setThreads(kafkaStreamsService.getThreads(app, cluster));
        return KafkaStreamsApplicationDto.fromDomain(app);
    }

    @PostMapping("/kafka-streams")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<KafkaStreamsApplicationDto> createKafkaStreamsApplication(@RequestBody KafkaStreamApplicationInput input) {
        if (streamsRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new ValidationException("Application with this name already exists in the cluster");
        }

        KafkaStreamsApplication app = KafkaStreamsApplication.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .applicationId(input.getApplicationId())
                .clusterId(input.getClusterId())
                .topics(input.getTopics())
                .configuration(input.getConfiguration())
                .isActive(input.isActive())
                .state(StreamsState.NOT_RUNNING)
                .threads(Collections.emptyList())
                .build();

        KafkaStreamsApplication saved = streamsRepository.save(KafkaStreamsApplicationEntity.fromDomain(app)).toDomain();
        return new ResponseEntity<>(KafkaStreamsApplicationDto.fromDomain(saved), HttpStatus.CREATED);
    }

    @PutMapping("/kafka-streams/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplicationDto updateKafkaStreamsApplication(@PathVariable String id, @RequestBody KafkaStreamApplicationInput input) {
        KafkaStreamsApplicationEntity entity = streamsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Streams App not found with id: " + id));

        entity.setName(input.getName());
        entity.setApplicationId(input.getApplicationId());
        entity.setTopics(input.getTopics().toArray(new String[0]));
        entity.setConfiguration(input.getConfiguration());
        entity.setActive(input.isActive());

        KafkaStreamsApplication updated = streamsRepository.save(entity).toDomain();
        return KafkaStreamsApplicationDto.fromDomain(updated);
    }

    @DeleteMapping("/kafka-streams/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public ResponseEntity<Void> deleteKafkaStreamsApplication(@PathVariable String id) {
        if (!streamsRepository.existsById(id)) {
            throw new ResourceNotFoundException("Kafka Streams App not found with id: " + id);
        }
        streamsRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}