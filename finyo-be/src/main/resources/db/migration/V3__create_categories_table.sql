CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    parent_id UUID REFERENCES category(id) ON DELETE SET NULL,
    icon VARCHAR(50),
    color VARCHAR(7),
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_category_user_id ON category(user_id);
