package io.brokr.api.graphql;

import io.brokr.api.input.EnvironmentInput;
import io.brokr.core.model.Environment;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.EnvironmentType;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class EnvironmentResolver {

    private final EnvironmentRepository environmentRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthorizationService authorizationService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId)")
    public List<Environment> environments(@Argument String organizationId) {
        User currentUser = authorizationService.getCurrentUser();

        // Super admins can see all environments
        if (currentUser.getRole() == io.brokr.core.model.Role.SUPER_ADMIN) {
            return environmentRepository.findByOrganizationId(organizationId).stream()
                    .map(EnvironmentEntity::toDomain)
                    .toList();
        }

        // Other users can only see environments they have access to
        return environmentRepository.findByOrganizationId(organizationId).stream()
                .filter(entity -> currentUser.getAccessibleEnvironmentIds().contains(entity.getId()))
                .map(EnvironmentEntity::toDomain)
                .toList();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public Environment environment(@Argument String id) {
        return environmentRepository.findById(id)
                .map(EnvironmentEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Environment not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#input.organizationId)")
    public Environment createEnvironment(@Argument EnvironmentInput input) {
        if (!organizationRepository.existsById(input.getOrganizationId())) {
            throw new RuntimeException("Organization not found");
        }

        Environment environment = Environment.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .type(input.getType().name())
                .description(input.getDescription())
                .isActive(input.isActive())
                .build();

        io.brokr.storage.entity.EnvironmentEntity entity =
                io.brokr.storage.entity.EnvironmentEntity.fromDomain(environment);

        entity.setOrganization(organizationRepository.findById(input.getOrganizationId()).orElse(null));

        return environmentRepository.save(entity).toDomain();
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public Environment updateEnvironment(@Argument String id, @Argument EnvironmentInput input) {
        return environmentRepository.findById(id)
                .map(entity -> {
                    entity.setName(input.getName());
                    entity.setType(EnvironmentType.valueOf(input.getType().name()));
                    entity.setDescription(input.getDescription());
                    entity.setActive(input.isActive());
                    return environmentRepository.save(entity).toDomain();
                })
                .orElseThrow(() -> new RuntimeException("Environment not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public boolean deleteEnvironment(@Argument String id) {
        if (environmentRepository.existsById(id)) {
            environmentRepository.deleteById(id);
            return true;
        }
        return false;
    }
}