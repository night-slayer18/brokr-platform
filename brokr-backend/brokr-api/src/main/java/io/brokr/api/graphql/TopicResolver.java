package io.brokr.api.graphql;

import io.brokr.api.input.TopicInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Topic;
import io.brokr.kafka.service.KafkaAdminService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class TopicResolver {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;
    private final AuthorizationService authorizationService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<Topic> topics(@Argument String clusterId) {
        KafkaCluster cluster = getCluster(clusterId);
        return kafkaAdminService.listTopics(cluster);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public Topic topic(@Argument String clusterId, @Argument String name) {
        KafkaCluster cluster = getCluster(clusterId);
        return kafkaAdminService.getTopic(cluster, name);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public Topic createTopic(@Argument String clusterId, @Argument TopicInput input) {
        KafkaCluster cluster = getCluster(clusterId);
        return kafkaAdminService.createTopic(
                cluster,
                input.getName(),
                input.getPartitions(),
                input.getReplicationFactor(),
                input.getConfigs()
        );
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public Topic updateTopic(@Argument String clusterId, @Argument String name, @Argument Map<String, String> configs) {
        KafkaCluster cluster = getCluster(clusterId);

        kafkaAdminService.updateTopicConfig(cluster, name, configs);

        // Return the updated topic
        return kafkaAdminService.getTopic(cluster, name);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public boolean deleteTopic(@Argument String clusterId, @Argument String name) {
        KafkaCluster cluster = getCluster(clusterId);
        kafkaAdminService.deleteTopic(cluster, name);
        return true;
    }

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }
}