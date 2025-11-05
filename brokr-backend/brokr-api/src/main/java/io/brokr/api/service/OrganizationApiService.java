package io.brokr.api.service;

import io.brokr.api.input.OrganizationInput;
import io.brokr.core.dto.EnvironmentDto;
import io.brokr.core.dto.OrganizationDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.Organization;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationApiService {

    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;

    public List<Organization> listOrganizations() {
        return organizationRepository.findAll().stream()
                .map(OrganizationEntity::toDomain)
                .toList();
    }

    public List<OrganizationDto> listOrganizationsWithEnvironments() {
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

    public Organization getOrganizationById(String id) {
        return organizationRepository.findById(id)
                .map(OrganizationEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));
    }

    public OrganizationDto getOrganizationDtoById(String id) {
        // 1. Get the Org
        OrganizationDto orgDto = getOrganizationById(id).toDto();

        // 2. Explicitly load its environments (1 query)
        List<EnvironmentDto> envs = environmentRepository.findByOrganizationId(id)
                .stream()
                .map(EnvironmentEntity::toDomain)
                .map(EnvironmentDto::fromDomain)
                .toList();

        orgDto.setEnvironments(envs);

        return orgDto;
    }

    public Organization createOrganization(OrganizationInput input) {
        if (organizationRepository.existsByName(input.getName())) {
            throw new ValidationException("Organization with this name already exists");
        }

        Organization org = Organization.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .description(input.getDescription())
                .isActive(input.isActive())
                .build();

        return organizationRepository.save(OrganizationEntity.fromDomain(org)).toDomain();
    }

    public Organization updateOrganization(String id, OrganizationInput input) {
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));

        entity.setName(input.getName());
        entity.setDescription(input.getDescription());
        entity.setActive(input.isActive());

        return organizationRepository.save(entity).toDomain();
    }

    public OrganizationDto updateAndGetOrganizationDto(String id, OrganizationInput input) {
        // 1. Update the org
        Organization updatedOrg = updateOrganization(id, input);
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

    public boolean deleteOrganization(String id) {
        if (organizationRepository.existsById(id)) {
            organizationRepository.deleteById(id);
            return true;
        }
        // Throw exception if not found, consistent with other methods
        throw new ResourceNotFoundException("Organization not found with id: " + id);
    }
}