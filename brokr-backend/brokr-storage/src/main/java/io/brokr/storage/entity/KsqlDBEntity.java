package io.brokr.storage.entity;

import io.brokr.core.model.KsqlDB;
import io.brokr.core.model.SecurityProtocol;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ksqldb_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlDBEntity {
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

    public KsqlDB toDomain() {
        return KsqlDB.builder()
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

    public static KsqlDBEntity fromDomain(KsqlDB ksqlDB) {
        return KsqlDBEntity.builder()
                .id(ksqlDB.getId())
                .name(ksqlDB.getName())
                .url(ksqlDB.getUrl())
                .clusterId(ksqlDB.getClusterId())
                .securityProtocol(ksqlDB.getSecurityProtocol())
                .username(ksqlDB.getUsername())
                .password(ksqlDB.getPassword())
                .isActive(ksqlDB.isActive())
                .isReachable(ksqlDB.isReachable())
                .lastConnectionError(ksqlDB.getLastConnectionError())
                .lastConnectionCheck(ksqlDB.getLastConnectionCheck())
                .build();
    }
}

