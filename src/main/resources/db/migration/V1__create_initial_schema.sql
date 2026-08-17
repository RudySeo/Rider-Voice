CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE oauth_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_oauth_accounts_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    rotated_to_session_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT uk_user_sessions_rotated_to_session UNIQUE (rotated_to_session_id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_sessions_rotated_to_session FOREIGN KEY (rotated_to_session_id) REFERENCES user_sessions (id),
    INDEX idx_user_sessions_user (user_id),
    INDEX idx_user_sessions_active_expiry (revoked_at, expires_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE pickup_locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    standard_address VARCHAR(255) NOT NULL,
    normalized_address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255) NULL,
    location_key VARCHAR(600) NOT NULL,
    latitude DECIMAL(11, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pickup_locations_location_key UNIQUE (location_key),
    INDEX idx_pickup_locations_normalized_address (normalized_address)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE restaurants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    brand_name VARCHAR(255) NOT NULL,
    pickup_location_id BIGINT NOT NULL,
    kakao_place_id VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_restaurants_pickup_location_brand_name UNIQUE (pickup_location_id, brand_name),
    CONSTRAINT uk_restaurants_kakao_place_id UNIQUE (kakao_place_id),
    CONSTRAINT fk_restaurants_pickup_location FOREIGN KEY (pickup_location_id) REFERENCES pickup_locations (id),
    INDEX idx_restaurants_status_brand_name (status, brand_name)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE restaurant_platforms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_restaurant_platforms_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    INDEX idx_restaurant_platforms_restaurant (restaurant_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_user_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    visit_month VARCHAR(7) NOT NULL,
    pickup_space_cleanliness VARCHAR(32) NOT NULL,
    packaging_stability VARCHAR(32) NOT NULL,
    order_readiness VARCHAR(32) NOT NULL,
    handoff_accuracy VARCHAR(32) NOT NULL,
    staff_interaction VARCHAR(32) NOT NULL,
    rider_respect VARCHAR(32) NOT NULL,
    comment VARCHAR(200) NULL,
    comment_moderation_status VARCHAR(32) NOT NULL,
    visibility_status VARCHAR(20) NOT NULL,
    deleted_at DATETIME(6) NULL,
    current_slot INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_author_restaurant_current_slot UNIQUE (author_user_id, restaurant_id, current_slot),
    CONSTRAINT fk_reviews_author_user FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    INDEX idx_reviews_author_restaurant_created (author_user_id, restaurant_id, created_at, id),
    INDEX idx_reviews_restaurant_visibility_created (restaurant_id, current_slot, visibility_status, deleted_at, created_at, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE review_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    details TINYTEXT NULL,
    status VARCHAR(20) NOT NULL,
    decision VARCHAR(32) NULL,
    decided_by_user_id BIGINT NULL,
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_reports_reporter_review UNIQUE (reporter_user_id, review_id),
    CONSTRAINT fk_review_reports_reporter_user FOREIGN KEY (reporter_user_id) REFERENCES users (id),
    CONSTRAINT fk_review_reports_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_reports_decided_by_user FOREIGN KEY (decided_by_user_id) REFERENCES users (id),
    INDEX idx_review_reports_status_created (status, created_at, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE restaurant_info_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    details TINYTEXT NULL,
    status VARCHAR(20) NOT NULL,
    decision VARCHAR(32) NULL,
    decided_by_user_id BIGINT NULL,
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_restaurant_info_reports_reporter_restaurant UNIQUE (reporter_user_id, restaurant_id),
    CONSTRAINT fk_restaurant_info_reports_reporter_user FOREIGN KEY (reporter_user_id) REFERENCES users (id),
    CONSTRAINT fk_restaurant_info_reports_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_restaurant_info_reports_decided_by_user FOREIGN KEY (decided_by_user_id) REFERENCES users (id),
    INDEX idx_restaurant_info_reports_status_created (status, created_at, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE moderation_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(48) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    reason TEXT NULL,
    before_state MEDIUMTEXT NOT NULL,
    after_state MEDIUMTEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_moderation_audits_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id),
    INDEX idx_moderation_audits_target_created (target_type, target_id, created_at, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
