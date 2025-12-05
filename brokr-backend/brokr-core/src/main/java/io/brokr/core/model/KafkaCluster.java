package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaCluster {
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
    private String saslPassword;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String sslKeystoreLocation;
    private String sslKeystorePassword;
    private String sslKeyPassword;

    // Connection status
    private boolean isReachable;
    private String lastConnectionError;
    private long lastConnectionCheck;

    // JMX Configuration for broker monitoring
    private boolean jmxEnabled;
    private Integer jmxPort;
    private boolean jmxAuthentication;
    private String jmxUsername;
    private String jmxPassword;
    private boolean jmxSsl;

    // Associated components
    private List<SchemaRegistry> schemaRegistries;
    private List<KafkaConnect> kafkaConnects;
    private List<KafkaStreamsApplication> kafkaStreamsApplications;
    private List<BrokerNode> brokers;
}