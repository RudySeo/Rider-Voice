# Step 1: rider-access-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/auth/domain`
- `/phases/12-rider-role-review-access/step0.md`

## 작업

`UserRole.RIDER`, `User.promoteToRider()`와 인증번호 활성·폐기 및 사용자별 5회 실패·15분 잠금 도메인 모델을 실패 테스트부터 추가한다. 모든 Entity는 `BaseEntity`의 Long IDENTITY를 사용한다.

## 인수 기준

```bash
./gradlew test --tests 'com.ridervoice.api.auth.domain.*'
```

## 검증

도메인이 Spring MVC DTO와 infrastructure 구현을 참조하지 않는지 확인하고 phase index를 갱신한다.

## 하지 말 것

- 인증번호 원문을 Entity에 저장하지 말 것. 이유: DB 유출 시 공용 번호가 노출된다.
- 기존 test를 깨뜨리지 말 것.
