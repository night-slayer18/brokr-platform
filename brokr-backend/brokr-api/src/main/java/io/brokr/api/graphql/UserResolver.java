package io.brokr.api.graphql;

import io.brokr.api.input.UserInput;
import io.brokr.api.service.UserApiService;
import io.brokr.core.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class UserResolver {

    private final UserApiService userApiService;

    @QueryMapping
    public UserDto me() {
        return UserDto.fromDomain(userApiService.getCurrentUser());
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId) or @authorizationService.canManageUsers()")
    public List<UserDto> users(@Argument String organizationId) {
        return userApiService.listUsers(organizationId).stream()
                .map(UserDto::fromDomain)
                .toList();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto user(@Argument String id) {
        return UserDto.fromDomain(userApiService.getUserById(id));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public UserDto createUser(@Argument UserInput input) {
        // Service layer will validate ADMIN can only create users in their own organization
        return UserDto.fromDomain(userApiService.createUser(input));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto updateUser(@Argument String id, @Argument UserInput input) {
        // Service layer will validate ADMIN can only update users in their own organization
        return UserDto.fromDomain(userApiService.updateUser(id, input));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public boolean deleteUser(@Argument String id) {
        // Service layer will validate ADMIN can only delete users in their own organization
        return userApiService.deleteUser(id);
    }
}