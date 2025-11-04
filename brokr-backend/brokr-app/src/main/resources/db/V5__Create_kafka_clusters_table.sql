-- brokr-app/src/main/resources/db/migration/V5__Create_kafka_clusters_table.sql
CREATE TABLE kafka_clusters (
                                id VARCHAR(255) PRIMARY KEY,
                                name VARCHAR(255) NOT NULL,
                                bootstrap_servers VARCHAR(255) NOT NULL,
                                properties JSONB,
                                is_active BOOLEAN NOT NULL DEFAULT true,
                                description TEXT,
                                organization_id VARCHAR(255) NOT NULL,
                                environment_id VARCHAR(255) NOT NULL,
                                security_protocol VARCHAR(20),
                                sasl_mechanism VARCHAR(50),
                                sasl_username VARCHAR(255),
                                sasl_password VARCHAR(255),
                                ssl_truststore_location VARCHAR(500),
                                ssl_truststore_password VARCHAR(255),
                                ssl_keystore_location VARCHAR(500),
                                ssl_keystore_password VARCHAR(255),
                                ssl_key_password VARCHAR(255),
                                is_reachable BOOLEAN NOT NULL DEFAULT false,
                                last_connection_error TEXT,
                                last_connection_check BIGINT NOT NULL DEFAULT 0,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                UNIQUE (name, organization_id),
                                FOREIGN KEY (organization_id) REFERENCES organizations(id),
                                FOREIGN KEY (environment_id) REFERENCES environments(id)
);

CREATE TRIGGER update_kafka_clusters_updated_at
    BEFORE UPDATE ON kafka_clusters
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();