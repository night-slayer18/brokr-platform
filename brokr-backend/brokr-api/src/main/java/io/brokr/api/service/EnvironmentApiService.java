package io.brokr.api.service;

import io.brokr.api.input.EnvironmentInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.Environment;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.EnvironmentType;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EnvironmentApiService {

    private final EnvironmentRepository environmentRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthorizationService authorizationService;

    public List<Environment> listEnvironments(String organizationId) {
        User currentUser = authorizationService.getCurrentUser();
        List<EnvironmentEntity> environments = environmentRepository.findByOrganizationId(organizationId);

        Stream<EnvironmentEntity> stream = environments.stream();
        if (currentUser.getRole() != io.brokr.core.model.Role.SUPER_ADMIN) {
            stream = stream.filter(e -> currentUser.getAccessibleEnvironmentIds().contains(e.getId()));
        }

        return stream
                .map(EnvironmentEntity::toDomain)
                .collect(Collectors.toList());
    }

    public Environment getEnvironmentById(String id) {
        return environmentRepository.findById(id)
                .map(EnvironmentEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));
    }

    public Environment createEnvironment(EnvironmentInput input) {
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
        // We must set the organization object for the relationship
        entity.setOrganization(organizationRepository.findById(input.getOrganizationId()).get());

        return environmentRepository.save(entity).toDomain();
    }

    public Environment updateEnvironment(String id, EnvironmentInput input) {
        EnvironmentEntity entity = environmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + id));

        entity.setName(input.getName());
        entity.setType(EnvironmentType.valueOf(input.getType().name()));
        entity.setDescription(input.getDescription());
        entity.setActive(input.isActive());

        return environmentRepository.save(entity).toDomain();
    }

    public boolean deleteEnvironment(String id) {
        if (environmentRepository.existsById(id)) {
            environmentRepository.deleteById(id);
            return true;
        }
        return false;
    }
}