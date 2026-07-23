# Step 1: onboarding-token-persistence

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-auth-onboarding/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/OnboardingToken.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/User.kt`

## 작업

`OnboardingTokenRepository`와 Entity mapping을 추가한다. `OnboardingToken`은 Long IDENTITY PK를 사용하고 `User`를 단방향 LAZY `ManyToOne`으로 참조하며 unique `token_hash`, `expires_at`, `consumed_at`, 공통 UTC audit 시각을 가진다. repository에는 hash로 token을 비관적 write lock과 함께 조회하는 `findByTokenHashForUpdate(tokenHash)`를 정의해 동시 소비 하나만 성공하게 한다. 활성 만료 정리를 위한 `(consumed_at, expires_at)` 인덱스를 둔다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. repository lock annotation, unique constraint, FK와 인덱스를 검증한다.
2. 로컬 MySQL integration test가 구성된 환경에서는 IDENTITY, User FK와 UTC mapping을 검증한다.
3. phase index step 1을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 부모 User에 역방향 token 컬렉션을 추가하지 말 것. 이유: 단방향 연관관계 경계를 유지해야 한다.
- token 원문을 저장하거나 일반 조회 method로 소비하지 말 것. 이유: 유출과 동시 재사용을 막아야 한다.
- 기존 test를 깨뜨리지 말 것.
