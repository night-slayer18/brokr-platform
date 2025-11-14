package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMetrics {
    private String id;
    private String clusterId;
    private String topicName;
    
    // Throughput
    private Long messagesPerSecondIn;
    private Long messagesPerSecondOut;
    private Long bytesPerSecondIn;
    private Long bytesPerSecondOut;
    
    // Size
    private Long totalSizeBytes;
    private Integer partitionCount;
    
    // Partition details (JSON)
    private Map<String, Object> partitionSizes;
    private Map<String, Object> partitionOffsets;
    
    private LocalDateTime timestamp;
}

