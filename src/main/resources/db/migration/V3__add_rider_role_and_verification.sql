DELETE FROM moderation_audits
WHERE target_type IN ('REVIEW', 'REVIEW_REPORT');

DELETE FROM review_reports;
DELETE FROM reviews;

CREATE TABLE rider_invite_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rotated_by_user_id BIGINT NOT NULL,
    code_hash VARCHAR(60) NOT NULL,
    current_slot INT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rider_invite_codes_current_slot UNIQUE (current_slot),
    CONSTRAINT fk_rider_invite_codes_rotated_by_user FOREIGN KEY (rotated_by_user_id) REFERENCES users (id),
    INDEX idx_rider_invite_codes_created (created_at, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE rider_verification_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    failed_attempt_count INT NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) NULL,
    locked_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rider_verification_attempts_user UNIQUE (user_id),
    CONSTRAINT fk_rider_verification_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_rider_verification_attempts_locked_until (locked_until)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
