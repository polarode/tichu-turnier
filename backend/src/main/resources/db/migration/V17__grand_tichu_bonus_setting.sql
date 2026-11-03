-- The grand-Tichu toggle becomes a bonus amount: a grand Tichu call wins or loses
-- 200 Tichu Points under the normal rules, or 100 to score it like a small Tichu.
-- Grand calls are always accepted now. A tournament that disabled grand Tichu keeps
-- the closest equivalent (100); an enabled or unset toggle becomes the normal 200.
UPDATE tournaments
SET settings = (settings - 'allowGrandTichu')
    || jsonb_build_object(
        'grandTichuBonus',
        CASE WHEN COALESCE((settings ->> 'allowGrandTichu')::boolean, TRUE) THEN 200 ELSE 100 END
    )
WHERE NOT settings ? 'grandTichuBonus';
