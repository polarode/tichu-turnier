-- The Total Rounds become a tournament setting (S7a, #114): how many rounds the
-- tournament runs, chosen by the organizer. Existing tournaments get the default 4.
UPDATE tournaments
SET settings = settings || jsonb_build_object('totalRounds', 4)
WHERE NOT settings ? 'totalRounds';
