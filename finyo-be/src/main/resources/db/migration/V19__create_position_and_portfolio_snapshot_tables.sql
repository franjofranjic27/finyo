CREATE TABLE position (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    instrument_id UUID NOT NULL REFERENCES instrument(id) ON DELETE CASCADE,
    quantity DECIMAL(19,6) NOT NULL CHECK (quantity > 0),
    purchase_price DECIMAL(19,4) NOT NULL CHECK (purchase_price >= 0),
    purchase_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_position_user_instrument UNIQUE (user_id, instrument_id)
);
CREATE INDEX idx_position_user_id ON position(user_id);
CREATE INDEX idx_position_instrument_id ON position(instrument_id);

CREATE TABLE portfolio_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    snapshot_date DATE NOT NULL,
    total_value DECIMAL(19,4) NOT NULL,
    total_cost DECIMAL(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_portfolio_snapshot_user_date UNIQUE (user_id, snapshot_date)
);
CREATE INDEX idx_portfolio_snapshot_user_id ON portfolio_snapshot(user_id);
