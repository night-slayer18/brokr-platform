package io.brokr.api.graphql;

import io.brokr.api.input.OrganizationInput;
import io.brokr.core.model.Environment;
import io.brokr.core.model.Organization;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class OrganizationResolver {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final EnvironmentRepository environmentRepository; // FIX: Inject

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public List<Organization> organizations() {
        return organizationRepository.findAll().stream()
                .map(OrganizationEntity::toDomain)
                .toList();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public Organization organization(@Argument String id) {
        return organizationRepository.findById(id)
                .map(OrganizationEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
    }

    /**
     * This method resolves the 'environments' field on the 'Organization' type.
     * It is called by GraphQL to populate the environments for an organization,
     * preventing the N+1 problem.
     */
    @SchemaMapping(typeName = "Organization", field = "environments")
    public List<Environment> getEnvironments(Organization organization) {
        return environmentRepository.findByOrganizationId(organization.getId())
                .stream()
                .map(EnvironmentEntity::toDomain)
                .toList();
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public Organization createOrganization(@Argument OrganizationInput input) {
        if (organizationRepository.existsByName(input.getName())) {
            throw new RuntimeException("Organization with this name already exists");
        }

        Organization organization = Organization.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .description(input.getDescription())
                .isActive(input.isActive())
                .build();

        return organizationRepository.save(io.brokr.storage.entity.OrganizationEntity.fromDomain(organization))
                .toDomain();
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public Organization updateOrganization(@Argument String id, @Argument OrganizationInput input) {
        return organizationRepository.findById(id)
                .map(entity -> {
                    entity.setName(input.getName());
                    entity.setDescription(input.getDescription());
                    entity.setActive(input.isActive());
                    return organizationRepository.save(entity).toDomain();
                })
                .orElseThrow(() -> new RuntimeException("Organization not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public boolean deleteOrganization(@Argument String id) {
        if (organizationRepository.existsById(id)) {
            organizationRepository.deleteById(id);
            return true;
        }
        return false;
    }
}