-- Shuffled Tables becomes a tournament setting: the organizer chooses whether each
-- round's pairings are seated at tables in random order. It is on by default for new
-- tournaments, but tournaments that already ran keep their standings-order seating.
-- Existing tournaments therefore get false.
UPDATE tournaments
SET settings = settings || jsonb_build_object('shuffledTables', false)
WHERE NOT settings ? 'shuffledTables';
