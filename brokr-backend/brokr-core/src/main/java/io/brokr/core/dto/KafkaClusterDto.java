package io.brokr.core.dto;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.SecurityProtocol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaClusterDto {
    private String id;
    private String name;
    private String bootstrapServers;
    private Map<String, Object> properties;
    private boolean isActive;
    private String description;
    private String organizationId;
    private String environmentId;

    // Connection security settings
    private SecurityProtocol securityProtocol;
    private String saslMechanism;
    private String saslUsername;
    // Note: Passwords are not returned in queries for security
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String sslKeystoreLocation;
    private String sslKeystorePassword;
    private String sslKeyPassword;

    // Connection status
    private boolean isReachable;
    private String lastConnectionError;
    private long lastConnectionCheck;

    public static KafkaClusterDto fromDomain(KafkaCluster cluster) {
        return KafkaClusterDto.builder()
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
                .isReachable(cluster.isReachable())
                .lastConnectionError(cluster.getLastConnectionError())
                .lastConnectionCheck(cluster.getLastConnectionCheck())
                .build();
    }
}