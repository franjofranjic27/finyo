CREATE TABLE savings_goal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    target_amount DECIMAL(19,4) NOT NULL,
    current_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    target_date DATE,
    account_id UUID REFERENCES account(id) ON DELETE SET NULL,
    icon VARCHAR(50),
    color VARCHAR(7),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_savings_goal_user_id ON savings_goal(user_id);
