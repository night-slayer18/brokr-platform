package io.brokr.api.input;

import io.brokr.core.model.SecurityProtocol;
import lombok.Data;

@Data
public class KsqlDBInput {
    private String id;
    private String name;
    private String url;
    private String clusterId;
    private SecurityProtocol securityProtocol;
    private String username;
    private String password;
    private boolean isActive;
}

