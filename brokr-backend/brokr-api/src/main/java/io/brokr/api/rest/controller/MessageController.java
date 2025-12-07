package io.brokr.api.rest.controller;

import io.brokr.api.input.MessageInput;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Message;
import io.brokr.kafka.service.KafkaConsumerService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for reading messages from topics.
 * Thin wrapper around KafkaConsumerService - no service changes needed.
 */
@RestController
@RequestMapping("/api/v1/brokr/clusters/{clusterId}/messages")
@RequiredArgsConstructor
public class MessageController {
    
    private final KafkaClusterRepository clusterRepository;
    private final KafkaConsumerService kafkaConsumerService;
    
    @PostMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId) and @authorizationService.canReadMessages()")
    public List<Message> getMessages(
            @PathVariable String clusterId,
            @RequestBody MessageInput input) {
        KafkaCluster cluster = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found with id: " + clusterId));
        
        return kafkaConsumerService.consumeMessages(
                cluster,
                input.getTopic(),
                input.getPartitions(),
                input.getOffset(),
                input.getLimit()
        );
    }
}

