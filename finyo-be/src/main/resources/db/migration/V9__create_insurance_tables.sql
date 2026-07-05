CREATE TABLE insurance_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name_en VARCHAR(100) NOT NULL,
    name_de VARCHAR(100) NOT NULL,
    description_en TEXT,
    description_de TEXT,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    typical_cost_min DECIMAL(10,2),
    typical_cost_max DECIMAL(10,2),
    comparison_url VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE user_insurance_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    insurance_type_id UUID NOT NULL REFERENCES insurance_type(id) ON DELETE CASCADE,
    has_insurance BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, insurance_type_id)
);

CREATE INDEX idx_user_insurance_status_user_id ON user_insurance_status(user_id);
