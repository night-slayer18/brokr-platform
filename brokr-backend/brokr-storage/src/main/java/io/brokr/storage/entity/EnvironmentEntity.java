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

    public Environment toDomain() {
        return Environment.builder()
                .id(id)
                .name(name)
                .type(type.name())
                .description(description)
                .isActive(isActive)
                .build();
    }

    public static EnvironmentEntity fromDomain(Environment environment) {
        return EnvironmentEntity.builder()
                .id(environment.getId())
                .name(environment.getName())
                .type(EnvironmentType.valueOf(environment.getType()))
                .description(environment.getDescription())
                .isActive(environment.isActive())
                .build();
    }
}