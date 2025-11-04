package io.brokr.core.dto;

import io.brokr.core.model.PartitionInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionInfoDto {
    private int id;
    private int leader;
    private List<Integer> replicas;
    private List<Integer> isr;
    private long size;
    private long earliestOffset;
    private long latestOffset;

    public static PartitionInfoDto fromDomain(PartitionInfo partitionInfo) {
        return PartitionInfoDto.builder()
                .id(partitionInfo.getId())
                .leader(partitionInfo.getLeader())
                .replicas(partitionInfo.getReplicas())
                .isr(partitionInfo.getIsr())
                .size(partitionInfo.getSize())
                .earliestOffset(partitionInfo.getEarliestOffset())
                .latestOffset(partitionInfo.getLatestOffset())
                .build();
    }
}