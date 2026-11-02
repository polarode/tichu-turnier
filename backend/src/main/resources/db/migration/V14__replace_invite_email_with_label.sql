-- Invites are handed out by hand (no mail delivery), so an invite is no longer bound to
-- an email address: it carries a free-text label naming who the link is for, and the link
-- can be redeemed with any email. Existing invites keep their address as their label.

DROP INDEX admin_invites_email_idx;

ALTER TABLE admin_invites RENAME COLUMN email TO label;
