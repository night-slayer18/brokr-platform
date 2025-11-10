package io.brokr.storage.entity;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.SecurityProtocol;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "kafka_clusters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaClusterEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String bootstrapServers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    @Column(nullable = false)
    private boolean isActive;

    private String description;

    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "environment_id")
    private String environmentId;

    // Connection security settings
    @Enumerated(EnumType.STRING)
    @Column(name = "security_protocol")
    private SecurityProtocol securityProtocol;

    @Column(name = "sasl_mechanism")
    private String saslMechanism;

    @Column(name = "sasl_username")
    private String saslUsername;

    @Column(name = "sasl_password")
    private String saslPassword;

    @Column(name = "ssl_truststore_location")
    private String sslTruststoreLocation;

    @Column(name = "ssl_truststore_password")
    private String sslTruststorePassword;

    @Column(name = "ssl_keystore_location")
    private String sslKeystoreLocation;

    @Column(name = "ssl_keystore_password")
    private String sslKeystorePassword;

    @Column(name = "ssl_key_password")
    private String sslKeyPassword;

    // Connection status
    @Column(name = "is_reachable")
    private boolean isReachable;

    @Column(name = "last_connection_error")
    private String lastConnectionError;

    @Column(name = "last_connection_check")
    private long lastConnectionCheck;

    public KafkaCluster toDomain() {
        return KafkaCluster.builder()
                .id(id)
                .name(name)
                .bootstrapServers(bootstrapServers)
                .properties(properties)
                .isActive(isActive)
                .description(description)
                .organizationId(organizationId)
                .environmentId(environmentId)
                .securityProtocol(securityProtocol)
                .saslMechanism(saslMechanism)
                .saslUsername(saslUsername)
                .saslPassword(saslPassword)
                .sslTruststoreLocation(sslTruststoreLocation)
                .sslTruststorePassword(sslTruststorePassword)
                .sslKeystoreLocation(sslKeystoreLocation)
                .sslKeystorePassword(sslKeystorePassword)
                .sslKeyPassword(sslKeyPassword)
                .isReachable(isReachable)
                .lastConnectionError(lastConnectionError)
                .lastConnectionCheck(lastConnectionCheck)
                .build();
    }

    public static KafkaClusterEntity fromDomain(KafkaCluster cluster) {
        return KafkaClusterEntity.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .bootstrapServers(cluster.getBootstrapServers())
                .properties(cluster.getProperties())
                .isActive(cluster.isActive())
                .description(cluster.getDescription())
                .organizationId(cluster.getOrganizationId())
                .environmentId(cluster.getEnvironmentId())
                .securityProtocol(cluster.getSecurityProtocol())
                .saslMechanism(cluster.getSaslMechanism())
                .saslUsername(cluster.getSaslUsername())
                .saslPassword(cluster.getSaslPassword())
                .sslTruststoreLocation(cluster.getSslTruststoreLocation())
                .sslTruststorePassword(cluster.getSslTruststorePassword())
                .sslKeystoreLocation(cluster.getSslKeystoreLocation())
                .sslKeystorePassword(cluster.getSslKeystorePassword())
                .sslKeyPassword(cluster.getSslKeyPassword())
                .isReachable(cluster.isReachable())
                .lastConnectionError(cluster.getLastConnectionError())
                .lastConnectionCheck(cluster.getLastConnectionCheck())
                .build();
    }
}