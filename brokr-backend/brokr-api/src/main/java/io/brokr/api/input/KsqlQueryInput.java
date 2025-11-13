package io.brokr.api.input;

import lombok.Data;

import java.util.Map;

@Data
public class KsqlQueryInput {
    private String query;
    private Map<String, Object> properties;
}

