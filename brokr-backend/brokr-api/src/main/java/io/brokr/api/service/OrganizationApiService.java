package io.brokr.api.service;

import io.brokr.api.input.OrganizationInput;
import io.brokr.api.input.OrganizationMfaPolicyInput;
import io.brokr.core.dto.EnvironmentDto;
import io.brokr.core.dto.OrganizationDto;
import io.brokr.core.exception.AccessDeniedException;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.Organization;
import io.brokr.core.model.Role;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.repository.ApiKeyRepository;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.OrganizationRepository;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brokr.core.model.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationApiService {

    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final KafkaClusterRepository kafkaClusterRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<Organization> listOrganizations() {
        return organizationRepository.findAll().stream()
                .map(OrganizationEntity::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Organization getOrganizationById(String id) {
        return organizationRepository.findById(id)
                .map(OrganizationEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Map<String, Organization> getOrganizationsByIds(List<String> ids) {
        return organizationRepository.findAllById(ids).stream()
                .map(OrganizationEntity::toDomain)
                .collect(Collectors.toMap(Organization::getId, java.util.function.Function.identity()));
    }


    @Transactional(readOnly = true)
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



    @Transactional
    public Organization createOrganization(OrganizationInput input) {
        if (organizationRepository.existsByName(input.getName())) {
            throw new ValidationException("Organization with this name already exists");
        }

        // Use Boolean wrapper and default to true if null (for backward compatibility)
        boolean isActive = input.getIsActive() != null ? input.getIsActive() : true;

        Organization org = Organization.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .description(input.getDescription())
                .isActive(isActive)
                .build();

        OrganizationEntity savedOrgEntity = organizationRepository.save(OrganizationEntity.fromDomain(org));
        
        List<Environment> createdEnvironments = new ArrayList<>();
        
        if (input.getInitialEnvironments() != null && !input.getInitialEnvironments().isEmpty()) {
            List<EnvironmentEntity> envEntities = input.getInitialEnvironments().stream()
                    .map(envInput -> EnvironmentEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .name(envInput.getName())
                            .type(envInput.getType())
                            .description(envInput.getDescription())
                            .isActive(true)
                            .organization(savedOrgEntity)
                            .build())
                    .toList();
            
            environmentRepository.saveAll(envEntities);
            createdEnvironments = envEntities.stream().map(EnvironmentEntity::toDomain).toList();
        }
        
        Organization result = savedOrgEntity.toDomain();
        result.setEnvironments(createdEnvironments);
        return result;
    }

    @Transactional
    public Organization updateOrganization(String id, OrganizationInput input) {
        var currentUser = authorizationService.getCurrentUser();
        
        // ADMIN can only update their own organization
        if (currentUser.getRole() == Role.ADMIN) {
            if (!currentUser.getOrganizationId().equals(id)) {
                throw new AccessDeniedException("ADMIN can only update their own organization");
            }
        }
        
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));

        entity.setName(input.getName());
        entity.setDescription(input.getDescription());
        
        // Use Boolean wrapper and default to current value if null (for backward compatibility)
        boolean isActive = input.getIsActive() != null ? input.getIsActive() : entity.isActive();
        entity.setActive(isActive);

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

    @Transactional
    public boolean deleteOrganization(String id) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));

        // Check for dependent entities that prevent deletion
        List<String> blockingEntities = new java.util.ArrayList<>();
        
        // Check for users
        long userCount = userRepository.findByOrganizationId(id).size();
        if (userCount > 0) {
            blockingEntities.add(String.format("%d user(s)", userCount));
        }
        
        // Check for API keys
        long apiKeyCount = apiKeyRepository.findByOrganizationId(id, PageRequest.of(0, 1)).getTotalElements();
        if (apiKeyCount > 0) {
            blockingEntities.add(String.format("%d API key(s)", apiKeyCount));
        }
        
        // Check for Kafka clusters
        long clusterCount = kafkaClusterRepository.findByOrganizationId(id).size();
        if (clusterCount > 0) {
            blockingEntities.add(String.format("%d Kafka cluster(s)", clusterCount));
        }
        
        // If there are blocking entities, throw a validation exception
        if (!blockingEntities.isEmpty()) {
            String message = String.format(
                "Cannot delete organization '%s' because it still has %s. " +
                "Please delete all associated users, API keys, and clusters before deleting the organization.",
                organization.getName(),
                String.join(", ", blockingEntities)
            );
            throw new ValidationException(message);
        }
        
        // All checks passed, safe to delete
        // Environments will be cascade deleted automatically
        organizationRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Organization updateOrganizationMfaPolicy(String id, OrganizationMfaPolicyInput input) {
        var currentUser = authorizationService.getCurrentUser();
        
        // ADMIN can only update their own organization
        if (currentUser.getRole() == Role.ADMIN) {
            if (!currentUser.getOrganizationId().equals(id)) {
                throw new AccessDeniedException("ADMIN can only update their own organization");
            }
        }
        
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));

        if (input.getMfaRequired() != null) {
            boolean wasRequired = entity.isMfaRequired();
            entity.setMfaRequired(input.getMfaRequired());
            
            // Set timestamp when MFA is first required (for grace period calculation)
            if (input.getMfaRequired() && !wasRequired) {
                entity.setMfaRequiredSince(java.time.LocalDateTime.now());
            } else if (!input.getMfaRequired()) {
                // Clear timestamp when MFA requirement is removed
                entity.setMfaRequiredSince(null);
            }
        }
        if (input.getMfaGracePeriodDays() != null) {
            entity.setMfaGracePeriodDays(input.getMfaGracePeriodDays());
        }

        return organizationRepository.save(entity).toDomain();
    }
}