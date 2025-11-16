-- =====================================================
-- Add MFA Required Since Timestamp
-- Tracks when MFA was required for an organization to calculate grace period
-- =====================================================

ALTER TABLE organizations ADD COLUMN IF NOT EXISTS mfa_required_since TIMESTAMP;

-- For existing organizations with MFA required, set timestamp to now
-- This ensures existing users get the full grace period
UPDATE organizations 
SET mfa_required_since = CURRENT_TIMESTAMP 
WHERE mfa_required = TRUE AND mfa_required_since IS NULL;

