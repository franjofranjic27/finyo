-- Keyword-based auto-categorization rules for statement imports. A rule maps
-- a case-insensitive keyword (substring match against the booking text) to a
-- category. Keywords are unique per user; deleting the category removes its
-- rules along with it.
CREATE TABLE category_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_rule_user_keyword UNIQUE (user_id, keyword)
);

CREATE INDEX idx_category_rule_user_id ON category_rule(user_id);
