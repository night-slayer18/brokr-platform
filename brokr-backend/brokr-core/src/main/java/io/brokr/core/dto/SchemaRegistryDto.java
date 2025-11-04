package io.brokr.core.dto;

import io.brokr.core.model.SchemaRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaRegistryDto {
    private String id;
    private String name;
    private String url;
    private String clusterId;
    private boolean isActive;
    private boolean isReachable;
    private String lastConnectionError;
    private long lastConnectionCheck;

    public static SchemaRegistryDto fromDomain(SchemaRegistry schemaRegistry) {
        return SchemaRegistryDto.builder()
                .id(schemaRegistry.getId())
                .name(schemaRegistry.getName())
                .url(schemaRegistry.getUrl())
                .clusterId(schemaRegistry.getClusterId())
                .isActive(schemaRegistry.isActive())
                .isReachable(schemaRegistry.isReachable())
                .lastConnectionError(schemaRegistry.getLastConnectionError())
                .lastConnectionCheck(schemaRegistry.getLastConnectionCheck())
                .build();
    }
}