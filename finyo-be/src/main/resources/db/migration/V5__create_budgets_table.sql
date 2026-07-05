CREATE TABLE budget (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    amount DECIMAL(19,4) NOT NULL,
    period VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_budget_user_id ON budget(user_id);
CREATE INDEX idx_budget_category_id ON budget(category_id);
