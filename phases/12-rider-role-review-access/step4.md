# Step 4: rider-access-api

## 읽을 파일

- `/src/main/kotlin/com/ridervoice/api/auth/presentation`
- `/src/main/kotlin/com/ridervoice/api/common/security`
- `/src/main/kotlin/com/ridervoice/api/common/error`
- `/phases/12-rider-role-review-access/step3.md`

## 작업

사용자 인증 POST와 관리자 번호 교체 PUT endpoint, 분리 DTO·mapper, 6자리 Bean Validation, RFC 7807 오류와 OpenAPI 계약을 추가한다. 모든 로그인 역할의 공통 API 접근과 ADMIN 전용 교체 권한을 명시한다.

## 인수 기준

```bash
./gradlew test --tests '*Auth*' --tests '*Security*' --tests '*ApiContract*'
```

## 검증

401·403·400·429·503 및 role enum 계약을 확인하고 phase index를 갱신한다.

## 하지 말 것

- 인증번호를 응답이나 로그에 반환하지 말 것. 이유: 공용 번호가 유출된다.
- JPA Entity를 API DTO로 사용하지 말 것.
