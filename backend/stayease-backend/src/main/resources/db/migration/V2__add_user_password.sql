-- =====================================================================
-- StayEase :: V2 — add password storage for authentication
--
-- The original users table had no password (auth was deferred). We add a
-- nullable password_hash so existing rows remain valid; new accounts created
-- via /api/auth/register will populate it with a BCrypt hash. The raw password
-- is NEVER stored — only its hash.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER phone;
