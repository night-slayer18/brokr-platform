-- brokr-app/src/main/resources/db/migration/V8__Create_kafka_streams_applications_table.sql
CREATE TABLE kafka_streams_applications (
                                            id VARCHAR(255) PRIMARY KEY,
                                            name VARCHAR(255) NOT NULL,
                                            application_id VARCHAR(255) NOT NULL,
                                            cluster_id VARCHAR(255) NOT NULL,
                                            topics TEXT[],
                                            configuration JSONB,
                                            is_active BOOLEAN NOT NULL DEFAULT true,
                                            state VARCHAR(20),
                                            threads JSONB,
                                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            UNIQUE (name, cluster_id),
                                            FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id)
);

CREATE INDEX idx_kafka_streams_applications_cluster_id ON kafka_streams_applications(cluster_id);

CREATE TRIGGER update_kafka_streams_applications_updated_at
    BEFORE UPDATE ON kafka_streams_applications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();