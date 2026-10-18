-- Tasks become owner-scoped: each row records the admin who created it, and the
-- schema enforces that invariant (NOT NULL). The template's pre-ownership task rows
-- are throwaway guinea-pig data with no admin to attribute them to, so they are
-- removed rather than left dangling and unreachable by any session.
DELETE FROM tasks;

ALTER TABLE tasks
    ADD COLUMN owner_id VARCHAR(36) NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE;

CREATE INDEX idx_tasks_owner_id ON tasks (owner_id);
