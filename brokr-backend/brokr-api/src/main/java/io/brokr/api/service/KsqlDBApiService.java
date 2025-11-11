package io.brokr.api.service;

import io.brokr.api.input.KsqlDBInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.KsqlDB;
import io.brokr.kafka.service.KsqlDBService;
import io.brokr.storage.entity.KsqlDBEntity;
import io.brokr.storage.repository.KsqlDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KsqlDBApiService {

    private final KsqlDBRepository ksqlDBRepository;
    private final KsqlDBService ksqlDBService;

    private KsqlDB getKsqlDBInternal(String id) {
        return ksqlDBRepository.findById(id)
                .map(KsqlDBEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("ksqlDB instance not found with id: " + id));
    }

    public List<KsqlDB> listKsqlDBs(String clusterId) {
        return ksqlDBRepository.findByClusterId(clusterId).stream()
                .map(KsqlDBEntity::toDomain)
                .collect(Collectors.toList());
    }

    public Map<String, List<KsqlDB>> getKsqlDBsForClusters(List<String> clusterIds) {
        return ksqlDBRepository.findByClusterIdIn(clusterIds).stream()
                .map(KsqlDBEntity::toDomain)
                .collect(Collectors.groupingBy(KsqlDB::getClusterId));
    }

    public KsqlDB getKsqlDBById(String id) {
        return getKsqlDBInternal(id);
    }

    public KsqlDB createKsqlDB(KsqlDBInput input) {
        if (ksqlDBRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new ValidationException("ksqlDB instance with this name already exists in the cluster");
        }

        KsqlDB ksqlDB = KsqlDB.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .url(input.getUrl())
                .clusterId(input.getClusterId())
                .securityProtocol(input.getSecurityProtocol())
                .username(input.getUsername())
                .password(input.getPassword())
                .isActive(input.isActive())
                .build();

        boolean isReachable = ksqlDBService.testConnection(ksqlDB);
        if (!isReachable) {
            throw new ValidationException("Failed to connect to ksqlDB. Please check the URL and credentials.");
        }
        ksqlDB.setReachable(isReachable);

        return ksqlDBRepository.save(KsqlDBEntity.fromDomain(ksqlDB)).toDomain();
    }

    public KsqlDB updateKsqlDB(String id, KsqlDBInput input) {
        KsqlDBEntity entity = ksqlDBRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ksqlDB instance not found with id: " + id));

        entity.setName(input.getName());
        entity.setUrl(input.getUrl());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setUsername(input.getUsername());
        entity.setPassword(input.getPassword());
        entity.setActive(input.isActive());

        KsqlDB ksqlDB = entity.toDomain();
        boolean isReachable = ksqlDBService.testConnection(ksqlDB);
        ksqlDB.setReachable(isReachable);

        return ksqlDBRepository.save(KsqlDBEntity.fromDomain(ksqlDB)).toDomain();
    }

    public boolean deleteKsqlDB(String id) {
        if (!ksqlDBRepository.existsById(id)) {
            throw new ResourceNotFoundException("ksqlDB instance not found with id: " + id);
        }
        ksqlDBRepository.deleteById(id);
        return true;
    }

    public boolean testKsqlDBConnection(String id) {
        KsqlDBEntity entity = ksqlDBRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ksqlDB instance not found"));

        KsqlDB ksqlDB = entity.toDomain();
        boolean isReachable = ksqlDBService.testConnection(ksqlDB);

        entity.setReachable(isReachable);
        entity.setLastConnectionCheck(System.currentTimeMillis());
        entity.setLastConnectionError(isReachable ? null : "Connection failed");
        ksqlDBRepository.save(entity);
        return isReachable;
    }

    public String getKsqlDBServerInfo(String id) {
        KsqlDB ksqlDB = getKsqlDBInternal(id);
        String info = ksqlDBService.getServerInfo(ksqlDB);
        if (info == null) {
            throw new ResourceNotFoundException("Failed to retrieve server info from ksqlDB");
        }
        return info;
    }
}

