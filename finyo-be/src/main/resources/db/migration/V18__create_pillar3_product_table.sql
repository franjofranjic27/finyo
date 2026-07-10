CREATE TABLE pillar3_product (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider    VARCHAR(100) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    isin        VARCHAR(12)  NOT NULL UNIQUE,
    valor       VARCHAR(20),
    equity_pct  DECIMAL(5,2) NOT NULL CHECK (equity_pct >= 0 AND equity_pct <= 100),
    ter_pct     DECIMAL(5,3) NOT NULL CHECK (ter_pct >= 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pillar3_product_active_sort ON pillar3_product(active, sort_order);
