package io.brokr.storage.entity;

import io.brokr.core.model.Organization;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean isActive;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EnvironmentEntity> environments = new ArrayList<>();

    // MFA Policy fields
    @Column(name = "mfa_required", nullable = false)
    @Builder.Default
    private boolean mfaRequired = false;

    @Column(name = "mfa_grace_period_days")
    @Builder.Default
    private Integer mfaGracePeriodDays = 7;

    @Column(name = "mfa_required_since")
    private java.time.LocalDateTime mfaRequiredSince;

    public Organization toDomain() {
        return Organization.builder()
                .id(id)
                .name(name)
                .description(description)
                .isActive(isActive)
                .mfaRequired(mfaRequired)
                .mfaGracePeriodDays(mfaGracePeriodDays)
                .mfaRequiredSince(mfaRequiredSince)
                .build();
    }

    public static OrganizationEntity fromDomain(Organization organization) {
        // Create organization entity first
        OrganizationEntity orgEntity = OrganizationEntity.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .isActive(organization.isActive())
                .mfaRequired(organization.isMfaRequired())
                .mfaGracePeriodDays(organization.getMfaGracePeriodDays())
                .mfaRequiredSince(organization.getMfaRequiredSince())
                .build();
        
        // Build environment entities and set organization reference
        List<EnvironmentEntity> envEntities = new ArrayList<>();
        if (organization.getEnvironments() != null) {
            envEntities = organization.getEnvironments().stream()
                    .map(env -> {
                        EnvironmentEntity envEntity = EnvironmentEntity.fromDomain(env);
                        // Set organization reference to establish bidirectional relationship
                        envEntity.setOrganization(orgEntity);
                        return envEntity;
                    })
                    .toList();
        }
        
        orgEntity.setEnvironments(envEntities);
        return orgEntity;
    }
}