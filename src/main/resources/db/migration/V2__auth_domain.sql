CREATE TABLE users (
    id BINARY(16) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    terms_version VARCHAR(50),
    terms_agreed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_users_status CHECK (
        status IN ('PENDING_TERMS', 'ACTIVE', 'RATE_LIMITED', 'SUSPENDED', 'WITHDRAWN')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE oauth_accounts (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_oauth_accounts_provider CHECK (provider IN ('KAKAO')),
    CONSTRAINT uk_oauth_accounts_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_sessions (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    rotated_to_session_id BINARY(16),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_user_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT chk_user_sessions_rotation CHECK (
        rotated_to_session_id IS NULL OR revoked_at IS NOT NULL
    ),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_sessions_rotated_to FOREIGN KEY (rotated_to_session_id) REFERENCES user_sessions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_active_expiry ON user_sessions (revoked_at, expires_at);

CREATE TABLE oauth_login_states (
    id BINARY(16) PRIMARY KEY,
    state_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_oauth_login_states_state_hash UNIQUE (state_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_oauth_login_states_active_expiry ON oauth_login_states (consumed_at, expires_at);
