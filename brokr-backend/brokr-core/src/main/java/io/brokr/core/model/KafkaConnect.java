package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaConnect {
    private String id;
    private String name;
    private String url;
    private String clusterId;
    private SecurityProtocol securityProtocol;
    private String username;
    private String password;
    private boolean isActive;
    private boolean isReachable;
    private String lastConnectionError;
    private long lastConnectionCheck;
    private List<Connector> connectors;
}