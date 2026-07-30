# Step 0: auth-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/common/persistence/BaseEntity.kt`

## 작업
`auth` 기능의 domain과 JPA persistence를 구현한다. 모든 Entity는 공통 Long IDENTITY PK를 사용하고 OAuthAccount와 UserSession은 User를 단방향 LAZY 관계로 참조한다. UserStatus에 PENDING_TERMS를 추가하고 약관 동의 후 ACTIVE 전이를 domain method로 표현한다. OAuth provider subject는 별도 테이블에 저장하고 refresh token 원문은 저장하지 않는다. Entity mapping과 repository/domain 테스트를 추가한다. Docker 없이 컴파일 가능한 테스트를 우선하고 로컬 MySQL 테스트는 기본 test task와 분리한다.

## 인수 기준
```bash
./gradlew compileKotlin compileTestKotlin
./gradlew test
```

## 하지 말 것
- 카카오 HTTP 호출을 구현하지 말 것. 이유: provider 연동은 다음 step의 infrastructure adapter 책임이다.
- JPA entity를 API DTO로 사용하지 말 것. 이유: persistence와 공개 계약을 분리해야 한다.
