package io.brokr.api.service;

import io.brokr.api.input.SchemaRegistryInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.SchemaRegistry;
import io.brokr.kafka.service.SchemaRegistryService;
import io.brokr.storage.entity.SchemaRegistryEntity;
import io.brokr.storage.repository.SchemaRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaRegistryApiService {

    private final SchemaRegistryRepository schemaRegistryRepository;
    private final SchemaRegistryService schemaRegistryService;

    private SchemaRegistry getSchemaRegistryInternal(String id) {
        return schemaRegistryRepository.findById(id)
                .map(SchemaRegistryEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Schema Registry not found with id: " + id));
    }

    public List<SchemaRegistry> listSchemaRegistries(String clusterId) {
        return schemaRegistryRepository.findByClusterId(clusterId).stream()
                .map(SchemaRegistryEntity::toDomain)
                .collect(Collectors.toList());
    }

    public SchemaRegistry getSchemaRegistryById(String id) {
        return getSchemaRegistryInternal(id);
    }

    public SchemaRegistry createSchemaRegistry(SchemaRegistryInput input) {
        if (schemaRegistryRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new ValidationException("Schema Registry with this name already exists in the cluster");
        }

        SchemaRegistry schemaRegistry = SchemaRegistry.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .url(input.getUrl())
                .clusterId(input.getClusterId())
                .securityProtocol(input.getSecurityProtocol())
                .username(input.getUsername())
                .password(input.getPassword())
                .isActive(input.isActive())
                .build();

        boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);
        if (!isReachable) {
            throw new ValidationException("Failed to connect to the Schema Registry. Please check the URL and credentials.");
        }
        schemaRegistry.setReachable(isReachable);

        return schemaRegistryRepository.save(SchemaRegistryEntity.fromDomain(schemaRegistry)).toDomain();
    }

    public SchemaRegistry updateSchemaRegistry(String id, SchemaRegistryInput input) {
        SchemaRegistryEntity entity = schemaRegistryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schema Registry not found with id: " + id));

        entity.setName(input.getName());
        entity.setUrl(input.getUrl());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setUsername(input.getUsername());
        entity.setPassword(input.getPassword());
        entity.setActive(input.isActive());

        SchemaRegistry schemaRegistry = entity.toDomain();
        boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);
        schemaRegistry.setReachable(isReachable);

        return schemaRegistryRepository.save(SchemaRegistryEntity.fromDomain(schemaRegistry)).toDomain();
    }

    public boolean deleteSchemaRegistry(String id) {
        if (!schemaRegistryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schema Registry not found with id: " + id);
        }
        schemaRegistryRepository.deleteById(id);
        return true;
    }

    public boolean testSchemaRegistryConnection(String id) {
        SchemaRegistryEntity entity = schemaRegistryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schema Registry not found"));

        SchemaRegistry schemaRegistry = entity.toDomain();
        boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);

        entity.setReachable(isReachable);
        entity.setLastConnectionCheck(System.currentTimeMillis());
        entity.setLastConnectionError(isReachable ? null : "Connection failed");
        schemaRegistryRepository.save(entity);
        return isReachable;
    }

    public List<String> getSchemaRegistrySubjects(String id) {
        SchemaRegistry schemaRegistry = getSchemaRegistryInternal(id);
        return schemaRegistryService.getSubjects(schemaRegistry);
    }

    public String getSchemaRegistryLatestSchema(String id, String subject) {
        SchemaRegistry schemaRegistry = getSchemaRegistryInternal(id);
        String schema = schemaRegistryService.getLatestSchema(schemaRegistry, subject);
        if (schema == null) {
            throw new ResourceNotFoundException("Schema subject not found: " + subject);
        }
        return schema;
    }

    public List<Integer> getSchemaRegistrySchemaVersions(String id, String subject) {
        SchemaRegistry schemaRegistry = getSchemaRegistryInternal(id);
        List<Integer> versions = schemaRegistryService.getSchemaVersions(schemaRegistry, subject);
        if (versions.isEmpty()) {
            throw new ResourceNotFoundException("Schema subject not found: " + subject);
        }
        return versions;
    }
}