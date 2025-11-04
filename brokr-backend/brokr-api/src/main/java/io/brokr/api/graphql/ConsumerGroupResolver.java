package io.brokr.api.graphql;

import io.brokr.core.model.ConsumerGroup;
import io.brokr.core.model.KafkaCluster;
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
public class ConsumerGroupResolver {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;
    private final AuthorizationService authorizationService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ConsumerGroup> consumerGroups(@Argument String clusterId) {
        KafkaCluster cluster = getCluster(clusterId);
        List<ConsumerGroup> groups = kafkaAdminService.listConsumerGroups(cluster);

        // Add topic offsets to each group
        groups.forEach(group -> {
            Map<String, Long> topicOffsets = kafkaAdminService.getConsumerGroupOffsets(cluster, group.getGroupId());
            group.setTopicOffsets(topicOffsets);
        });

        return groups;
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public ConsumerGroup consumerGroup(@Argument String clusterId, @Argument String groupId) {
        KafkaCluster cluster = getCluster(clusterId);
        List<ConsumerGroup> groups = kafkaAdminService.listConsumerGroups(cluster);

        return groups.stream()
                .filter(group -> group.getGroupId().equals(groupId))
                .findFirst()
                .map(group -> {
                    Map<String, Long> topicOffsets = kafkaAdminService.getConsumerGroupOffsets(cluster, groupId);
                    group.setTopicOffsets(topicOffsets);
                    return group;
                })
                .orElseThrow(() -> new RuntimeException("Consumer group not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public boolean resetConsumerGroupOffset(@Argument String clusterId, @Argument String groupId,
                                            @Argument String topic, @Argument int partition, @Argument long offset) {
        KafkaCluster cluster = getCluster(clusterId);

        return kafkaAdminService.resetConsumerGroupOffset(cluster, groupId, topic, partition, offset);
    }

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }
}