package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.exception.ValidationException;
import io.brokr.core.dto.ConsumerGroupDto;
import io.brokr.core.model.ConsumerGroup;
import io.brokr.core.model.KafkaCluster;
import io.brokr.kafka.service.KafkaAdminService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/consumer-groups")
@RequiredArgsConstructor
public class ConsumerGroupController {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaAdminService kafkaAdminService;

    private KafkaCluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ConsumerGroupDto> getConsumerGroups(@PathVariable String clusterId) {
        KafkaCluster cluster = getCluster(clusterId);
        List<ConsumerGroup> groups = kafkaAdminService.listConsumerGroups(cluster);

        // Logic from ConsumerGroupResolver
        return groups.stream()
                .peek(group -> {
                    Map<String, Long> offsets = kafkaAdminService.getConsumerGroupOffsets(cluster, group.getGroupId());
                    group.setTopicOffsets(offsets);
                })
                .map(ConsumerGroupDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public ConsumerGroupDto getConsumerGroup(@PathVariable String clusterId, @PathVariable String groupId) {
        KafkaCluster cluster = getCluster(clusterId);

        return kafkaAdminService.getConsumerGroup(cluster, groupId)
                .map(group -> {
                    Map<String, Long> offsets = kafkaAdminService.getConsumerGroupOffsets(cluster, groupId);
                    group.setTopicOffsets(offsets);
                    return ConsumerGroupDto.fromDomain(group);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Consumer group not found: " + groupId));
    }

    @PostMapping("/{groupId}/reset-offset")
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public ResponseEntity<Boolean> resetOffset(
            @PathVariable String clusterId,
            @PathVariable String groupId,
            @RequestBody Map<String, Object> payload) {

        // <<< FIX: Implemented TODO >>>
        try {
            String topic = (String) payload.get("topic");
            int partition = (Integer) payload.get("partition");
            long offset = ((Number) payload.get("offset")).longValue();

            if (topic == null || topic.isBlank()) {
                throw new ValidationException("Field 'topic' is required");
            }

            boolean success = kafkaAdminService.resetConsumerGroupOffset(getCluster(clusterId), groupId, topic, partition, offset);
            return ResponseEntity.ok(success);

        } catch (ClassCastException | NullPointerException e) {
            throw new ValidationException("Invalid payload. Required fields: 'topic' (String), 'partition' (int), 'offset' (long)");
        }
    }
}