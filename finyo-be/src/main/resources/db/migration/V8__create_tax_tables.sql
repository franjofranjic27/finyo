CREATE TABLE tax_federal_rate (
    id BIGSERIAL PRIMARY KEY,
    tax_year INT NOT NULL,
    tariff VARCHAR(20) NOT NULL,
    income_from DECIMAL(19,4) NOT NULL,
    income_to DECIMAL(19,4),
    base_tax DECIMAL(19,4) NOT NULL DEFAULT 0,
    rate_per_100 DECIMAL(10,6) NOT NULL DEFAULT 0,
    UNIQUE (tax_year, tariff, income_from)
);

CREATE TABLE tax_canton_rate (
    id BIGSERIAL PRIMARY KEY,
    tax_year INT NOT NULL,
    canton_code VARCHAR(2) NOT NULL,
    tariff VARCHAR(20) NOT NULL,
    income_from DECIMAL(19,4) NOT NULL,
    income_to DECIMAL(19,4),
    base_tax DECIMAL(19,4) NOT NULL DEFAULT 0,
    rate_per_100 DECIMAL(10,6) NOT NULL DEFAULT 0,
    UNIQUE (tax_year, canton_code, tariff, income_from)
);

CREATE TABLE tax_commune_multiplier (
    id BIGSERIAL PRIMARY KEY,
    tax_year INT NOT NULL,
    canton_code VARCHAR(2) NOT NULL,
    commune_name VARCHAR(100) NOT NULL,
    bfs_number INT NOT NULL,
    multiplier DECIMAL(8,4) NOT NULL,
    UNIQUE (tax_year, bfs_number)
);
