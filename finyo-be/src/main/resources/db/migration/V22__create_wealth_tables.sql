CREATE TABLE wealth_bucket (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    note VARCHAR(200),
    source VARCHAR(20) NOT NULL CHECK (source IN ('MANUAL', 'PORTFOLIO')),
    manual_balance DECIMAL(19,4) CHECK (manual_balance >= 0),
    asset_classes VARCHAR(100),
    monthly_rate DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (monthly_rate >= 0),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wealth_bucket_user_name UNIQUE (user_id, name)
);
CREATE INDEX idx_wealth_bucket_user_id ON wealth_bucket(user_id);

CREATE TABLE net_worth_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    snapshot_date DATE NOT NULL,
    total DECIMAL(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_net_worth_snapshot_user_date UNIQUE (user_id, snapshot_date)
);
CREATE INDEX idx_net_worth_snapshot_user_id ON net_worth_snapshot(user_id);
