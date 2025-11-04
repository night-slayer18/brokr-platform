-- brokr-app/src/main/resources/db/migration/V1__Create_organizations_table.sql
CREATE TABLE organizations (
                               id VARCHAR(255) PRIMARY KEY,
                               name VARCHAR(255) NOT NULL UNIQUE,
                               description TEXT,
                               is_active BOOLEAN NOT NULL DEFAULT true,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();