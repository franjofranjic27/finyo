-- Profile expansion (findings P2/P3): personal master data (name, address,
-- contact) plus the default currency preference. All master data stays
-- optional at the DB level; postal code, phone and nationality are free text
-- because international users are possible. canton_code follows the existing
-- two-letter style of the tax tables (see tax_year.canton_code in V16).
-- The default currency mirrors UserProfile.withDefaults() (CHF).
ALTER TABLE user_profile
    ADD COLUMN salutation VARCHAR(10) CHECK (salutation IN ('NONE', 'MR', 'MS')),
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100),
    ADD COLUMN street VARCHAR(200),
    ADD COLUMN postal_code VARCHAR(10),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN municipality VARCHAR(100),
    ADD COLUMN canton_code VARCHAR(2),
    ADD COLUMN nationality VARCHAR(100),
    ADD COLUMN phone VARCHAR(30),
    -- VARCHAR(3), not CHAR(3): CurrencyCode goes through an AttributeConverter
    -- to a plain String, which Hibernate validates as VARCHAR (see V34).
    ADD COLUMN default_currency VARCHAR(3) NOT NULL DEFAULT 'CHF';
