package io.brokr.api.service;

import io.brokr.api.input.KafkaStreamApplicationInput;
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
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KafkaStreamsApiService {

    private final KafkaStreamsApplicationRepository streamsRepository;
    private final KafkaClusterRepository clusterRepository;
    private final KafkaStreamsService kafkaStreamsService;

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));
    }

    public List<KafkaStreamsApplication> listKafkaStreamsApplications(String clusterId) {
        KafkaCluster cluster = getCluster(clusterId);
        return streamsRepository.findByClusterId(clusterId).stream()
                .map(entity -> {
                    KafkaStreamsApplication app = entity.toDomain();
                    app.setState(kafkaStreamsService.getState(app, cluster));
                    app.setThreads(kafkaStreamsService.getThreads(app, cluster));
                    return app;
                })
                .collect(Collectors.toList());
    }

    public Map<String, List<KafkaStreamsApplication>> getKafkaStreamsApplicationsForClusters(List<String> clusterIds) {
        // Note: This does not populate the live state, consistent with the non-batch list view.
        return streamsRepository.findByClusterIdIn(clusterIds).stream()
                .map(KafkaStreamsApplicationEntity::toDomain)
                .collect(Collectors.groupingBy(KafkaStreamsApplication::getClusterId));
    }

    public KafkaStreamsApplication getKafkaStreamsApplication(String id) {
        KafkaStreamsApplication app = streamsRepository.findById(id)
                .map(KafkaStreamsApplicationEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Streams Application not found"));

        KafkaCluster cluster = getCluster(app.getClusterId());
        app.setState(kafkaStreamsService.getState(app, cluster));
        app.setThreads(kafkaStreamsService.getThreads(app, cluster));
        return app;
    }

    public KafkaStreamsApplication createKafkaStreamsApplication(KafkaStreamApplicationInput input) {
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
                .state(StreamsState.NOT_RUNNING) // Set initial state
                .threads(Collections.emptyList())
                .build();

        return streamsRepository.save(KafkaStreamsApplicationEntity.fromDomain(app)).toDomain();
    }

    public KafkaStreamsApplication updateKafkaStreamsApplication(String id, KafkaStreamApplicationInput input) {
        KafkaStreamsApplicationEntity entity = streamsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Streams Application not found"));

        entity.setName(input.getName());
        entity.setApplicationId(input.getApplicationId());
        entity.setTopics(input.getTopics().toArray(new String[0]));
        entity.setConfiguration(input.getConfiguration());
        entity.setActive(input.isActive());

        // Note: State and threads are not updated here as they are live data
        return streamsRepository.save(entity).toDomain();
    }

    public boolean deleteKafkaStreamsApplication(String id) {
        if (streamsRepository.existsById(id)) {
            streamsRepository.deleteById(id);
            return true;
        }
        return false;
    }
}