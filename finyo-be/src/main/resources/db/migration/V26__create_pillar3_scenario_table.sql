CREATE TABLE pillar3_scenario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    current_balance DECIMAL(19,4) NOT NULL,
    annual_contribution DECIMAL(19,4) NOT NULL,
    assumed_annual_return_percent DECIMAL(5,2) NOT NULL,
    years_to_retirement INT NOT NULL,
    gross_employment_income DECIMAL(19,4),
    civil_status VARCHAR(20),
    canton_code VARCHAR(2),
    tax_year INT,
    product_id UUID REFERENCES pillar3_product(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pillar3_scenario_user_id ON pillar3_scenario(user_id);
CREATE INDEX idx_pillar3_scenario_product_id ON pillar3_scenario(product_id);

-- At most one default scenario per user, enforced at DB level
CREATE UNIQUE INDEX ux_pillar3_scenario_default_per_user ON pillar3_scenario (user_id) WHERE is_default;
