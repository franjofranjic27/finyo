CREATE TABLE tax_scenario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    tax_year_id UUID NOT NULL REFERENCES tax_year(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    canton_code VARCHAR(2),
    bfs_number INT,
    civil_status VARCHAR(20),
    number_of_children INT,
    church_affiliation VARCHAR(20),
    gross_employment_income DECIMAL(19,4),
    self_employment_income DECIMAL(19,4),
    investment_income DECIMAL(19,4),
    rental_income DECIMAL(19,4),
    deduction_professional_expenses DECIMAL(19,4),
    deduction_insurance_premiums DECIMAL(19,4),
    deduction_charitable_donations DECIMAL(19,4),
    deduction_debt_interest DECIMAL(19,4),
    pillar3a_contribution DECIMAL(19,4),
    net_wealth DECIMAL(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_scenario_user_id ON tax_scenario(user_id);
CREATE INDEX idx_tax_scenario_tax_year_id ON tax_scenario(tax_year_id);

-- At most one default scenario per tax year, enforced at DB level
CREATE UNIQUE INDEX ux_tax_scenario_default_per_year ON tax_scenario (tax_year_id) WHERE is_default;
