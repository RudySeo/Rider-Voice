# Rider Voice ERD

## 1. 기준

이 문서는 현재 JPA Entity와 로컬 MySQL schema를 기준으로 Rider Voice의 11개 테이블 구조를 보여준다. 도식에는 관계와 무결성을 이해하는 데 필요한 핵심 컬럼만 표시하며, 정확한 타입과 전체 index는 JPA Entity와 실제 schema를 기준으로 한다.

모든 테이블은 `BaseEntity`에서 다음 공통 컬럼을 사용한다.

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| `id` | `BIGINT` | PK, `IDENTITY` |
| `created_at` | `DATETIME(6)` | UTC, not null |
| `updated_at` | `DATETIME(6)` | UTC, not null |

## 2. 테이블 구조

```mermaid
erDiagram
    USERS {
        bigint id PK
        enum role
        enum status
        varchar terms_version
        datetime terms_agreed_at
    }

    OAUTH_ACCOUNTS {
        bigint id PK
        bigint user_id FK
        enum provider
        varchar provider_subject
    }

    USER_SESSIONS {
        bigint id PK
        bigint user_id FK
        bigint rotated_to_session_id FK "nullable"
        varchar refresh_token_hash UK
        datetime expires_at
        datetime revoked_at "nullable"
    }

    PICKUP_LOCATIONS {
        bigint id PK
        varchar standard_address
        varchar normalized_address
        varchar detail_address "nullable"
        varchar location_key UK
        decimal latitude
        decimal longitude
        enum source
    }

    RESTAURANTS {
        bigint id PK
        bigint pickup_location_id FK
        bigint canonical_restaurant_id FK "nullable"
        varchar brand_name
        varchar normalized_name
        enum status
    }

    RESTAURANT_EXTERNAL_REFERENCES {
        bigint id PK
        bigint restaurant_id FK
        enum provider
        varchar external_place_id
    }

    RESTAURANT_PLATFORMS {
        bigint id PK
        bigint restaurant_id FK
        enum platform
    }

    REVIEWS {
        bigint id PK
        bigint author_user_id FK
        bigint restaurant_id FK
        varchar visit_month
        tinyint current_slot "nullable"
        datetime deleted_at "nullable"
        enum pickup_space_cleanliness
        enum packaging_stability
        enum order_readiness
        enum handoff_accuracy
        enum staff_interaction
        enum rider_respect
        varchar comment "nullable"
        enum comment_moderation_status
        enum visibility_status
    }

    REVIEW_REPORTS {
        bigint id PK
        bigint reporter_user_id FK
        bigint review_id FK
        bigint decided_by_user_id FK "nullable"
        enum reason
        text details "nullable"
        enum status
        enum decision "nullable"
        datetime decided_at "nullable"
    }

    RESTAURANT_INFO_REPORTS {
        bigint id PK
        bigint reporter_user_id FK
        bigint restaurant_id FK
        bigint decided_by_user_id FK "nullable"
        enum reason
        text details "nullable"
        enum status
        enum decision "nullable"
        datetime decided_at "nullable"
    }

    MODERATION_AUDITS {
        bigint id PK
        bigint actor_user_id FK
        enum action
        enum target_type
        bigint target_id
        text reason "nullable"
        mediumtext before_state
        mediumtext after_state
        datetime occurred_at
    }

    USERS ||--o{ OAUTH_ACCOUNTS : connects
    USERS ||--o{ USER_SESSIONS : owns
    USER_SESSIONS o|--o| USER_SESSIONS : rotates_to

    PICKUP_LOCATIONS ||--o{ RESTAURANTS : contains
    RESTAURANTS o|--o{ RESTAURANTS : merged_into
    RESTAURANTS ||--o{ RESTAURANT_EXTERNAL_REFERENCES : identifies
    RESTAURANTS ||--o{ RESTAURANT_PLATFORMS : appears_on

    USERS ||--o{ REVIEWS : writes
    RESTAURANTS ||--o{ REVIEWS : receives

    USERS ||--o{ REVIEW_REPORTS : reports
    REVIEWS ||--o{ REVIEW_REPORTS : target
    USERS o|--o{ REVIEW_REPORTS : decides

    USERS ||--o{ RESTAURANT_INFO_REPORTS : reports
    RESTAURANTS ||--o{ RESTAURANT_INFO_REPORTS : target
    USERS o|--o{ RESTAURANT_INFO_REPORTS : decides

    USERS ||--o{ MODERATION_AUDITS : acts
```


## 3. 테이블별 역할과 주요 제약

| 영역 | 테이블 | 역할 | 주요 unique 제약 |
| --- | --- | --- | --- |
| 인증 | `users` | 내부 사용자 상태와 권한 | - |
| 인증 | `oauth_accounts` | 외부 OAuth 계정 연결 | `(provider, provider_subject)`, `(user_id, provider)` |
| 인증 | `user_sessions` | refresh token 만료·폐기·회전 | `(refresh_token_hash)` |
| 음식점 | `pickup_locations` | 실제 픽업 장소 | `(location_key)` |
| 음식점 | `restaurants` | 소비자에게 보이는 배달 브랜드 | `(pickup_location_id, normalized_name)` |
| 음식점 | `restaurant_external_references` | 외부 장소 ID 연결 | `(provider, external_place_id)` |
| 음식점 | `restaurant_platforms` | 배달 플랫폼 메타데이터 | - |
| 리뷰 | `reviews` | 평가, 의견과 공개·삭제 이력 | `(author_user_id, restaurant_id, current_slot)` |
| 운영 | `review_reports` | 리뷰 신고와 처리 결과 | `(reporter_user_id, review_id)` |
| 운영 | `restaurant_info_reports` | 음식점 정보 신고와 처리 결과 | `(reporter_user_id, restaurant_id)` |
| 운영 | `moderation_audits` | 관리자 변경 전후 감사 기록 | - |

