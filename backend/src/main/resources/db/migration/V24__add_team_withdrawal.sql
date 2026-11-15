-- A team's Withdrawal (ADR 0011): why it withdrew, the Forfeit Compensation the
-- organizer picked for the games it still owed, the round its current table belonged
-- to, and who recorded it and when. Only a withdrawn team carries these, so every
-- column stays nullable; the CHECK keeps the set whole, all five null or all five set.
--
-- The reason and the compensation are stored as their enum names and checked against
-- the same values as the domain enums (WithdrawalReason, ForfeitCompensation).
--
-- As with corrected_by (V23), no foreign key is declared on withdrawn_by: the withdrawal
-- is an audit fact that must outlive the admin row. ON DELETE SET NULL would break the
-- all-or-none check, and ON DELETE CASCADE would erase the record of the withdrawal.

ALTER TABLE teams
    ADD COLUMN withdrawal_reason    VARCHAR(16)  NULL,
    ADD COLUMN forfeit_compensation VARCHAR(8)   NULL,
    ADD COLUMN withdrawal_round     INT          NULL,
    ADD COLUMN withdrawn_at         TIMESTAMPTZ  NULL,
    ADD COLUMN withdrawn_by         VARCHAR(36)  NULL,
    ADD CONSTRAINT ck_teams_withdrawal_reason CHECK (withdrawal_reason IN ('DISQUALIFIED', 'VOLUNTARY')),
    ADD CONSTRAINT ck_teams_forfeit_compensation CHECK (forfeit_compensation IN ('FULL', 'HALF', 'NONE')),
    ADD CONSTRAINT ck_teams_withdrawal_all_or_none CHECK (
        (withdrawal_reason IS NULL AND forfeit_compensation IS NULL AND withdrawal_round IS NULL
            AND withdrawn_at IS NULL AND withdrawn_by IS NULL)
        OR
        (withdrawal_reason IS NOT NULL AND forfeit_compensation IS NOT NULL AND withdrawal_round IS NOT NULL
            AND withdrawn_at IS NOT NULL AND withdrawn_by IS NOT NULL)
    );
