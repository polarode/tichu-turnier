-- The organizer's last-resort correction of a game at a completed table (ADR
-- 0010): who corrected it and when. Only a corrected game carries these, so both
-- columns stay nullable; the CHECK keeps the pair whole, both set or both null.
--
-- As with approved_by and invited_by (V7, V8), no foreign key is declared on corrected_by:
-- the correction is an audit fact that must outlive the admin row. ON DELETE SET NULL would
-- break the pair check, and ON DELETE CASCADE would erase the audit trail of the game.

ALTER TABLE games
    ADD COLUMN corrected_by VARCHAR(36) NULL,
    ADD COLUMN corrected_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_games_correction_pair CHECK ((corrected_by IS NULL) = (corrected_at IS NULL));
