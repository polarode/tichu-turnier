-- Phase 4 (#21): admin accounts gain a lifecycle and a role. Registration becomes a
-- request (PENDING), and only owners manage admins. Existing accounts were created
-- under open registration and were all equal admins, so they backfill to ACTIVE/ADMIN;
-- the configured platform owner is provisioned from configuration at startup.
--
-- The columns are added nullable, backfilled, then made NOT NULL: an inline DEFAULT
-- would silently make any future status-omitting insert ACTIVE.

ALTER TABLE admin_users ADD COLUMN status VARCHAR(20);
ALTER TABLE admin_users ADD COLUMN role VARCHAR(20);

UPDATE admin_users SET status = 'ACTIVE', role = 'ADMIN';

ALTER TABLE admin_users ALTER COLUMN status SET NOT NULL;
ALTER TABLE admin_users ALTER COLUMN role SET NOT NULL;

-- Enforce the vocabulary at the database boundary too, so a buggy write cannot store a
-- status or role the application would never recognize.
ALTER TABLE admin_users
    ADD CONSTRAINT admin_users_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED'));
ALTER TABLE admin_users
    ADD CONSTRAINT admin_users_role_check CHECK (role IN ('OWNER', 'ADMIN'));
