package io.brokr.api.input;

import java.util.List;
import lombok.Data;

@Data
public class OrganizationInput {
    private String id;
    private String name;
    private String description;
    private Boolean isActive;
    private List<EnvironmentInput> initialEnvironments;
}