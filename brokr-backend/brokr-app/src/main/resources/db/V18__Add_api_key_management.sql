-- =====================================================
-- API Key Management Migration
-- Creates tables for API key management with enterprise-grade features
-- =====================================================

-- =====================================================
-- API Keys Table
-- Stores API keys with hashed secrets, scopes, and metadata
-- =====================================================
CREATE TABLE IF NOT EXISTS api_keys (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    key_prefix VARCHAR(50) NOT NULL, -- "brokr_<uuid>" part for fast lookup
    secret_hash VARCHAR(255) NOT NULL, -- BCrypt hash of secret
    old_secret_hash VARCHAR(255), -- For rotation grace period
    scopes TEXT[] NOT NULL DEFAULT '{}', -- Array of permission scopes
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revoked_reason TEXT,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP, -- Soft delete
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT unique_key_prefix UNIQUE(key_prefix)
);

-- Indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_org_id ON api_keys(organization_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_prefix ON api_keys(key_prefix);
-- Composite index for active key lookups (most common query)
CREATE INDEX IF NOT EXISTS idx_api_keys_active ON api_keys(is_active, is_revoked, expires_at) 
    WHERE is_active = TRUE AND is_revoked = FALSE;
-- Composite index for user's active keys
CREATE INDEX IF NOT EXISTS idx_api_keys_user_active ON api_keys(user_id, is_active, is_revoked) 
    WHERE is_active = TRUE AND is_revoked = FALSE;
-- Index for expiration checks
CREATE INDEX IF NOT EXISTS idx_api_keys_expires_at ON api_keys(expires_at) 
    WHERE expires_at IS NOT NULL;

-- Trigger to update updated_at
CREATE TRIGGER update_api_keys_updated_at
    BEFORE UPDATE ON api_keys
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- API Key Usage Table
-- Tracks API key usage for analytics and security monitoring
-- Partitioned by month for better performance (optional, can be added later)
-- =====================================================
CREATE TABLE IF NOT EXISTS api_key_usage (
    id BIGSERIAL PRIMARY KEY,
    api_key_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    endpoint VARCHAR(255) NOT NULL, -- e.g., "/api/clusters", "/graphql"
    method VARCHAR(10) NOT NULL, -- GET, POST, PUT, DELETE, etc.
    status_code INT NOT NULL,
    response_time_ms INT,
    ip_address VARCHAR(45), -- IPv4 or IPv6
    user_agent TEXT,
    request_size_bytes INT,
    response_size_bytes INT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (api_key_id) REFERENCES api_keys(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

-- Indexes for analytics queries
CREATE INDEX IF NOT EXISTS idx_api_key_usage_key_id ON api_key_usage(api_key_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_user_id ON api_key_usage(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_org_id ON api_key_usage(organization_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_endpoint ON api_key_usage(endpoint, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_created_at ON api_key_usage(created_at DESC);
-- Composite index for time-range queries
CREATE INDEX IF NOT EXISTS idx_api_key_usage_key_time ON api_key_usage(api_key_id, created_at DESC);
-- Index for status code analysis
CREATE INDEX IF NOT EXISTS idx_api_key_usage_status ON api_key_usage(status_code, created_at DESC);

-- =====================================================
-- API Key Rate Limits Table
-- Stores rate limit configuration per API key
-- =====================================================
CREATE TABLE IF NOT EXISTS api_key_rate_limits (
    id VARCHAR(255) PRIMARY KEY,
    api_key_id VARCHAR(255) NOT NULL,
    limit_type VARCHAR(20) NOT NULL, -- 'per_second', 'per_minute', 'per_hour', 'per_day'
    limit_value INT NOT NULL,
    window_seconds INT NOT NULL, -- Time window in seconds
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (api_key_id) REFERENCES api_keys(id) ON DELETE CASCADE,
    CONSTRAINT unique_key_limit_type UNIQUE(api_key_id, limit_type)
);

-- Indexes for rate limit lookups
CREATE INDEX IF NOT EXISTS idx_rate_limits_key_id ON api_key_rate_limits(api_key_id);
CREATE INDEX IF NOT EXISTS idx_rate_limits_type ON api_key_rate_limits(limit_type);

-- Trigger to update updated_at
CREATE TRIGGER update_api_key_rate_limits_updated_at
    BEFORE UPDATE ON api_key_rate_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- Comments for documentation
-- =====================================================
COMMENT ON TABLE api_keys IS 'Stores API keys with hashed secrets and scopes for programmatic access';
COMMENT ON TABLE api_key_usage IS 'Tracks API key usage for analytics, security monitoring, and billing';
COMMENT ON TABLE api_key_rate_limits IS 'Stores rate limit configuration per API key';

COMMENT ON COLUMN api_keys.key_prefix IS 'The "brokr_<uuid>" prefix for fast key lookup without secret';
COMMENT ON COLUMN api_keys.secret_hash IS 'BCrypt hash of the secret part of the API key';
COMMENT ON COLUMN api_keys.old_secret_hash IS 'Previous secret hash during rotation grace period';
COMMENT ON COLUMN api_keys.scopes IS 'Array of permission scopes (e.g., clusters:read, topics:write)';
COMMENT ON COLUMN api_keys.deleted_at IS 'Soft delete timestamp for audit trail';

