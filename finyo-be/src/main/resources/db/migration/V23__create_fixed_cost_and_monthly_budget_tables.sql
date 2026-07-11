-- "payment_interval" instead of "interval": INTERVAL is a reserved word in SQL
CREATE TABLE fixed_cost (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    payment_interval VARCHAR(20) NOT NULL CHECK (payment_interval IN ('MONTHLY', 'YEARLY')),
    -- cost per payment interval as entered (monthly or yearly amount)
    amount DECIMAL(19,4) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_fixed_cost_user_id ON fixed_cost(user_id);

CREATE TABLE monthly_budget (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    net_income DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (net_income >= 0),
    savings DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (savings >= 0),
    investing DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (investing >= 0),
    pillar3a DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (pillar3a >= 0),
    tax_reserve DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (tax_reserve >= 0),
    work_costs DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (work_costs >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_monthly_budget_user UNIQUE (user_id)
);
