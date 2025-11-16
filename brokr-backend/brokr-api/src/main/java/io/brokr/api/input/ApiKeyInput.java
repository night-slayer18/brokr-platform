package io.brokr.api.input;

import lombok.Data;

import java.util.Set;

/**
 * Input for creating an API key.
 */
@Data
public class ApiKeyInput {
    private String name;
    private String description;
    private Set<String> scopes;
    private String expiresAt; // ISO 8601 format
}

