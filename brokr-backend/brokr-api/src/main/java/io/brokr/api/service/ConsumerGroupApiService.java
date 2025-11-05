package io.brokr.api.service;

import io.brokr.api.input.ResetOffsetInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.ConsumerGroup;
import io.brokr.core.model.KafkaCluster;
import io.brokr.kafka.service.KafkaAdminService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsumerGroupApiService {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));
    }

    public List<ConsumerGroup> listConsumerGroups(String clusterId) {
        KafkaCluster cluster = getCluster(clusterId);
        List<ConsumerGroup> groups = kafkaAdminService.listConsumerGroups(cluster);

        // Add topic offsets to each group
        groups.forEach(group -> {
            Map<String, Long> topicOffsets = kafkaAdminService.getConsumerGroupOffsets(cluster, group.getGroupId());
            group.setTopicOffsets(topicOffsets);
        });

        return groups;
    }

    public ConsumerGroup getConsumerGroup(String clusterId, String groupId) {
        KafkaCluster cluster = getCluster(clusterId);
        return kafkaAdminService.getConsumerGroup(cluster, groupId)
                .map(group -> {
                    Map<String, Long> topicOffsets = kafkaAdminService.getConsumerGroupOffsets(cluster, groupId);
                    group.setTopicOffsets(topicOffsets);
                    return group;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Consumer group not found: " + groupId));
    }

    public boolean resetConsumerGroupOffset(String clusterId, String groupId, ResetOffsetInput input) {
        return kafkaAdminService.resetConsumerGroupOffset(
                getCluster(clusterId),
                groupId,
                input.getTopic(),
                input.getPartition(),
                input.getOffset()
        );
    }
}