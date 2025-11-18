-- ========================================
-- Performance & Security Index Improvements
-- Migration V19 - November 18, 2025
-- 
-- Adds missing database indexes identified in security audit
-- Fixes N+1 query issues and improves query performance
-- ========================================

-- ========================================
-- USER TABLE INDEXES
-- ========================================

-- Critical index for login operations (high traffic)
-- Every login looks up user by email
-- Note: idx_users_email already exists from V3, but keeping for consistency
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);

-- Index for finding users by organization
-- Used in admin operations and authorization checks
-- Note: idx_users_organization_id already exists from V3, but keeping for consistency
CREATE INDEX IF NOT EXISTS idx_user_organization ON users(organization_id);

-- Composite index for active users in organization
-- Used for user management queries
CREATE INDEX IF NOT EXISTS idx_user_org_active ON users(organization_id, is_active);

-- ========================================
-- API KEY INDEXES
-- ========================================

-- Composite index for listing active API keys by user
-- Improves performance of API key management page
CREATE INDEX IF NOT EXISTS idx_api_key_user_active ON api_keys(user_id, is_active, deleted_at);

-- Index for organization-level API key queries
CREATE INDEX IF NOT EXISTS idx_api_key_organization ON api_keys(organization_id);

-- Composite index for active non-deleted keys
-- Optimizes API key listing and filtering
CREATE INDEX IF NOT EXISTS idx_api_key_active_status ON api_keys(is_active, is_revoked, deleted_at);

-- ========================================
-- MFA DEVICE INDEXES
-- ========================================

-- Critical index for MFA verification (authentication hot path)
-- Every MFA login looks up active device by user + type
CREATE INDEX IF NOT EXISTS idx_mfa_device_user_type_active 
    ON mfa_devices(user_id, type, is_active);

-- Composite index for verified active devices
CREATE INDEX IF NOT EXISTS idx_mfa_device_verified_active 
    ON mfa_devices(user_id, is_verified, is_active);

-- ========================================
-- BACKUP CODE INDEXES
-- ========================================

-- Critical index for backup code verification (authentication hot path)
-- MFA login with backup code needs fast lookup of unused codes
CREATE INDEX IF NOT EXISTS idx_backup_code_user_unused 
    ON backup_codes(user_id, is_used);

-- ========================================
-- KAFKA CLUSTER INDEXES
-- ========================================

-- Index for finding active clusters in environment
-- Used by authorization and cluster listing
CREATE INDEX IF NOT EXISTS idx_cluster_env_active 
    ON kafka_clusters(environment_id, is_active);

-- Index for organization cluster queries
CREATE INDEX IF NOT EXISTS idx_cluster_organization 
    ON kafka_clusters(organization_id);

-- Composite index for unique cluster names per organization
-- Already exists via unique constraint, but adding for clarity
-- CREATE UNIQUE INDEX IF NOT EXISTS idx_cluster_name_org 
--     ON kafka_clusters(name, organization_id);

-- ========================================
-- ENVIRONMENT INDEXES
-- ========================================

-- Index for finding environments by organization
-- Used frequently in environment listing
CREATE INDEX IF NOT EXISTS idx_environment_organization 
    ON environments(organization_id);

-- Index for environment type filtering
CREATE INDEX IF NOT EXISTS idx_environment_type 
    ON environments(type);

-- ========================================
-- MESSAGE REPLAY JOB INDEXES
-- ========================================
-- Note: Some indexes already exist based on code comments
-- Adding missing ones identified in audit

-- Index for finding jobs by source topic (for conflict detection)
CREATE INDEX IF NOT EXISTS idx_replay_source_topic 
    ON message_replay_jobs(source_topic);

-- Index for finding jobs by target topic
CREATE INDEX IF NOT EXISTS idx_replay_target_topic 
    ON message_replay_jobs(target_topic);

-- Composite index for active jobs by cluster
-- Optimizes concurrent job limit checking
CREATE INDEX IF NOT EXISTS idx_replay_cluster_status_created 
    ON message_replay_jobs(cluster_id, status, created_at);

-- ========================================
-- AUDIT LOG INDEXES
-- ========================================

-- Index for audit log queries by resource type
CREATE INDEX IF NOT EXISTS idx_audit_resource_type 
    ON audit_logs(resource_type);

-- Composite index for resource-specific audit queries
CREATE INDEX IF NOT EXISTS idx_audit_resource_id_type 
    ON audit_logs(resource_id, resource_type);

-- Index for user audit history
CREATE INDEX IF NOT EXISTS idx_audit_user_created 
    ON audit_logs(user_id, timestamp DESC);

-- Index for organization audit queries
CREATE INDEX IF NOT EXISTS idx_audit_organization 
    ON audit_logs(organization_id);

-- ========================================
-- SCHEMA REGISTRY INDEXES
-- ========================================

-- Index for finding schema registries by cluster
CREATE INDEX IF NOT EXISTS idx_schema_registry_cluster 
    ON schema_registries(cluster_id);

-- ========================================
-- KAFKA CONNECT INDEXES
-- ========================================

-- Index for finding Kafka Connect instances by cluster
CREATE INDEX IF NOT EXISTS idx_kafka_connect_cluster 
    ON kafka_connects(cluster_id);

-- ========================================
-- KAFKA STREAMS INDEXES
-- ========================================

-- Index for finding Kafka Streams apps by cluster
CREATE INDEX IF NOT EXISTS idx_kafka_streams_cluster 
    ON kafka_streams_applications(cluster_id);

-- ========================================
-- KSQLDB INDEXES
-- ========================================

-- Index for finding ksqlDB instances by cluster
CREATE INDEX IF NOT EXISTS idx_ksqldb_cluster 
    ON ksqldb_instances(cluster_id);

-- ========================================
-- PARTIAL INDEXES FOR PERFORMANCE
-- ========================================

-- Partial index for active API keys only (reduces index size)
-- Only available in PostgreSQL
CREATE INDEX IF NOT EXISTS idx_api_key_active_only 
    ON api_keys(user_id, last_used_at DESC) 
    WHERE is_active = true AND is_revoked = false AND deleted_at IS NULL;

-- Partial index for running replay jobs only
CREATE INDEX IF NOT EXISTS idx_replay_running_only 
    ON message_replay_jobs(started_at) 
    WHERE status = 'RUNNING';

-- Partial index for pending replay jobs (for scheduler)
CREATE INDEX IF NOT EXISTS idx_replay_pending_scheduled 
    ON message_replay_jobs(next_scheduled_run) 
    WHERE status = 'PENDING' AND next_scheduled_run IS NOT NULL;

-- ========================================
-- ANALYZE TABLES FOR STATISTICS
-- ========================================
-- Update table statistics for query planner optimization

ANALYZE users;
ANALYZE api_keys;
ANALYZE mfa_devices;
ANALYZE backup_codes;
ANALYZE kafka_clusters;
ANALYZE environments;
ANALYZE message_replay_jobs;
ANALYZE audit_logs;
ANALYZE schema_registries;
ANALYZE kafka_connects;
ANALYZE kafka_streams_applications;
ANALYZE ksqldb_instances;

-- ========================================
-- END OF MIGRATION
-- ========================================

