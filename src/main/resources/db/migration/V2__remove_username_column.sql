-- Remove username column and index from users table
-- This migration removes username as a uniqueness constraint, using email only

-- Drop the username index
DROP INDEX IF EXISTS idx_users_username;

-- Drop the username column
ALTER TABLE users DROP COLUMN IF EXISTS username;
