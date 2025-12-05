package io.brokr.api.input;


import io.brokr.core.model.SecurityProtocol;
import lombok.Data;

import java.util.Map;

@Data
public class KafkaClusterInput {
    private String id;
    private String name;
    private String bootstrapServers;
    private Map<String, Object> properties;
    private Boolean isActive;
    private String description;
    private String organizationId;
    private String environmentId;

    // Connection settings
    private SecurityProtocol securityProtocol;
    private String saslMechanism;
    private String saslUsername;
    private String saslPassword;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String sslKeystoreLocation;
    private String sslKeystorePassword;
    private String sslKeyPassword;
    
    // JMX configuration for broker monitoring
    private Boolean jmxEnabled;
    private Integer jmxPort;
    private Boolean jmxAuthentication;
    private String jmxUsername;
    private String jmxPassword;
    private Boolean jmxSsl;
}