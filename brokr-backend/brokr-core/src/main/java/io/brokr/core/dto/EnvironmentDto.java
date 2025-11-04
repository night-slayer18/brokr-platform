package io.brokr.core.dto;

import io.brokr.core.model.Environment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentDto {
    private String id;
    private String name;
    private String type;
    private String description;
    private boolean isActive;

    public static EnvironmentDto fromDomain(Environment environment) {
        return EnvironmentDto.builder()
                .id(environment.getId())
                .name(environment.getName())
                .type(environment.getType())
                .description(environment.getDescription())
                .isActive(environment.isActive())
                .build();
    }
}