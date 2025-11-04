package io.brokr.storage.entity;

import io.brokr.core.model.User;
import io.brokr.core.model.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "organization_id")
    private String organizationId;

    @ElementCollection
    @CollectionTable(name = "user_accessible_environments", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "environment_id")
    private List<String> accessibleEnvironmentIds = new ArrayList<>();

    @Column(nullable = false)
    private boolean isActive;

    public User toDomain() {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .password(password)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .organizationId(organizationId)
                .accessibleEnvironmentIds(accessibleEnvironmentIds)
                .isActive(isActive)
                .build();
    }

    public static UserEntity fromDomain(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .organizationId(user.getOrganizationId())
                .accessibleEnvironmentIds(user.getAccessibleEnvironmentIds())
                .isActive(user.isActive())
                .build();
    }
}