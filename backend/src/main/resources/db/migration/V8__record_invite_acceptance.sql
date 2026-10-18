-- Phase 4 (#23): an account activated by an invite records who invited it and when,
-- mirroring the approval audit from V7. Both columns stay nullable because only
-- invite-activated accounts carry them, and the inviter is the issuing owner.
--
-- As with approved_by, no foreign key is declared: the owner row is never deleted, and
-- the invite row itself is the durable record of the link.

ALTER TABLE admin_users ADD COLUMN invited_by VARCHAR(36);
ALTER TABLE admin_users ADD COLUMN invited_at TIMESTAMPTZ;
