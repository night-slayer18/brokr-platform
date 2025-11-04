package io.brokr.storage.entity;

import io.brokr.core.model.Organization;
import io.brokr.core.model.Environment;
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
    private List<EnvironmentEntity> environments = new ArrayList<>();

    public Organization toDomain() {
        return Organization.builder()
                .id(id)
                .name(name)
                .description(description)
                .isActive(isActive)
                .environments(environments.stream().map(EnvironmentEntity::toDomain).toList())
                .build();
    }

    public static OrganizationEntity fromDomain(Organization organization) {
        return OrganizationEntity.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .isActive(organization.isActive())
                .environments(organization.getEnvironments().stream()
                        .map(EnvironmentEntity::fromDomain)
                        .toList())
                .build();
    }
}