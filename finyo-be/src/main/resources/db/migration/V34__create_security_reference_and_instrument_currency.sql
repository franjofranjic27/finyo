-- Master data for securities, resolved from SIX / OpenFIGI (see docs/DATENQUELLEN.md).
--
-- No user_id on purpose. The fact that IE00B4L5Y983 is an iShares ETF quoted in USD
-- is identical for every user, so this table sits outside the row-level tenancy of
-- ADR-001: market facts are not user data. A per-user copy would be duplication with
-- a consistency risk attached, and it would make a shared price cache impossible.
--
-- It is also a persistent cache, not just a mirror: it is what keeps instrument
-- lookup working when SIX is unreachable, when the circuit breaker is open, and on
-- the day SIX has to be switched off for licensing reasons.
CREATE TABLE security_reference (
    isin         VARCHAR(12) PRIMARY KEY,
    valor        VARCHAR(20),
    ticker       VARCHAR(20),
    name         VARCHAR(255),
    type         VARCHAR(20) NOT NULL,
    -- VARCHAR(3), not CHAR(3): CurrencyCode goes through an AttributeConverter to a
    -- plain String, which Hibernate maps to VARCHAR. Against a CHAR(3) column
    -- (Postgres: bpchar) ddl-auto=validate refuses to start the application at all.
    currency     VARCHAR(3),
    issuer       VARCHAR(255),
    source       VARCHAR(20) NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL
);

-- Lookup by valor is the Swiss entry point (a valor is what a bank statement shows),
-- and SIX is the only free provider that resolves one.
CREATE INDEX idx_security_reference_valor ON security_reference(valor);
CREATE INDEX idx_security_reference_ticker ON security_reference(ticker);

-- The missing column that made FX structurally impossible: without it a USD ETF was
-- summed into the portfolio total as though it were CHF, and the number was simply
-- wrong. PR 4 makes the value count by introducing FxConverter.
--
-- NULLABLE on purpose. NULL means "unknown", and that must stay distinguishable from a
-- verified 'CHF' — OpenFIGI publishes no currency at all, so instruments resolved
-- through it legitimately have none. A NOT NULL DEFAULT 'CHF' would quietly turn every
-- unknown currency into a Swiss one and hand the FX converter a guess dressed as a fact.
-- That is the original bug with a new coat of paint.
--
-- Existing rows are backfilled to CHF because that is what the code already assumed for
-- them — stating the old assumption explicitly, not inventing a new one.
ALTER TABLE instrument
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN source   VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

UPDATE instrument SET currency = 'CHF' WHERE currency IS NULL;

COMMENT ON COLUMN instrument.source IS
    'Provenance of the master data. MANUAL = entered by the user. SIX/OPENFIGI/EODHD = '
    'verified against a provider. HEURISTIC = no provider knew the security, so its asset '
    'class was guessed from the name (the normal case for unlisted 3a funds). UNRESOLVED = '
    'the providers could not be reached, so nothing was verified and it needs another '
    'attempt — deliberately distinct from HEURISTIC, which is a final answer.';

COMMENT ON COLUMN instrument.currency IS
    'Trading currency. NULL means unknown, which is NOT the same as CHF: OpenFIGI '
    'publishes no currency, so instruments resolved through it have none.';
