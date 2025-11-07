package io.brokr.api.service;

import io.brokr.api.input.KafkaConnectInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.KafkaConnect;
import io.brokr.kafka.service.KafkaConnectService;
import io.brokr.storage.entity.KafkaConnectEntity;
import io.brokr.storage.repository.KafkaConnectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KafkaConnectApiService {

    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaConnectService kafkaConnectService;

    public List<KafkaConnect> listKafkaConnects(String clusterId) {
        return kafkaConnectRepository.findByClusterId(clusterId).stream()
                .map(KafkaConnectEntity::toDomain)
                .collect(Collectors.toList());
    }

    public Map<String, List<KafkaConnect>> getKafkaConnectsForClusters(List<String> clusterIds) {
        return kafkaConnectRepository.findByClusterIdIn(clusterIds).stream()
                .map(KafkaConnectEntity::toDomain)
                .collect(Collectors.groupingBy(KafkaConnect::getClusterId));
    }

    public KafkaConnect getKafkaConnectById(String id) {
        return kafkaConnectRepository.findById(id)
                .map(KafkaConnectEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Connect not found with id: " + id));
    }

    public KafkaConnect createKafkaConnect(KafkaConnectInput input) {
        if (kafkaConnectRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new ValidationException("Kafka Connect with this name already exists in the cluster");
        }

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

        return kafkaConnectRepository.save(KafkaConnectEntity.fromDomain(kafkaConnect)).toDomain();
    }

    public KafkaConnect updateKafkaConnect(String id, KafkaConnectInput input) {
        KafkaConnectEntity entity = kafkaConnectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Connect not found with id: " + id));

        entity.setName(input.getName());
        entity.setUrl(input.getUrl());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setUsername(input.getUsername());
        entity.setPassword(input.getPassword());
        entity.setActive(input.isActive());

        KafkaConnect kafkaConnect = entity.toDomain();
        boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);
        kafkaConnect.setReachable(isReachable);

        return kafkaConnectRepository.save(KafkaConnectEntity.fromDomain(kafkaConnect)).toDomain();
    }

    public boolean deleteKafkaConnect(String id) {
        if (!kafkaConnectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Kafka Connect not found with id: " + id);
        }
        kafkaConnectRepository.deleteById(id);
        return true;
    }

    public boolean testKafkaConnectConnection(String id) {
        KafkaConnectEntity entity = kafkaConnectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kafka Connect not found"));

        KafkaConnect kafkaConnect = entity.toDomain();
        boolean isReachable = kafkaConnectService.testConnection(kafkaConnect);

        entity.setReachable(isReachable);
        entity.setLastConnectionCheck(System.currentTimeMillis());
        entity.setLastConnectionError(isReachable ? null : "Connection failed");
        kafkaConnectRepository.save(entity);
        return isReachable;
    }
}