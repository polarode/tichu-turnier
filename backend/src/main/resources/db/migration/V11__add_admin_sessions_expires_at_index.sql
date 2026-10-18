-- The hourly session sweep deletes expired admin sessions by `expires_at`;
-- without this index the delete is a sequential scan once the table grows
-- (team_sessions got the matching index with its table in V10).
CREATE INDEX IF NOT EXISTS admin_sessions_expires_at_idx ON admin_sessions (expires_at);