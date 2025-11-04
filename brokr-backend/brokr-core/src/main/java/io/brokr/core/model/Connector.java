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
public class Connector {
    private String name;
    private String type;
    private ConnectorState state;
    private String config;
    private List<Task> tasks;
}