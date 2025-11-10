-- brokr-app/src/main/resources/db/migration/V3__Create_users_table.sql
CREATE TYPE user_role AS ENUM (
    'VIEWER',
    'ADMIN',
    'SUPER_ADMIN',
    'SERVER_ADMIN'
);

CREATE TABLE users (
                       id VARCHAR(255) PRIMARY KEY,
                       username VARCHAR(255) NOT NULL UNIQUE,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       first_name VARCHAR(255),
                       last_name VARCHAR(255),
                       role user_role NOT NULL,
                       organization_id VARCHAR(255),
                       is_active BOOLEAN NOT NULL DEFAULT true,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE INDEX idx_users_organization_id ON users(organization_id);

-- Indexes on username and email for faster lookups (UNIQUE constraints already create indexes, but explicit for clarity)
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();