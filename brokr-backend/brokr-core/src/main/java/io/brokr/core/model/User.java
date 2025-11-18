package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@lombok.ToString(exclude = {"password"})
@lombok.EqualsAndHashCode(exclude = {"password"})
public class User {
    private String id;
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private Role role;
    private String organizationId;
    private List<String> accessibleEnvironmentIds;
    private boolean isActive;
    
    // MFA fields
    private boolean mfaEnabled;
    private MfaType mfaType;
    private boolean mfaEnforced; // Organization-level enforcement
}