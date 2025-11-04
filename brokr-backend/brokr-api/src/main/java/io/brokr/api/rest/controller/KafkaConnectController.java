package io.brokr.api.rest.controller;

import io.brokr.api.input.KafkaConnectInput;
import io.brokr.core.dto.KafkaConnectDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.KafkaConnect;
import io.brokr.kafka.service.KafkaConnectService;
import io.brokr.storage.entity.KafkaConnectEntity;
import io.brokr.storage.repository.KafkaConnectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KafkaConnectController {

    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaConnectService kafkaConnectService;

    @GetMapping("/clusters/{clusterId}/kafka-connects")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaConnectDto> getKafkaConnects(@PathVariable String clusterId) {
        return kafkaConnectRepository.findByClusterId(clusterId).stream()
                .map(KafkaConnectEntity::toDomain)
                .map(KafkaConnectDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/kafka-connects/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnectDto getKafkaConnect(@PathVariable String id) {
        return kafkaConnectRepository.findById(id)
                .map(KafkaConnectEntity::toDomain)
                .map(KafkaConnectDto::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Connect not found with id: " + id));
    }

    @PostMapping("/kafka-connects")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<KafkaConnectDto> createKafkaConnect(@RequestBody KafkaConnectInput input) {
        if (kafkaConnectRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new ValidationException("Kafka Connect with this name already exists in the cluster");
        }

        // Logic from KafkaConnectResolver
        KafkaConnect kafkaConnect = KafkaConnect.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .url(input.getUrl())
                .clusterId(input.getClusterId())
                .securityProtocol(input.getSecurityProtocol())
                .username(input.getUsername())
                .password(input.getPassword())
                .isActive(input.isActive())
                .build();

        boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);
        if (!isReachable) {
            throw new ValidationException("Failed to connect to Kafka Connect. Please check the URL and credentials.");
        }
        kafkaConnect.setReachable(isReachable);

        KafkaConnect saved = kafkaConnectRepository.save(KafkaConnectEntity.fromDomain(kafkaConnect)).toDomain();
        return new ResponseEntity<>(KafkaConnectDto.fromDomain(saved), HttpStatus.CREATED);
    }

    @PutMapping("/kafka-connects/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnectDto updateKafkaConnect(@PathVariable String id, @RequestBody KafkaConnectInput input) {
        KafkaConnectEntity entity = kafkaConnectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Connect not found with id: " + id));

        // Logic from KafkaConnectResolver
        entity.setName(input.getName());
        entity.setUrl(input.getUrl());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setUsername(input.getUsername());
        entity.setPassword(input.getPassword());
        entity.setActive(input.isActive());

        KafkaConnect kafkaConnect = entity.toDomain();
        boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);
        kafkaConnect.setReachable(isReachable);

        KafkaConnect updated = kafkaConnectRepository.save(KafkaConnectEntity.fromDomain(kafkaConnect)).toDomain();
        return KafkaConnectDto.fromDomain(updated);
    }

    @DeleteMapping("/kafka-connects/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public ResponseEntity<Void> deleteKafkaConnect(@PathVariable String id) {
        if (!kafkaConnectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Kafka Connect not found with id: " + id);
        }
        kafkaConnectRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}