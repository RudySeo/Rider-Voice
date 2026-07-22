CREATE TABLE users (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    terms_version VARCHAR(50),
    terms_agreed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_users_status CHECK (
        status IN ('PENDING_TERMS', 'ACTIVE', 'RATE_LIMITED', 'SUSPENDED', 'WITHDRAWN')
    )
);

CREATE TABLE oauth_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_oauth_accounts_provider CHECK (provider IN ('KAKAO')),
    CONSTRAINT uk_oauth_accounts_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_accounts_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    refresh_token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    rotated_to_session_id UUID REFERENCES user_sessions (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT chk_user_sessions_rotation CHECK (
        rotated_to_session_id IS NULL OR revoked_at IS NOT NULL
    )
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_active_expiry ON user_sessions (expires_at) WHERE revoked_at IS NULL;

CREATE TABLE oauth_login_states (
    id UUID PRIMARY KEY,
    state_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_oauth_login_states_state_hash UNIQUE (state_hash)
);

CREATE INDEX idx_oauth_login_states_active_expiry
    ON oauth_login_states (expires_at)
    WHERE consumed_at IS NULL;
