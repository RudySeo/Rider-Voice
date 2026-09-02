# Step 3: rider-access-application

## 읽을 파일

- `/docs/ARCHITECTURE.md`
- `/src/main/kotlin/com/ridervoice/api/auth/application`
- `/src/main/kotlin/com/ridervoice/api/auth/domain`
- `/phases/12-rider-role-review-access/step2.md`

## 작업

관리자 번호 교체, 사용자 RIDER 승격, 현재 DB 역할 기반 리뷰 작성 자격 확인 use case를 구현한다. BCrypt hash만 사용하고 번호 교체·실패 누적·잠금·성공 초기화·역할 승격을 트랜잭션으로 처리한다.

## 인수 기준

```bash
./gradlew test --tests 'com.ridervoice.api.auth.application.*'
```

## 검증

번호 원문이나 hash가 result와 예외에 포함되지 않는지 확인하고 phase index를 갱신한다.

## 하지 말 것

- Controller에서 role 변경이나 repository query를 하지 말 것. 이유: 트랜잭션과 상태 전이는 application service 책임이다.
- 기존 RIDER를 번호 교체 시 USER로 내리지 말 것.
