package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.exception.ValidationException;
import io.brokr.api.input.SchemaRegistryInput;
import io.brokr.core.dto.SchemaRegistryDto;
import io.brokr.core.model.SchemaRegistry;
import io.brokr.kafka.service.SchemaRegistryService;
import io.brokr.storage.entity.SchemaRegistryEntity;
import io.brokr.storage.repository.SchemaRegistryRepository;
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
public class SchemaRegistryController {

    private final SchemaRegistryRepository schemaRegistryRepository;
    private final SchemaRegistryService schemaRegistryService;

    // <<< FIX: Added helper method >>>
    private SchemaRegistry getSchemaRegistryInternal(String id) {
        return schemaRegistryRepository.findById(id)
                .map(SchemaRegistryEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Schema Registry not found with id: " + id));
    }

    @GetMapping("/clusters/{clusterId}/schema-registries")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<SchemaRegistryDto> getSchemaRegistries(@PathVariable String clusterId) {
        return schemaRegistryRepository.findByClusterId(clusterId).stream()
                .map(SchemaRegistryEntity::toDomain)
                .map(SchemaRegistryDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/schema-registries/{id}")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public SchemaRegistryDto getSchemaRegistry(@PathVariable String id) {
        return SchemaRegistryDto.fromDomain(getSchemaRegistryInternal(id));
    }

    @PostMapping("/schema-registries")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<SchemaRegistryDto> createSchemaRegistry(@RequestBody SchemaRegistryInput input) {
        if (schemaRegistryRepository.existsByNameAndClusterId(input.getName(), input.getClusterId())) {
            throw new ValidationException("Schema Registry with this name already exists in the cluster");
        }

        // Logic from SchemaRegistryResolver
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

        SchemaRegistry saved = schemaRegistryRepository.save(SchemaRegistryEntity.fromDomain(schemaRegistry)).toDomain();
        return new ResponseEntity<>(SchemaRegistryDto.fromDomain(saved), HttpStatus.CREATED);
    }

    @PutMapping("/schema-registries/{id}")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public SchemaRegistryDto updateSchemaRegistry(@PathVariable String id, @RequestBody SchemaRegistryInput input) {
        SchemaRegistryEntity entity = schemaRegistryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schema Registry not found with id: " + id));

        // Logic from SchemaRegistryResolver
        entity.setName(input.getName());
        entity.setUrl(input.getUrl());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setUsername(input.getUsername());
        entity.setPassword(input.getPassword());
        entity.setActive(input.isActive());

        SchemaRegistry schemaRegistry = entity.toDomain();
        boolean isReachable = schemaRegistryService.testConnection(schemaRegistry);
        schemaRegistry.setReachable(isReachable);

        SchemaRegistry updated = schemaRegistryRepository.save(SchemaRegistryEntity.fromDomain(schemaRegistry)).toDomain();
        return SchemaRegistryDto.fromDomain(updated);
    }

    @DeleteMapping("/schema-registries/{id}")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public ResponseEntity<Void> deleteSchemaRegistry(@PathVariable String id) {
        if (!schemaRegistryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schema Registry not found with id: " + id);
        }
        schemaRegistryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // <<< FIX: Implemented omitted endpoints >>>

    @GetMapping("/schema-registries/{id}/subjects")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public List<String> getSchemaRegistrySubjects(@PathVariable String id) {
        SchemaRegistry schemaRegistry = getSchemaRegistryInternal(id);
        return schemaRegistryService.getSubjects(schemaRegistry);
    }

    @GetMapping("/schema-registries/{id}/subjects/{subject}/versions/latest")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public String getSchemaRegistryLatestSchema(@PathVariable String id, @PathVariable String subject) {
        SchemaRegistry schemaRegistry = getSchemaRegistryInternal(id);
        String schema = schemaRegistryService.getLatestSchema(schemaRegistry, subject);
        if (schema == null) {
            throw new ResourceNotFoundException("Schema subject not found: " + subject);
        }
        return schema;
    }

    @GetMapping("/schema-registries/{id}/subjects/{subject}/versions")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public List<Integer> getSchemaRegistrySchemaVersions(@PathVariable String id, @PathVariable String subject) {
        SchemaRegistry schemaRegistry = getSchemaRegistryInternal(id);
        List<Integer> versions = schemaRegistryService.getSchemaVersions(schemaRegistry, subject);
        if (versions.isEmpty()) {
            throw new ResourceNotFoundException("Schema subject not found: " + subject);
        }
        return versions;
    }
}