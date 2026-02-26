CREATE TABLE instrument (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    valor VARCHAR(20),
    isin VARCHAR(12),
    ticker VARCHAR(20),
    name VARCHAR(200),
    instrument_type VARCHAR(50),
    sort_order INT NOT NULL DEFAULT 0,
    last_price DECIMAL(19,4),
    last_price_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_instrument_user_id ON instrument(user_id);
