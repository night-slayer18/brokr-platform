-- Initial users and organization setup
-- This script creates the initial users (admin, orgadmin, developer) and Sample Organization
-- Mirrors what DataInitializer does but runs in production mode
-- Note: Passwords are BCrypt hashed. Default passwords:
--   - admin: admin123
--   - orgadmin: orgadmin123
--   - developer: developer123

-- Insert Sample Organization
INSERT INTO organizations (id, name, description, is_active, created_at, updated_at)
VALUES
    ('e295bdff-7729-4a32-8e5a-5afe5731926d', 'Sample Organization', 'A sample organization for demonstration', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Insert environments for Sample Organization
INSERT INTO environments (id, name, type, description, organization_id, is_active, created_at, updated_at)
VALUES
    ('252d1fa8-71d7-4c93-b12f-c7813e25a433', 'Hotfix', 'NON_PROD_HOTFIX', 'Non-production hotfix environment', 'e295bdff-7729-4a32-8e5a-5afe5731926d', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('3e276a41-2d17-47ea-bcf8-38925f58f6dc', 'Minor', 'NON_PROD_MINOR', 'Non-production minor environment', 'e295bdff-7729-4a32-8e5a-5afe5731926d', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c1ee2568-4836-40ee-aa68-2c5ffd6033a4', 'Major', 'NON_PROD_MAJOR', 'Non-production major environment', 'e295bdff-7729-4a32-8e5a-5afe5731926d', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('f7fc7307-a5d4-40b9-8b92-cdb4ca102380', 'Production', 'PROD', 'Production environment', 'e295bdff-7729-4a32-8e5a-5afe5731926d', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Insert initial users
-- Note: Passwords are BCrypt hashed. Default passwords:
--   admin: admin123
--   orgadmin: orgadmin123
--   developer: developer123
-- Note: These are placeholder hashes. Passwords will be properly set when users create accounts in the platform.
INSERT INTO users (id, username, email, password, first_name, last_name, role, organization_id, is_active, created_at, updated_at)
VALUES
    -- Super Admin (no organization)
    -- Note: Password hash will be set when user creates account in platform
    ('1a92ceda-00ba-4be7-b338-b0deadabdb03', 'admin', 'admin@brokr.io', '$2a$10$zMvNteE3NS7odAulHHMWSeBSeAzjK1OU3hTt9rqw7bl/C4esTcyXS', 'Admin', 'Brokr', 'SUPER_ADMIN', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Organization Admin
    -- Note: Password hash will be set when user creates account in platform
    ('614759b2-e1c4-4ce2-96c8-2123ddc83416', 'orgadmin', 'orgadmin@brokr.io', '$2a$10$mm3vRGwJsltX0SuM.DRjKeL6ZRk06.d7jycKtgs/L/hC7x1iDcxxO', 'John', 'Smith', 'ADMIN', 'e295bdff-7729-4a32-8e5a-5afe5731926d', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Developer (Viewer)
    -- Note: Password hash will be set when user creates account in platform
    ('2ac87ac4-f8e6-44de-981f-293eb248cdb3', 'developer', 'developer@brokr.io', '$2a$10$RxyFD35z9XLMFfKuasT5Yegx28aDva3tZVf8F2fdHN17pQSurRury', 'Jane', 'Doe', 'VIEWER', 'e295bdff-7729-4a32-8e5a-5afe5731926d', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Insert accessible environments for orgadmin (has access to all environments)
INSERT INTO user_accessible_environments (user_id, environment_id)
VALUES
    ('614759b2-e1c4-4ce2-96c8-2123ddc83416', '252d1fa8-71d7-4c93-b12f-c7813e25a433'), -- Hotfix
    ('614759b2-e1c4-4ce2-96c8-2123ddc83416', '3e276a41-2d17-47ea-bcf8-38925f58f6dc'), -- Minor
    ('614759b2-e1c4-4ce2-96c8-2123ddc83416', 'c1ee2568-4836-40ee-aa68-2c5ffd6033a4'), -- Major
    ('614759b2-e1c4-4ce2-96c8-2123ddc83416', 'f7fc7307-a5d4-40b9-8b92-cdb4ca102380')  -- Production
ON CONFLICT DO NOTHING;

-- Insert accessible environments for developer (has access to non-PROD environments only)
INSERT INTO user_accessible_environments (user_id, environment_id)
VALUES
    ('2ac87ac4-f8e6-44de-981f-293eb248cdb3', '252d1fa8-71d7-4c93-b12f-c7813e25a433'), -- Hotfix
    ('2ac87ac4-f8e6-44de-981f-293eb248cdb3', '3e276a41-2d17-47ea-bcf8-38925f58f6dc'), -- Minor
    ('2ac87ac4-f8e6-44de-981f-293eb248cdb3', 'c1ee2568-4836-40ee-aa68-2c5ffd6033a4')  -- Major
ON CONFLICT DO NOTHING;

