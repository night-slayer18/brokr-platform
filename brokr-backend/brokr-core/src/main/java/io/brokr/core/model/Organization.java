package io.brokr.core.model;

import io.brokr.core.dto.OrganizationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {
    private String id;
    private String name;
    private String description;
    private List<Environment> environments;
    private boolean isActive;
    
    // MFA Policy fields
    private boolean mfaRequired;
    private Integer mfaGracePeriodDays; // Grace period in days for users to enable MFA
    private java.time.LocalDateTime mfaRequiredSince; // When MFA was required (for grace period calculation)

    // Convenience method for DTO conversion
    public OrganizationDto toDto() {
        return OrganizationDto.fromDomain(this);
    }
}