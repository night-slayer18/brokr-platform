package io.brokr.api.input;

import io.brokr.storage.entity.EnvironmentType;
import lombok.Data;

@Data
public class EnvironmentInput {
    private String id;
    private String name;
    private EnvironmentType type;
    private String description;
    private String organizationId;
    private boolean isActive;
}