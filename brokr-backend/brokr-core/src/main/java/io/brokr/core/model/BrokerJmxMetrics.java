package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JMX metrics collected from a Kafka broker.
 * Contains system-level and Kafka-specific metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerJmxMetrics {
    private Double cpuUsagePercent;
    private Long memoryUsedBytes;
    private Long memoryMaxBytes;
    private Long diskUsedBytes;
    private Long diskTotalBytes;
    private Long bytesInPerSecond;
    private Long bytesOutPerSecond;
    private Long messagesInPerSecond;
    private Long requestsPerSecond;
}
