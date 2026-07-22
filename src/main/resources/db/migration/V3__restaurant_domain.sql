CREATE TABLE restaurants (
    id UUID PRIMARY KEY,
    kakao_place_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(11, 7) NOT NULL,
    included_in_pilot BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_restaurants_kakao_place_id UNIQUE (kakao_place_id),
    CONSTRAINT chk_restaurants_kakao_place_id_not_blank CHECK (BTRIM(kakao_place_id) <> ''),
    CONSTRAINT chk_restaurants_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_restaurants_address_not_blank CHECK (BTRIM(address) <> ''),
    CONSTRAINT chk_restaurants_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_restaurants_longitude CHECK (longitude BETWEEN -180 AND 180)
);
