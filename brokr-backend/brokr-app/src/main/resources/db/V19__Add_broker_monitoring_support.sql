-- =====================================================
-- V19: Create Broker Metrics Table
-- Stores time-series metrics for individual Kafka brokers
-- Includes resource usage, throughput, and partition data
-- =====================================================

CREATE TABLE broker_metrics (
    id VARCHAR(255) PRIMARY KEY,
    cluster_id VARCHAR(255) NOT NULL,
    broker_id INTEGER NOT NULL,
    
    -- Resource metrics (from JMX)
    cpu_usage_percent DOUBLE PRECISION,
    memory_used_bytes BIGINT,
    memory_max_bytes BIGINT,
    disk_used_bytes BIGINT,
    disk_total_bytes BIGINT,
    
    -- Throughput metrics (from JMX)
    bytes_in_per_second BIGINT,
    bytes_out_per_second BIGINT,
    messages_in_per_second BIGINT,
    requests_per_second BIGINT,
    
    -- Partition metrics (from Admin API)
    leader_partition_count INTEGER,
    replica_partition_count INTEGER,
    under_replicated_partitions INTEGER,
    offline_partitions INTEGER,
    
    -- Health status
    is_controller BOOLEAN,
    is_healthy BOOLEAN,
    last_error TEXT,
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id) ON DELETE CASCADE
);

-- Composite index for time-range queries by cluster and broker
CREATE INDEX idx_broker_metrics_cluster_broker_ts ON broker_metrics(cluster_id, broker_id, timestamp DESC);

-- Index for time-range queries by cluster
CREATE INDEX idx_broker_metrics_cluster_ts ON broker_metrics(cluster_id, timestamp DESC);

-- Index for timestamp-based cleanup queries
CREATE INDEX idx_broker_metrics_timestamp ON broker_metrics(timestamp DESC);

-- =====================================================
-- Comments for documentation
-- =====================================================
COMMENT ON TABLE broker_metrics IS 'Time-series metrics for individual Kafka brokers, collected via JMX and Admin API';
COMMENT ON COLUMN broker_metrics.cpu_usage_percent IS 'CPU usage percentage (0-100)';
COMMENT ON COLUMN broker_metrics.memory_used_bytes IS 'JVM heap memory currently used';
COMMENT ON COLUMN broker_metrics.memory_max_bytes IS 'JVM heap memory maximum';
COMMENT ON COLUMN broker_metrics.bytes_in_per_second IS 'Bytes received per second';
COMMENT ON COLUMN broker_metrics.bytes_out_per_second IS 'Bytes sent per second';
COMMENT ON COLUMN broker_metrics.leader_partition_count IS 'Number of partitions where this broker is leader';
COMMENT ON COLUMN broker_metrics.under_replicated_partitions IS 'Number of under-replicated partitions on this broker';
COMMENT ON COLUMN broker_metrics.is_controller IS 'Whether this broker is the cluster controller';
