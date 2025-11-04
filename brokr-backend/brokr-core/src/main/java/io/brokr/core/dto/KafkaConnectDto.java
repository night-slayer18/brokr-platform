package io.brokr.core.dto;

import io.brokr.core.model.KafkaConnect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaConnectDto {
    private String id;
    private String name;
    private String url;
    private String clusterId;
    private boolean isActive;
    private boolean isReachable;
    private String lastConnectionError;
    private long lastConnectionCheck;
    private List<ConnectorDto> connectors;

    public static KafkaConnectDto fromDomain(KafkaConnect kafkaConnect) {
        return KafkaConnectDto.builder()
                .id(kafkaConnect.getId())
                .name(kafkaConnect.getName())
                .url(kafkaConnect.getUrl())
                .clusterId(kafkaConnect.getClusterId())
                .isActive(kafkaConnect.isActive())
                .isReachable(kafkaConnect.isReachable())
                .lastConnectionError(kafkaConnect.getLastConnectionError())
                .lastConnectionCheck(kafkaConnect.getLastConnectionCheck())
                .connectors(kafkaConnect.getConnectors().stream()
                        .map(ConnectorDto::fromDomain)
                        .toList())
                .build();
    }
}