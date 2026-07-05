-- Church tax multipliers (Kirchensteuerfuss) per confession and the cantonal
-- multiplier (Kantonssteuerfuss, display-only readout — not used in calculation)
ALTER TABLE tax_commune_multiplier
    ADD COLUMN church_multiplier_protestant DECIMAL(8,4),
    ADD COLUMN church_multiplier_roman_catholic DECIMAL(8,4),
    ADD COLUMN canton_multiplier DECIMAL(8,4);
