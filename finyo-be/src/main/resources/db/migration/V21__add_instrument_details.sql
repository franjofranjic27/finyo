-- Position detail: asset classes, TER and factsheet link on instrument.
-- valor already exists since V7. name is widened to match the API contract
-- (PATCH accepts up to 255 characters).

ALTER TABLE instrument
    ALTER COLUMN name TYPE VARCHAR(255);

ALTER TABLE instrument
    ADD COLUMN asset_class VARCHAR(20) NOT NULL DEFAULT 'STOCK',
    ADD COLUMN ter NUMERIC(5, 2),
    ADD COLUMN factsheet_url VARCHAR(500);

-- Uploaded factsheet PDFs live in their own table so the (up to 10 MB) blob
-- is only ever fetched by the dedicated factsheet endpoints — never by
-- portfolio or position-detail reads. One factsheet per instrument: the
-- instrument id doubles as the primary key.
CREATE TABLE instrument_factsheet (
    instrument_id UUID PRIMARY KEY REFERENCES instrument(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    pdf BYTEA NOT NULL,
    filename VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_instrument_factsheet_user_id ON instrument_factsheet(user_id);

-- Heuristic backfill, first match wins (same rules as AssetClassifier.java —
-- keep both in sync): ETF > FUND > CRYPTO > BOND, everything else stays STOCK.
UPDATE instrument
SET asset_class = 'ETF'
WHERE name ILIKE '%etf%';

UPDATE instrument
SET asset_class = 'FUND'
WHERE asset_class = 'STOCK'
  AND (name ILIKE '%fund%' OR name ILIKE '%fonds%');

-- Crypto has no ISIN — blank counts as absent, matching AssetClassifier.
UPDATE instrument
SET asset_class = 'CRYPTO'
WHERE asset_class = 'STOCK'
  AND (isin IS NULL OR btrim(isin) = '')
  AND (UPPER(name) IN ('BTC', 'ETH', 'BITCOIN', 'ETHEREUM') OR name ILIKE '%coin%');

UPDATE instrument
SET asset_class = 'BOND'
WHERE asset_class = 'STOCK'
  AND (name ILIKE '%obligation%' OR name ILIKE '%bond%' OR name ILIKE '%festgeld%');
