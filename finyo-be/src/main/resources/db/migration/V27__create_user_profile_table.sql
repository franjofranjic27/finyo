-- Per-user singleton row holding profile master data (birth date, civil
-- status, church affiliation) and UI preferences (language, theme) plus the
-- onboarding flag. Defaults must stay in sync with
-- UserProfile.withDefaults() in the application.
CREATE TABLE user_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    birth_date DATE,
    civil_status VARCHAR(20),
    church_affiliation VARCHAR(20),
    preferred_language VARCHAR(5),
    theme VARCHAR(10) NOT NULL DEFAULT 'SYSTEM' CHECK (theme IN ('LIGHT','DARK','SYSTEM')),
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_profile_user UNIQUE (user_id)
);
