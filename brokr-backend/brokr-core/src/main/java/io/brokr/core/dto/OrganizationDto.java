package io.brokr.core.dto;

import io.brokr.core.model.Organization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {
    private String id;
    private String name;
    private String description;
    private List<EnvironmentDto> environments;
    private boolean isActive;
    
    // MFA Policy fields
    private boolean mfaRequired;
    private Integer mfaGracePeriodDays;

    public static OrganizationDto fromDomain(Organization organization) {
        return OrganizationDto.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .isActive(organization.isActive())
                .mfaRequired(organization.isMfaRequired())
                .mfaGracePeriodDays(organization.getMfaGracePeriodDays())
                .build();
    }
}