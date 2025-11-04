package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.input.EnvironmentInput;
import io.brokr.core.dto.EnvironmentDto;
import io.brokr.core.model.Environment;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.EnvironmentType;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentRepository environmentRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthorizationService authorizationService;

    @GetMapping("/organizations/{orgId}/environments")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#orgId)")
    public List<EnvironmentDto> getEnvironments(@PathVariable String orgId) {
        User currentUser = authorizationService.getCurrentUser();
        List<EnvironmentEntity> environments = environmentRepository.findByOrganizationId(orgId);

        Stream<EnvironmentEntity> stream = environments.stream();
        if (currentUser.getRole() != io.brokr.core.model.Role.SUPER_ADMIN) {
            stream = stream.filter(e -> currentUser.getAccessibleEnvironmentIds().contains(e.getId()));
        }

        return stream
                .map(EnvironmentEntity::toDomain)
                .map(EnvironmentDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/environments/{id}")
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public EnvironmentDto getEnvironment(@PathVariable String id) {
        return environmentRepository.findById(id)
                .map(EnvironmentEntity::toDomain)
                .map(EnvironmentDto::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
    }

    @PostMapping("/environments")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#input.organizationId)")
    public ResponseEntity<EnvironmentDto> createEnvironment(@RequestBody EnvironmentInput input) {
        if (!organizationRepository.existsById(input.getOrganizationId())) {
            throw new ResourceNotFoundException("Organization not found with id: " + input.getOrganizationId());
        }

        Environment env = Environment.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .type(input.getType().name())
                .description(input.getDescription())
                .isActive(input.isActive())
                .build();

        EnvironmentEntity entity = EnvironmentEntity.fromDomain(env);
        entity.setOrganization(organizationRepository.findById(input.getOrganizationId()).get());

        Environment savedEnv = environmentRepository.save(entity).toDomain();
        return new ResponseEntity<>(EnvironmentDto.fromDomain(savedEnv), HttpStatus.CREATED);
    }

    @PutMapping("/environments/{id}")
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public EnvironmentDto updateEnvironment(@PathVariable String id, @RequestBody EnvironmentInput input) {
        EnvironmentEntity entity = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));

        entity.setName(input.getName());
        entity.setType(EnvironmentType.valueOf(input.getType().name()));
        entity.setDescription(input.getDescription());
        entity.setActive(input.isActive());

        Environment updatedEnv = environmentRepository.save(entity).toDomain();
        return EnvironmentDto.fromDomain(updatedEnv);
    }

    @DeleteMapping("/environments/{id}")
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable String id) {
        if (!environmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Environment not found with id: " + id);
        }
        environmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}