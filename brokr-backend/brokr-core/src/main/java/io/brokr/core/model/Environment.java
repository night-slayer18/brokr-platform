package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Environment {
    private String id;
    private String name;
    private String type; // NON_PROD_HOTFIX, NON_PROD_MINOR, NON_PROD_MAJOR, PROD
    private String description;
    private boolean isActive;
    private String organizationId;
}