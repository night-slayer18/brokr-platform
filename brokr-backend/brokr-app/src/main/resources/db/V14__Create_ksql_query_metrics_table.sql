-- Create ksqlDB query performance metrics table
CREATE TABLE ksql_query_metrics (
    id VARCHAR(255) PRIMARY KEY,
    query_history_id VARCHAR(255) NOT NULL,
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_mb BIGINT,
    rows_processed_per_second BIGINT,
    bytes_read BIGINT,
    bytes_written BIGINT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (query_history_id) REFERENCES ksql_query_history(id) ON DELETE CASCADE
);

CREATE INDEX idx_ksql_query_metrics_query_history_id ON ksql_query_metrics(query_history_id);
CREATE INDEX idx_ksql_query_metrics_timestamp ON ksql_query_metrics(timestamp DESC);

