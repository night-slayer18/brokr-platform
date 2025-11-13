-- Create ksqlDB query history table
CREATE TABLE ksql_query_history (
    id VARCHAR(255) PRIMARY KEY,
    ksql_db_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    query_text TEXT NOT NULL,
    query_type VARCHAR(50) NOT NULL, -- 'SELECT', 'CREATE_STREAM', 'CREATE_TABLE', 'DROP', 'TERMINATE', etc.
    status VARCHAR(50) NOT NULL, -- 'SUCCESS', 'FAILURE', 'RUNNING', 'CANCELLED'
    execution_time_ms BIGINT,
    rows_returned INTEGER,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    properties JSONB, -- Additional query properties
    FOREIGN KEY (ksql_db_id) REFERENCES ksqldb_instances(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_ksql_query_history_ksql_db_id ON ksql_query_history(ksql_db_id);
CREATE INDEX idx_ksql_query_history_user_id ON ksql_query_history(user_id);
CREATE INDEX idx_ksql_query_history_started_at ON ksql_query_history(started_at DESC);
CREATE INDEX idx_ksql_query_history_status ON ksql_query_history(status);
CREATE INDEX idx_ksql_query_history_query_type ON ksql_query_history(query_type);

