-- Church tax multipliers (evangelisch-reformiert / römisch-katholisch) and the
-- SG cantonal multiplier (105 %) for the 2025 communes seeded in V11.
-- TODO-verify: Platzhalter, gegen offizielle SG-Steuerfüsse prüfen
UPDATE tax_commune_multiplier AS t
SET church_multiplier_protestant     = v.protestant,
    church_multiplier_roman_catholic = v.roman_catholic,
    canton_multiplier                = 1.0500
FROM (VALUES
    (3203, 0.2400, 0.2600),  -- St. Gallen
    (3340, 0.2200, 0.2400),  -- Rapperswil-Jona
    (3213, 0.2500, 0.2700),  -- Gossau SG
    (3232, 0.2600, 0.2800),  -- Wittenbach
    (3212, 0.2300, 0.2500),  -- Gaiserwald
    (3214, 0.2600, 0.2800),  -- Flawil
    (3427, 0.2500, 0.2700),  -- Wil SG
    (3221, 0.2800, 0.3200),  -- Rorschach
    (3401, 0.2400, 0.2600),  -- Buchs SG
    (3342, 0.2500, 0.2800),  -- Sargans
    (3351, 0.2600, 0.3000),  -- Uznach
    (3301, 0.2500, 0.2800),  -- Altstätten
    (3344, 0.2600, 0.2900),  -- Walenstadt
    (3308, 0.2700, 0.3000),  -- Diepoldsau
    (3215, 0.2400, 0.2600),  -- Goldach
    (3218, 0.2200, 0.2400),  -- Mörschwil
    (3226, 0.2500, 0.2700)   -- Steinach
) AS v(bfs_number, protestant, roman_catholic)
WHERE t.tax_year = 2025
  AND t.bfs_number = v.bfs_number;
