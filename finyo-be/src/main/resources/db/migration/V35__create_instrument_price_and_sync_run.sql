-- Closing prices as a time series, and an audit trail for the jobs that fill it.

-- The table that takes SIX off the read path.
--
-- Before this, building a portfolio issued one synchronous HTTP call per instrument inside the
-- user's request; a vendor that accepted the connection and then went silent would pin a Tomcat
-- thread per position. Now the portfolio is priced from here, and the network is only touched by
-- a nightly job.
--
-- Tenant-free, like security_reference: the close of IE00B4L5Y983 on a given day is the same fact
-- for every user. It is keyed on (isin, price_date) rather than holding a single "last price",
-- which makes it a real time series -- and that is what portfolio_snapshot is not. The two are
-- complementary, not redundant: this is a market fact, a snapshot is a user fact (it depends on
-- what they held that day, and positions are mutable, so it cannot be reconstructed afterwards).
CREATE TABLE instrument_price (
    isin         VARCHAR(12)    NOT NULL,
    price_date   DATE           NOT NULL,
    close        NUMERIC(19, 4) NOT NULL,
    -- Nullable, like instrument.currency: a price without a currency is a number without a
    -- meaning, and inventing CHF for it is exactly the bug ADR-008 exists to prevent.
    currency     VARCHAR(3),
    source       VARCHAR(20)    NOT NULL,
    retrieved_at TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (isin, price_date)
);

-- The portfolio asks for the latest price of every ISIN it holds; the PK's leading column plus a
-- descending date serves that directly.
CREATE INDEX idx_instrument_price_isin_date ON instrument_price(isin, price_date DESC);

-- A job that has been failing quietly every night for three months is indistinguishable from a
-- job with nothing to do. This is the difference between "the prices look old, no idea why" and
-- a question with an answer.
CREATE TABLE sync_run (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name        VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    finished_at     TIMESTAMPTZ,
    items_processed INTEGER,
    message         VARCHAR(500)
);

CREATE INDEX idx_sync_run_job_started ON sync_run(job_name, started_at DESC);
