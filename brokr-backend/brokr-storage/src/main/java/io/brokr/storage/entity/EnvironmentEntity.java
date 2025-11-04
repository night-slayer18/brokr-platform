package io.brokr.storage.entity;

import io.brokr.core.model.Environment;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "environments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentType type;

    private String description;

    @Column(nullable = false)
    private boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private OrganizationEntity organization;

    // FIX: Add the organizationId field directly for efficient queries
    // This maps to the *same* column as the object above
    @Column(name = "organization_id", insertable = false, updatable = false)
    private String organizationId;

    public Environment toDomain() {
        return Environment.builder()
                .id(id)
                .name(name)
                .type(type.name())
                .description(description)
                .isActive(isActive)
                .organizationId(organizationId) // FIX: Pass the ID to the domain model
                .build();
    }

    public static EnvironmentEntity fromDomain(Environment environment) {
        return EnvironmentEntity.builder()
                .id(environment.getId())
                .name(environment.getName())
                .type(EnvironmentType.valueOf(environment.getType()))
                .description(environment.getDescription())
                .isActive(environment.isActive())
                // We only need to set the organization object when creating/updating
                // The organizationId field is managed by the database
                .build();
    }
}