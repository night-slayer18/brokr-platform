package io.brokr.api.graphql;

import io.brokr.api.input.KafkaStreamApplicationInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.KafkaStreamsApplication;
import io.brokr.core.model.StreamsState;
import io.brokr.kafka.service.KafkaStreamsService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.KafkaStreamsApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class KafkaStreamsResolver {

    private final KafkaStreamsApplicationRepository streamsRepository;
    private final AuthorizationService authorizationService;
    private final KafkaClusterRepository clusterRepository;

    // <<< FIX: Injected the new service >>>
    private final KafkaStreamsService kafkaStreamsService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaStreamsApplication> kafkaStreamsApplications(@Argument String clusterId) {
        // Fetch cluster once for all apps in this list
        KafkaCluster cluster = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));

        return streamsRepository.findByClusterId(clusterId).stream()
                .map(entity -> {
                    KafkaStreamsApplication app = entity.toDomain();

                    // <<< FIX: Replaced TODOs with calls to the service >>>
                    app.setState(kafkaStreamsService.getState(app, cluster));
                    app.setThreads(kafkaStreamsService.getThreads(app, cluster));
                    return app;
                })
                .collect(Collectors.toList());
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplication kafkaStreamsApplication(@Argument String id) {
        // Fetch the app from the database
        KafkaStreamsApplication app = streamsRepository.findById(id)
                .map(KafkaStreamsApplicationEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Kafka Streams Application not found"));

        // Fetch the cluster this app belongs to
        KafkaCluster cluster = clusterRepository.findById(app.getClusterId())
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found for this application"));

        // <<< FIX: Replaced TODOs with calls to the service >>>
        app.setState(kafkaStreamsService.getState(app, cluster));
        app.setThreads(kafkaStreamsService.getThreads(app, cluster));

        return app;
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public KafkaStreamsApplication createKafkaStreamsApplication(@Argument KafkaStreamApplicationInput input) {
        if (streamsRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new RuntimeException("Application with this name already exists in the cluster");
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

        return streamsRepository.save(KafkaStreamsApplicationEntity.fromDomain(app))
                .toDomain();
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplication updateKafkaStreamsApplication(@Argument String id, @Argument KafkaStreamApplicationInput input) {
        return streamsRepository.findById(id)
                .map(entity -> {
                    entity.setName(input.getName());
                    entity.setApplicationId(input.getApplicationId());
                    entity.setTopics(input.getTopics().toArray(new String[0]));
                    entity.setConfiguration(input.getConfiguration());
                    entity.setActive(input.isActive());

                    // Note: State and threads are not updated here as they are live data
                    return streamsRepository.save(entity).toDomain();
                })
                .orElseThrow(() -> new RuntimeException("Kafka Streams Application not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public boolean deleteKafkaStreamsApplication(@Argument String id) {
        if (streamsRepository.existsById(id)) {
            streamsRepository.deleteById(id);
            return true;
        }
        return false;
    }
}