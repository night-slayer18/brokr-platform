package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerGroup {
    private String groupId;
    private String state;
    private List<MemberInfo> members;
    private Map<String, Long> topicOffsets;
    private String coordinator;
}