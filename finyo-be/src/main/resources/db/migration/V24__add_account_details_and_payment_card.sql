-- Bank-account master data. All new columns are optional or defaulted so
-- existing rows and API clients keep working unchanged.
ALTER TABLE account
    ADD COLUMN iban VARCHAR(34),                             -- stored normalized: no spaces, uppercase
    ADD COLUMN bic VARCHAR(11),
    ADD COLUMN contract_number VARCHAR(50),
    ADD COLUMN fee_note VARCHAR(100),                        -- free text like 'CHF 5 / Monat'
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PRIVATE|BUSINESS
    ADD COLUMN to_close BOOLEAN NOT NULL DEFAULT FALSE;      -- user flagged account for termination

-- Payment cards are master data only: the table deliberately stores NO card
-- number / PAN / CVV / expiry (see PaymentCard.java). A card may optionally
-- reference the account it settles to; deleting the account keeps the card
-- and just detaches the link.
CREATE TABLE payment_card (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(50),
    account_id UUID REFERENCES account(id) ON DELETE SET NULL,
    currency VARCHAR(3),
    fee_note VARCHAR(100),
    scope VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_card_user_id ON payment_card(user_id);
