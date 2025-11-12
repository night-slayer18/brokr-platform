-- brokr-app/src/main/resources/db/migration/V11__Create_audit_logs_table.sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- User Information
    user_id VARCHAR(255),
    user_email VARCHAR(255),
    user_role VARCHAR(50),
    
    -- Action Details
    action_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255),
    resource_name VARCHAR(500),
    
    -- Context
    organization_id VARCHAR(255),
    environment_id VARCHAR(255),
    cluster_id VARCHAR(255),
    
    -- Request Details
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(255),
    
    -- Change Details
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    
    -- Result
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    
    -- Additional Metadata
    metadata JSONB,
    severity VARCHAR(20) DEFAULT 'INFO',
    
    -- Compliance
    retention_until TIMESTAMP,
    is_sensitive BOOLEAN DEFAULT false
);

-- Indexes for efficient querying
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs(resource_type);
CREATE INDEX idx_audit_logs_resource_id ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_organization_id ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_cluster_id ON audit_logs(cluster_id);
CREATE INDEX idx_audit_logs_status ON audit_logs(status);
CREATE INDEX idx_audit_logs_severity ON audit_logs(severity);

-- Composite indexes for common queries
CREATE INDEX idx_audit_logs_user_time ON audit_logs(user_id, timestamp DESC);
CREATE INDEX idx_audit_logs_resource_time ON audit_logs(resource_type, resource_id, timestamp DESC);
CREATE INDEX idx_audit_logs_org_time ON audit_logs(organization_id, timestamp DESC);

-- Comments for documentation
COMMENT ON TABLE audit_logs IS 'Stores audit logs for all user actions and system events';
COMMENT ON COLUMN audit_logs.id IS 'Auto-incrementing primary key';
COMMENT ON COLUMN audit_logs.timestamp IS 'When the action occurred';
COMMENT ON COLUMN audit_logs.user_id IS 'ID of the user who performed the action';
COMMENT ON COLUMN audit_logs.action_type IS 'Type of action: CREATE, UPDATE, DELETE, READ, LOGIN, etc.';
COMMENT ON COLUMN audit_logs.resource_type IS 'Type of resource: CLUSTER, TOPIC, USER, etc.';
COMMENT ON COLUMN audit_logs.resource_id IS 'ID of the affected resource';
COMMENT ON COLUMN audit_logs.old_values IS 'Previous state (JSON) for UPDATE/DELETE operations';
COMMENT ON COLUMN audit_logs.new_values IS 'New state (JSON) for CREATE/UPDATE operations';
COMMENT ON COLUMN audit_logs.changed_fields IS 'Array of field names that changed';
COMMENT ON COLUMN audit_logs.status IS 'SUCCESS, FAILURE, or PARTIAL';
COMMENT ON COLUMN audit_logs.severity IS 'INFO, WARNING, ERROR, or CRITICAL';
COMMENT ON COLUMN audit_logs.is_sensitive IS 'Marks operations that involve sensitive data';
COMMENT ON COLUMN audit_logs.retention_until IS 'Timestamp after which this log can be archived/deleted';

