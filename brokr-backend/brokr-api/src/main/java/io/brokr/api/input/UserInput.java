package io.brokr.api.input;

import io.brokr.core.model.Role;
import lombok.Data;

import java.util.List;

@Data
public class UserInput {
    private String id;
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private Role role;
    private String organizationId;
    private List<String> accessibleEnvironmentIds;
    private Boolean isActive; // Use Boolean wrapper instead of primitive boolean to allow proper GraphQL deserialization
}