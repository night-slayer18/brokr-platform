package io.brokr.api.rest.controller;

import io.brokr.api.input.SchemaRegistryInput;
import io.brokr.api.service.SchemaRegistryApiService;
import io.brokr.core.dto.SchemaRegistryDto;
import io.brokr.core.model.SchemaRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/brokr")
@RequiredArgsConstructor
public class SchemaRegistryController {

    private final SchemaRegistryApiService schemaRegistryApiService;

    @GetMapping("/clusters/{clusterId}/schema-registries")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<SchemaRegistryDto> getSchemaRegistries(@PathVariable String clusterId) {
        return schemaRegistryApiService.listSchemaRegistries(clusterId).stream()
                .map(SchemaRegistryDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/schema-registries/{id}")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public SchemaRegistryDto getSchemaRegistry(@PathVariable String id) {
        return SchemaRegistryDto.fromDomain(schemaRegistryApiService.getSchemaRegistryById(id));
    }

    @PostMapping("/schema-registries")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<SchemaRegistryDto> createSchemaRegistry(@RequestBody SchemaRegistryInput input) {
        SchemaRegistry saved = schemaRegistryApiService.createSchemaRegistry(input);
        return new ResponseEntity<>(SchemaRegistryDto.fromDomain(saved), HttpStatus.CREATED);
    }

    @PutMapping("/schema-registries/{id}")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public SchemaRegistryDto updateSchemaRegistry(@PathVariable String id, @RequestBody SchemaRegistryInput input) {
        SchemaRegistry updated = schemaRegistryApiService.updateSchemaRegistry(id, input);
        return SchemaRegistryDto.fromDomain(updated);
    }

    @DeleteMapping("/schema-registries/{id}")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public ResponseEntity<Void> deleteSchemaRegistry(@PathVariable String id) {
        schemaRegistryApiService.deleteSchemaRegistry(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/schema-registries/{id}/subjects")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public List<String> getSchemaRegistrySubjects(@PathVariable String id) {
        return schemaRegistryApiService.getSchemaRegistrySubjects(id);
    }

    @GetMapping("/schema-registries/{id}/subjects/{subject}/versions/latest")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public String getSchemaRegistryLatestSchema(@PathVariable String id, @PathVariable String subject) {
        return schemaRegistryApiService.getSchemaRegistryLatestSchema(id, subject);
    }

    @GetMapping("/schema-registries/{id}/subjects/{subject}/versions")
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public List<Integer> getSchemaRegistrySchemaVersions(@PathVariable String id, @PathVariable String subject) {
        return schemaRegistryApiService.getSchemaRegistrySchemaVersions(id, subject);
    }
}