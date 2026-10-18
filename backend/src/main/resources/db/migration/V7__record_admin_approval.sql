-- Phase 4 (#22): owner approval of a pending registration is an auditable event. The
-- approving owner and the time are stored on the account. Both columns stay nullable
-- because a PENDING (or bootstrap-created) account has no approver, and an account may
-- be approved only once.
--
-- No foreign key is declared on approved_by: the owner row is never deleted, and a
-- foreign key here would add no safety while complicating the bootstrap ordering.

ALTER TABLE admin_users ADD COLUMN approved_by VARCHAR(36);
ALTER TABLE admin_users ADD COLUMN approved_at TIMESTAMPTZ;
