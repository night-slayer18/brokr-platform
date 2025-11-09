package io.brokr.api.graphql;

import io.brokr.api.input.OrganizationInput;
import io.brokr.api.service.EnvironmentApiService;
import io.brokr.api.service.OrganizationApiService;
import io.brokr.api.service.UserApiService;
import io.brokr.core.model.Environment;
import io.brokr.core.model.Organization;
import io.brokr.core.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class OrganizationResolver {

    private final OrganizationApiService organizationApiService;
    private final EnvironmentApiService environmentApiService;
    private final UserApiService userApiService;

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

    @BatchMapping(typeName = "Organization", field = "environments")
    public Map<Organization, List<Environment>> getEnvironments(List<Organization> organizations) {
        List<String> organizationIds = organizations.stream()
                .map(Organization::getId)
                .toList();

        Map<String, List<Environment>> environmentsByOrgId = environmentApiService.getEnvironmentsForOrganizations(organizationIds);

        return organizations.stream()
                .collect(Collectors.toMap(
                        java.util.function.Function.identity(),
                        org -> environmentsByOrgId.getOrDefault(org.getId(), List.of())
                ));
    }

    @BatchMapping(typeName = "Organization", field = "users")
    public Map<Organization, List<User>> getUsers(List<Organization> organizations) {
        List<String> organizationIds = organizations.stream()
                .map(Organization::getId)
                .toList();

        Map<String, List<User>> usersByOrgId = userApiService.getUsersForOrganizations(organizationIds);

        return organizations.stream()
                .collect(Collectors.toMap(
                        java.util.function.Function.identity(),
                        org -> usersByOrgId.getOrDefault(org.getId(), List.of())
                ));
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