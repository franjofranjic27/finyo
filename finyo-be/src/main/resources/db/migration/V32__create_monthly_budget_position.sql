CREATE TABLE monthly_budget_position (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4) NOT NULL CHECK (amount >= 0),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_monthly_budget_position_user_name UNIQUE (user_id, name)
);
CREATE INDEX idx_monthly_budget_position_user_id ON monthly_budget_position(user_id);

-- Migrate the five fixed allocation columns into named positions; zero
-- amounts are skipped so users start with a clean, minimal position list.
INSERT INTO monthly_budget_position (user_id, name, amount, sort_order)
SELECT mb.user_id, p.name, p.amount, p.sort_order
FROM monthly_budget mb
CROSS JOIN LATERAL (VALUES
    ('Sparen', mb.savings, 0),
    ('Investieren', mb.investing, 1),
    ('Säule 3a', mb.pillar3a, 2),
    ('Steuerrückstellung', mb.tax_reserve, 3),
    ('Arbeitskosten', mb.work_costs, 4)
) AS p(name, amount, sort_order)
WHERE p.amount > 0;

-- net_income stays; everything else is now a dynamic position
ALTER TABLE monthly_budget
    DROP COLUMN savings,
    DROP COLUMN investing,
    DROP COLUMN pillar3a,
    DROP COLUMN tax_reserve,
    DROP COLUMN work_costs;
