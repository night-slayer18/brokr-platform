package io.brokr.api.service;

import io.brokr.api.input.TopicInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Topic;
import io.brokr.kafka.service.KafkaAdminService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicApiService {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));
    }

    public List<Topic> listTopics(String clusterId) {
        return kafkaAdminService.listTopics(getCluster(clusterId));
    }

    public org.springframework.data.domain.Page<Topic> getTopicsPaginated(String clusterId, int page, int size, String search) {
        return kafkaAdminService.listTopicsPaginated(getCluster(clusterId), page, size, search);
    }

    public Topic getTopic(String clusterId, String name) {
        return kafkaAdminService.getTopic(getCluster(clusterId), name);
    }

    public Topic createTopic(String clusterId, TopicInput input) {
        return kafkaAdminService.createTopic(
                getCluster(clusterId),
                input.getName(),
                input.getPartitions(),
                input.getReplicationFactor(),
                input.getConfigs()
        );
    }

    public Topic updateTopicConfig(String clusterId, String name, Map<String, String> configs) {
        KafkaCluster cluster = getCluster(clusterId);
        kafkaAdminService.updateTopicConfig(cluster, name, configs);
        // Return the updated topic
        return kafkaAdminService.getTopic(cluster, name);
    }

    public boolean deleteTopic(String clusterId, String name) {
        kafkaAdminService.deleteTopic(getCluster(clusterId), name);
        return true;
    }
}