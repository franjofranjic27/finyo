CREATE TABLE tax_year (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    tax_year INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
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
    filing_deadline DATE,
    filed_at DATE,
    assessed_at DATE,
    assessed_amount DECIMAL(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, tax_year)
);

CREATE INDEX idx_tax_year_user_id ON tax_year(user_id);

CREATE TABLE tax_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_year_id UUID NOT NULL REFERENCES tax_year(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    payment_date DATE NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    label VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_payment_user_id ON tax_payment(user_id);
CREATE INDEX idx_tax_payment_tax_year_id ON tax_payment(tax_year_id);

CREATE TABLE tax_deadline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_year_id UUID NOT NULL REFERENCES tax_year(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    due_date DATE NOT NULL,
    label VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4),
    done BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_deadline_user_id ON tax_deadline(user_id);
CREATE INDEX idx_tax_deadline_tax_year_id ON tax_deadline(tax_year_id);
