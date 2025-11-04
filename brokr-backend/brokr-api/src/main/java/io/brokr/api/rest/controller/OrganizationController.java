package io.brokr.api.rest.controller;

import io.brokr.api.exception.ResourceNotFoundException;
import io.brokr.api.exception.ValidationException;
import io.brokr.api.input.OrganizationInput;
import io.brokr.core.dto.OrganizationDto;
import io.brokr.core.model.Organization;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;

    @GetMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public List<OrganizationDto> getOrganizations() {
        return organizationRepository.findAll().stream()
                .map(OrganizationEntity::toDomain)
                .map(OrganizationDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public OrganizationDto getOrganization(@PathVariable String id) {
        return organizationRepository.findById(id)
                .map(OrganizationEntity::toDomain)
                .map(OrganizationDto::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));
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
        return OrganizationDto.fromDomain(updatedOrg);
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