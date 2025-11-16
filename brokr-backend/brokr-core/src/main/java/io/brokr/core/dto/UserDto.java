package io.brokr.core.dto;

import io.brokr.core.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String organizationId;
    private List<String> accessibleEnvironmentIds;
    private boolean isActive;
    private boolean mfaEnabled;
    private String mfaType;

    public static UserDto fromDomain(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .organizationId(user.getOrganizationId())
                .accessibleEnvironmentIds(user.getAccessibleEnvironmentIds())
                .isActive(user.isActive())
                .mfaEnabled(user.isMfaEnabled())
                .mfaType(user.getMfaType() != null ? user.getMfaType().name() : null)
                .build();
    }
}