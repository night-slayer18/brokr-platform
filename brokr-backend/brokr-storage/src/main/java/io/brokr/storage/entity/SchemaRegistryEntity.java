package io.brokr.storage.entity;

import io.brokr.core.model.SchemaRegistry;
import io.brokr.core.model.SecurityProtocol;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schema_registries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaRegistryEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(name = "cluster_id")
    private String clusterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_protocol")
    private SecurityProtocol securityProtocol;

    private String username;
    private String password;

    @Column(nullable = false)
    private boolean isActive;

    @Column(name = "is_reachable")
    private boolean isReachable;

    @Column(name = "last_connection_error")
    private String lastConnectionError;

    @Column(name = "last_connection_check")
    private long lastConnectionCheck;

    public SchemaRegistry toDomain() {
        return SchemaRegistry.builder()
                .id(id)
                .name(name)
                .url(url)
                .clusterId(clusterId)
                .securityProtocol(securityProtocol)
                .username(username)
                .password(password)
                .isActive(isActive)
                .isReachable(isReachable)
                .lastConnectionError(lastConnectionError)
                .lastConnectionCheck(lastConnectionCheck)
                .build();
    }

    public static SchemaRegistryEntity fromDomain(SchemaRegistry schemaRegistry) {
        return SchemaRegistryEntity.builder()
                .id(schemaRegistry.getId())
                .name(schemaRegistry.getName())
                .url(schemaRegistry.getUrl())
                .clusterId(schemaRegistry.getClusterId())
                .securityProtocol(schemaRegistry.getSecurityProtocol())
                .username(schemaRegistry.getUsername())
                .password(schemaRegistry.getPassword())
                .isActive(schemaRegistry.isActive())
                .isReachable(schemaRegistry.isReachable())
                .lastConnectionError(schemaRegistry.getLastConnectionError())
                .lastConnectionCheck(schemaRegistry.getLastConnectionCheck())
                .build();
    }
}