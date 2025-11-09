-- Add missing indexes for performance optimization

-- Index on environments.organization_id (frequently queried)
CREATE INDEX IF NOT EXISTS idx_environments_organization_id ON environments(organization_id);

-- Indexes on user_accessible_environments foreign keys (for efficient joins)
CREATE INDEX IF NOT EXISTS idx_user_accessible_environments_user_id ON user_accessible_environments(user_id);
CREATE INDEX IF NOT EXISTS idx_user_accessible_environments_environment_id ON user_accessible_environments(environment_id);

-- Index on users.username and users.email (for faster lookups)
-- Note: These already have UNIQUE constraints which create indexes, but being explicit
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

