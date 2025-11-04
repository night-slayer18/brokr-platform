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
public class PartitionInfo {
    private int id;
    private int leader;
    private List<Integer> replicas;
    private List<Integer> isr;
    private long size;
    private long earliestOffset;
    private long latestOffset;
}