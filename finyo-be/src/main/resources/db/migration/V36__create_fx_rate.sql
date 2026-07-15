-- Exchange rates as a time series, so a foreign-currency portfolio can be totalled in CHF.

-- The table that fixes a wrong number rather than adding a feature. Before this, PortfolioService
-- summed a USD position into the total as though its value were francs -- a EUR ETF and a CHF one
-- added the same way. That is not a rounding error, it is the total being wrong.
--
-- Tenant-free, like instrument_price: the EUR/CHF mid rate on a given day is the same fact for
-- every user. Keyed on (currency, rate_date, rate_type) so it is a real time series and can hold
-- both a mid rate (ECB, for valuation) and the official Swiss sell rate (BAZG, for tax) for the
-- same day without one overwriting the other.
--
-- The direction trap is solved by the schema, not by convention: the only thing storable is
-- chf_per_unit -- CHF per one unit of the foreign currency. Frankfurter returns EUR-per-CHF and
-- is inverted in its adapter; BAZG already returns CHF-per-EUR. Both normalise before writing, so
-- a rate in the wrong direction can never reach the domain. See ADR-009.
CREATE TABLE fx_rate (
    currency     VARCHAR(3)    NOT NULL,        -- foreign currency; the base is always CHF
    rate_date    DATE          NOT NULL,
    chf_per_unit NUMERIC(19, 8) NOT NULL,       -- CHF per 1 unit of currency
    rate_type    VARCHAR(20)   NOT NULL,        -- MID | OFFICIAL_CH
    source       VARCHAR(20)   NOT NULL,        -- frankfurter | bazg
    retrieved_at TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (currency, rate_date, rate_type)
);

-- The converter asks for the latest rate of a currency at or before a valuation date (a weekend
-- or holiday has no rate of its own, and interpolating one would invent a fact). The leading
-- key columns plus a descending date serve that directly.
CREATE INDEX idx_fx_rate_lookup ON fx_rate(currency, rate_type, rate_date DESC);
