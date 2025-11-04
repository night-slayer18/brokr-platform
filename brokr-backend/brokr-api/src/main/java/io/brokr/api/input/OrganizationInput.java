package io.brokr.api.input;

import lombok.Data;

@Data
public class OrganizationInput {
    private String id;
    private String name;
    private String description;
    private boolean isActive;
}