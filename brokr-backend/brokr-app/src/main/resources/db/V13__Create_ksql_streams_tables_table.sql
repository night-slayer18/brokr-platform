-- Create ksqlDB streams and tables metadata table
CREATE TABLE ksql_streams_tables (
    id VARCHAR(255) PRIMARY KEY,
    ksql_db_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL, -- 'STREAM', 'TABLE'
    topic_name VARCHAR(255),
    key_format VARCHAR(50),
    value_format VARCHAR(50),
    schema TEXT, -- JSON schema
    query_text TEXT, -- Original CREATE statement
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    properties JSONB,
    FOREIGN KEY (ksql_db_id) REFERENCES ksqldb_instances(id) ON DELETE CASCADE,
    UNIQUE(ksql_db_id, name)
);

CREATE INDEX idx_ksql_streams_tables_ksql_db_id ON ksql_streams_tables(ksql_db_id);
CREATE INDEX idx_ksql_streams_tables_type ON ksql_streams_tables(type);
CREATE INDEX idx_ksql_streams_tables_name ON ksql_streams_tables(name);

CREATE TRIGGER update_ksql_streams_tables_updated_at
    BEFORE UPDATE ON ksql_streams_tables
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

