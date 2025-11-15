-- =====================================================
-- Topic Metrics Table
-- Tracks throughput, size, and partition metrics per topic
-- =====================================================
CREATE TABLE topic_metrics (
    id VARCHAR(255) PRIMARY KEY,
    cluster_id VARCHAR(255) NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    
    -- Throughput metrics (producer side only - consumer metrics tracked separately)
    messages_per_second_in BIGINT DEFAULT 0,
    bytes_per_second_in BIGINT DEFAULT 0,
    
    -- Size metrics
    total_size_bytes BIGINT DEFAULT 0,
    partition_count INT NOT NULL,
    
    -- Partition-level metrics (stored as JSON for flexibility)
    partition_sizes JSONB,
    partition_offsets JSONB,
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id) ON DELETE CASCADE
);

-- Indexes for efficient time-series queries
CREATE INDEX idx_topic_metrics_cluster_timestamp ON topic_metrics(cluster_id, timestamp DESC);
CREATE INDEX idx_topic_metrics_topic_timestamp ON topic_metrics(topic_name, timestamp DESC);
CREATE INDEX idx_topic_metrics_timestamp ON topic_metrics(timestamp DESC);

-- Composite index for common query patterns
CREATE INDEX idx_topic_metrics_cluster_topic_timestamp ON topic_metrics(cluster_id, topic_name, timestamp DESC);

-- =====================================================
-- Consumer Group Metrics Table
-- Tracks lag, offset, and member metrics per consumer group
-- =====================================================
CREATE TABLE consumer_group_metrics (
    id VARCHAR(255) PRIMARY KEY,
    cluster_id VARCHAR(255) NOT NULL,
    consumer_group_id VARCHAR(255) NOT NULL,
    
    -- Lag metrics
    total_lag BIGINT DEFAULT 0,
    max_lag BIGINT DEFAULT 0,
    min_lag BIGINT DEFAULT 0,
    avg_lag BIGINT DEFAULT 0,
    
    -- Offset metrics
    total_offset BIGINT DEFAULT 0,
    committed_offset BIGINT DEFAULT 0,
    
    -- Member metrics
    member_count INT DEFAULT 0,
    active_member_count INT DEFAULT 0,
    
    -- Topic-level lag breakdown (JSON for flexibility)
    topic_lags JSONB,
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_consumer_group_metrics_cluster_timestamp ON consumer_group_metrics(cluster_id, timestamp DESC);
CREATE INDEX idx_consumer_group_metrics_group_timestamp ON consumer_group_metrics(consumer_group_id, timestamp DESC);
CREATE INDEX idx_consumer_group_metrics_timestamp ON consumer_group_metrics(timestamp DESC);
CREATE INDEX idx_consumer_group_metrics_cluster_group_timestamp ON consumer_group_metrics(cluster_id, consumer_group_id, timestamp DESC);

-- =====================================================
-- Cluster Metrics Table
-- Tracks cluster-level health and broker metrics
-- =====================================================
CREATE TABLE cluster_metrics (
    id VARCHAR(255) PRIMARY KEY,
    cluster_id VARCHAR(255) NOT NULL,
    
    -- Broker metrics
    broker_count INT NOT NULL,
    active_broker_count INT NOT NULL,
    
    -- Topic metrics (aggregated)
    total_topics INT NOT NULL,
    total_partitions INT NOT NULL,
    
    -- Throughput (cluster-wide)
    total_messages_per_second BIGINT DEFAULT 0,
    total_bytes_per_second BIGINT DEFAULT 0,
    
    -- Health metrics
    is_healthy BOOLEAN NOT NULL DEFAULT true,
    connection_error_count INT DEFAULT 0,
    
    -- Broker details (JSON for flexibility)
    broker_details JSONB,
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_cluster_metrics_cluster_timestamp ON cluster_metrics(cluster_id, timestamp DESC);
CREATE INDEX idx_cluster_metrics_timestamp ON cluster_metrics(timestamp DESC);

