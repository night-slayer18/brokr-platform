-- brokr-app/src/main/resources/db/migration/V2__Create_environments_table.sql
CREATE TYPE environment_type AS ENUM (
    'NON_PROD_HOTFIX',
    'NON_PROD_MINOR',
    'NON_PROD_MAJOR',
    'PROD'
);

CREATE TABLE environments (
                              id VARCHAR(255) PRIMARY KEY,
                              name VARCHAR(255) NOT NULL,
                              type environment_type NOT NULL,
                              description TEXT,
                              organization_id VARCHAR(255) NOT NULL,
                              is_active BOOLEAN NOT NULL DEFAULT true,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE (name, organization_id),
                              FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

-- Index on organization_id for performance optimization
CREATE INDEX idx_environments_organization_id ON environments(organization_id);

CREATE TRIGGER update_environments_updated_at
    BEFORE UPDATE ON environments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();