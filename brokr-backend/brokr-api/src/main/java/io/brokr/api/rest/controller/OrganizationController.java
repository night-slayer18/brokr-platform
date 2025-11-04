package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.exception.ValidationException;
import io.brokr.api.input.OrganizationInput;
import io.brokr.core.dto.EnvironmentDto;
import io.brokr.core.dto.OrganizationDto;
import io.brokr.core.model.Organization;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;

    @GetMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public List<OrganizationDto> getOrganizations() {

        // 1. Fetch all organizations (1 query)
        List<OrganizationDto> orgDtos = organizationRepository.findAll().stream()
                .map(OrganizationEntity::toDomain)
                .map(OrganizationDto::fromDomain)
                .collect(Collectors.toList());

        if (orgDtos.isEmpty()) {
            return orgDtos;
        }

        // 2. Get all their IDs
        List<String> orgIds = orgDtos.stream().map(OrganizationDto::getId).toList();

        // 3. Fetch all environments for ALL those orgs (1 query)
        Map<String, List<EnvironmentDto>> envsByOrgId = environmentRepository.findByOrganizationIdIn(orgIds)
                .stream()
                .map(EnvironmentEntity::toDomain)
                .map(EnvironmentDto::fromDomain)
                // 4. Group them by orgID in memory (fast)
                .collect(Collectors.groupingBy(EnvironmentDto::getOrganizationId));

        // 5. Attach the environment lists to their respective orgs
        orgDtos.forEach(org -> org.setEnvironments(
                envsByOrgId.getOrDefault(org.getId(), List.of())
        ));

        return orgDtos;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public OrganizationDto getOrganization(@PathVariable String id) {
        // 1. Get the Org
        OrganizationDto orgDto = organizationRepository.findById(id)
                .map(OrganizationEntity::toDomain)
                .map(OrganizationDto::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));

        // 2. Explicitly load its environments (1 query)
        List<EnvironmentDto> envs = environmentRepository.findByOrganizationId(id)
                .stream()
                .map(EnvironmentEntity::toDomain)
                .map(EnvironmentDto::fromDomain)
                .toList();

        orgDto.setEnvironments(envs);

        return orgDto;
    }

    @PostMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public ResponseEntity<OrganizationDto> createOrganization(@RequestBody OrganizationInput input) {
        if (organizationRepository.existsByName(input.getName())) {
            throw new ValidationException("Organization with this name already exists");
        }

        Organization org = Organization.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .description(input.getDescription())
                .isActive(input.isActive())
                .build();

        Organization savedOrg = organizationRepository.save(OrganizationEntity.fromDomain(org)).toDomain();
        // Return DTO with empty environments list, which is correct for a new org
        return new ResponseEntity<>(OrganizationDto.fromDomain(savedOrg), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public OrganizationDto updateOrganization(@PathVariable String id, @RequestBody OrganizationInput input) {
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));

        entity.setName(input.getName());
        entity.setDescription(input.getDescription());
        entity.setActive(input.isActive());

        Organization updatedOrg = organizationRepository.save(entity).toDomain();
        OrganizationDto dto = OrganizationDto.fromDomain(updatedOrg);

        // 2. Explicitly load environments for the response (1 query)
        List<EnvironmentDto> envs = environmentRepository.findByOrganizationId(id)
                .stream()
                .map(EnvironmentEntity::toDomain)
                .map(EnvironmentDto::fromDomain)
                .toList();

        dto.setEnvironments(envs);
        return dto;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public ResponseEntity<Void> deleteOrganization(@PathVariable String id) {
        if (!organizationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Organization not found with id: " + id);
        }
        organizationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}