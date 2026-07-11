-- Per-user singleton row holding the gross-to-net salary calculator inputs.
-- Rate defaults are the common Swiss employee contributions (AHV/IV/EO 5.3%,
-- ALV 1.1%, NBU 0.455%, KTG 0.17%) and must stay in sync with
-- SalaryProfile.withDefaults() in the application.
CREATE TABLE salary_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    gross_monthly DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (gross_monthly >= 0),
    thirteenth_salary BOOLEAN NOT NULL DEFAULT FALSE,
    ahv_pct DECIMAL(7,4) NOT NULL DEFAULT 5.3 CHECK (ahv_pct >= 0 AND ahv_pct <= 100),
    alv_pct DECIMAL(7,4) NOT NULL DEFAULT 1.1 CHECK (alv_pct >= 0 AND alv_pct <= 100),
    nbu_pct DECIMAL(7,4) NOT NULL DEFAULT 0.455 CHECK (nbu_pct >= 0 AND nbu_pct <= 100),
    ktg_pct DECIMAL(7,4) NOT NULL DEFAULT 0.17 CHECK (ktg_pct >= 0 AND ktg_pct <= 100),
    pension_fixed DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (pension_fixed >= 0),
    other_fixed DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (other_fixed >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_salary_profile_user UNIQUE (user_id)
);
