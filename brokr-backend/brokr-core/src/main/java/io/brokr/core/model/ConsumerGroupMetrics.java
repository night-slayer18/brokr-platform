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
public class ConsumerGroupMetrics {
    private String id;
    private String clusterId;
    private String consumerGroupId;
    
    // Lag metrics
    private Long totalLag;
    private Long maxLag;
    private Long minLag;
    private Long avgLag;
    
    // Offset metrics
    private Long totalOffset;
    private Long committedOffset;
    
    // Member metrics
    private Integer memberCount;
    private Integer activeMemberCount;
    
    // Topic-level lag breakdown
    private Map<String, Long> topicLags;
    
    private LocalDateTime timestamp;
}

