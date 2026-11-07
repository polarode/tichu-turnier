-- The Double Win bonus becomes a tournament setting: the Tichu Points a double win
-- earns, 200 under the normal rules or 100. Existing tournaments get the normal 200.
UPDATE tournaments
SET settings = settings || jsonb_build_object('doubleWinBonus', 200)
WHERE NOT settings ? 'doubleWinBonus';
