-- brokr-app/src/main/resources/db/migration/V6__Create_schema_registries_table.sql
CREATE TABLE schema_registries (
                                   id VARCHAR(255) PRIMARY KEY,
                                   name VARCHAR(255) NOT NULL,
                                   url VARCHAR(500) NOT NULL,
                                   cluster_id VARCHAR(255) NOT NULL,
                                   security_protocol VARCHAR(20),
                                   username VARCHAR(255),
                                   password VARCHAR(255),
                                   is_active BOOLEAN NOT NULL DEFAULT true,
                                   is_reachable BOOLEAN NOT NULL DEFAULT false,
                                   last_connection_error TEXT,
                                   last_connection_check BIGINT NOT NULL DEFAULT 0,
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   UNIQUE (name, cluster_id),
                                   FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id)
);

CREATE INDEX idx_schema_registries_cluster_id ON schema_registries(cluster_id);

CREATE TRIGGER update_schema_registries_updated_at
    BEFORE UPDATE ON schema_registries
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();