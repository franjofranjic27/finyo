CREATE TABLE transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CHF',
    date DATE NOT NULL,
    description TEXT,
    category_id UUID REFERENCES category(id) ON DELETE SET NULL,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_user_id ON transaction(user_id);
CREATE INDEX idx_transaction_date ON transaction(date);
CREATE INDEX idx_transaction_account_id ON transaction(account_id);
CREATE INDEX idx_transaction_category_id ON transaction(category_id);
