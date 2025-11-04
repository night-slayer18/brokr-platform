package io.brokr.core.dto;

import io.brokr.core.model.Connector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorDto {
    private String name;
    private String type;
    private String state;
    private String config;
    private List<TaskDto> tasks;

    public static ConnectorDto fromDomain(Connector connector) {
        return ConnectorDto.builder()
                .name(connector.getName())
                .type(connector.getType())
                .state(connector.getState().name())
                .config(connector.getConfig())
                .tasks(connector.getTasks().stream()
                        .map(TaskDto::fromDomain)
                        .toList())
                .build();
    }
}