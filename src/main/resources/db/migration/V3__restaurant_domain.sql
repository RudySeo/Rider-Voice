CREATE TABLE restaurants (
    id BINARY(16) PRIMARY KEY,
    kakao_place_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(11, 7) NOT NULL,
    included_in_pilot BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_restaurants_kakao_place_id UNIQUE (kakao_place_id),
    CONSTRAINT chk_restaurants_kakao_place_id_not_blank CHECK (TRIM(kakao_place_id) <> ''),
    CONSTRAINT chk_restaurants_name_not_blank CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_restaurants_address_not_blank CHECK (TRIM(address) <> ''),
    CONSTRAINT chk_restaurants_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_restaurants_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
