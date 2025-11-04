package io.brokr.storage.entity;

import io.brokr.core.model.KafkaConnect;
import io.brokr.core.model.SecurityProtocol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kafka_connects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaConnectEntity {
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

    @JdbcTypeCode(SqlTypes.JSON)
    private String connectors;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaConnect toDomain() {
        List<io.brokr.core.model.Connector> connectorList = null;
        if (connectors != null) {
            try {
                connectorList = objectMapper.readValue(connectors, new TypeReference<List<io.brokr.core.model.Connector>>() {});
            } catch (IOException e) {
                // Handle error
            }
        }

        return KafkaConnect.builder()
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
                .connectors(connectorList)
                .build();
    }

    public static KafkaConnectEntity fromDomain(KafkaConnect kafkaConnect) {
        String connectorsJson = null;
        if (kafkaConnect.getConnectors() != null) {
            try {
                connectorsJson = objectMapper.writeValueAsString(kafkaConnect.getConnectors());
            } catch (IOException e) {
                // Handle error
            }
        }

        return KafkaConnectEntity.builder()
                .id(kafkaConnect.getId())
                .name(kafkaConnect.getName())
                .url(kafkaConnect.getUrl())
                .clusterId(kafkaConnect.getClusterId())
                .securityProtocol(kafkaConnect.getSecurityProtocol())
                .username(kafkaConnect.getUsername())
                .password(kafkaConnect.getPassword())
                .isActive(kafkaConnect.isActive())
                .isReachable(kafkaConnect.isReachable())
                .lastConnectionError(kafkaConnect.getLastConnectionError())
                .lastConnectionCheck(kafkaConnect.getLastConnectionCheck())
                .connectors(connectorsJson)
                .build();
    }
}