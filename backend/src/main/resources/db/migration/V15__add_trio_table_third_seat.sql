-- Trio tables (S4, #109): a round table seats two teams, or three at the trio
-- table. Seats 1–3 follow the domain's seat order (A/B/C); the third seat
-- is null at a normal table.
ALTER TABLE round_tables RENAME COLUMN home_team_id TO seat1_team_id;
ALTER TABLE round_tables RENAME COLUMN away_team_id TO seat2_team_id;
ALTER TABLE round_tables ADD COLUMN seat3_team_id VARCHAR(36);
