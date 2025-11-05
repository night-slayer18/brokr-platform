package io.brokr.api.graphql;

import io.brokr.api.input.OrganizationInput;
import io.brokr.api.service.OrganizationApiService;
import io.brokr.core.model.Environment;
import io.brokr.core.model.Organization;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.repository.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class OrganizationResolver {

    private final OrganizationApiService organizationApiService;
    private final EnvironmentRepository environmentRepository;

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public List<Organization> organizations() {
        return organizationApiService.listOrganizations();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public Organization organization(@Argument String id) {
        return organizationApiService.getOrganizationById(id);
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
        return organizationApiService.createOrganization(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public Organization updateOrganization(@Argument String id, @Argument OrganizationInput input) {
        return organizationApiService.updateOrganization(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public boolean deleteOrganization(@Argument String id) {
        return organizationApiService.deleteOrganization(id);
    }
}