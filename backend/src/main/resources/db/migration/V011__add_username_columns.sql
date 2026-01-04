-- Add username columns for Gesellschaft username generation
-- usernameKeyword: association identifier (e.g., 'W_CORP', 'LIMBUS_COMPANY_LCB')
-- usernameSuffix: unique 5-character alphanumeric suffix

ALTER TABLE users
ADD COLUMN username_keyword VARCHAR(50) NOT NULL,
ADD COLUMN username_suffix VARCHAR(5) NOT NULL;

-- Suffix must be unique to ensure username uniqueness
ALTER TABLE users
ADD CONSTRAINT uk_users_username_suffix UNIQUE (username_suffix);

-- Index on keyword for potential filtering by association
CREATE INDEX idx_users_username_keyword ON users(username_keyword);
