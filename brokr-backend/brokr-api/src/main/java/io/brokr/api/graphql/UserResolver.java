package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.UserInput;
import io.brokr.api.service.UserApiService;
import io.brokr.core.dto.UserDto;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class UserResolver {

    private final UserApiService userApiService;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserDto me() {
        return UserDto.fromDomain(userApiService.getCurrentUser());
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId) or @authorizationService.canManageUsers()")
    public UserPage users(@Argument String organizationId, @Argument Integer page, @Argument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 100;
        Page<User> pageResult = userApiService.listUsers(organizationId, pageNum, pageSize);
        return new UserPage(
                pageResult.getContent().stream()
                        .map(UserDto::fromDomain)
                        .toList(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getNumber(),
                pageResult.getSize()
        );
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto user(@Argument String id) {
        return UserDto.fromDomain(userApiService.getUserById(id));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.USER, resourceNameParam = "input.email", logResult = true)
    public UserDto createUser(@Argument UserInput input) {
        // Service layer will validate ADMIN can only create users in their own organization
        return UserDto.fromDomain(userApiService.createUser(input));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.USER, resourceIdParam = "id", resourceNameParam = "input.email", logResult = true)
    public UserDto updateUser(@Argument String id, @Argument UserInput input) {
        // Service layer will validate ADMIN can only update users in their own organization
        return UserDto.fromDomain(userApiService.updateUser(id, input));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.USER, resourceIdParam = "id")
    public boolean deleteUser(@Argument String id) {
        // Service layer will validate ADMIN can only delete users in their own organization
        return userApiService.deleteUser(id);
    }
    
    /**
     * GraphQL type for paginated user results.
     */
    public static class UserPage {
        private final java.util.List<UserDto> content;
        private final long totalElements;
        private final int totalPages;
        private final int currentPage;
        private final int pageSize;

        public UserPage(java.util.List<UserDto> content, long totalElements, int totalPages, int currentPage, int pageSize) {
            this.content = content;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
        }

        public java.util.List<UserDto> getContent() { return content; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
    }
}