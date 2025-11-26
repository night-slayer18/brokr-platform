-- =====================================================
-- MFA Support Migration
-- Creates MFA-related tables for TOTP-based Multi-Factor Authentication
-- Note: MFA columns for users and organizations are in their respective CREATE TABLE scripts
-- =====================================================

-- =====================================================
-- MFA Devices Table
-- Stores TOTP device information (secret keys)
-- =====================================================
CREATE TABLE IF NOT EXISTS mfa_devices (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL, -- 'TOTP' only
    name VARCHAR(255), -- Device name (e.g., "iPhone", "Google Authenticator")
    secret_key VARCHAR(255) NOT NULL, -- Encrypted secret for TOTP
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_active_device UNIQUE(user_id, type, is_active) -- Only one active device per type per user
);

CREATE INDEX IF NOT EXISTS idx_mfa_devices_user_id ON mfa_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_devices_user_type_active ON mfa_devices(user_id, type, is_active);

-- Composite index for verified active devices
CREATE INDEX IF NOT EXISTS idx_mfa_device_verified_active ON mfa_devices(user_id, is_verified, is_active);

-- Trigger to update updated_at
CREATE TRIGGER update_mfa_devices_updated_at
    BEFORE UPDATE ON mfa_devices
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- Backup Codes Table
-- Stores hashed backup codes for account recovery
-- =====================================================
CREATE TABLE IF NOT EXISTS backup_codes (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL, -- BCrypt hash of the code
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_code_hash UNIQUE(user_id, code_hash)
);

CREATE INDEX IF NOT EXISTS idx_backup_codes_user_id ON backup_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_backup_codes_user_unused ON backup_codes(user_id, is_used) WHERE is_used = FALSE;

