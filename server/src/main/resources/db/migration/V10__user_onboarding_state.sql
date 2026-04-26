CREATE TABLE user_onboarding_states (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    first_letter_prompt_dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    first_letter_sent BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
