package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents partition distribution information for a broker.
 * Used to track leader and replica counts per broker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerPartitionInfo {
    private int leaderCount;
    private int replicaCount;
    private int underReplicatedCount;
    private int offlineCount;
}
