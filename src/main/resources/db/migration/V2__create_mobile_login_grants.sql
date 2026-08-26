CREATE TABLE mobile_login_grants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mobile_login_grants_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_mobile_login_grants_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_mobile_login_grants_expiry (expires_at, consumed_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
