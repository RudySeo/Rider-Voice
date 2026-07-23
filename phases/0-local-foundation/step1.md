# Step 1: problem-detail

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-local-foundation/index.json`
- `/src/main/kotlin/com/ridervoice/api/common/`

수정 전에 step 0 산출물과 현재 Controller를 읽는다.

## 작업

`common/error`에 RFC 7807 `ProblemDetail` 기반 공통 오류 계약을 구현한다. Bean Validation, 잘못된 요청, 인증 실패, 리소스 없음, 상태 충돌과 예상하지 못한 오류를 안정적인 내부 error code로 매핑하고 provider 메시지·secret·stack trace를 응답에 포함하지 않는다. Controller에는 예외 변환 로직을 두지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 오류별 status, code, detail을 MockMvc 또는 단위 테스트로 검증한다.
2. `phases/0-local-foundation/index.json`의 step 1을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 외부 provider 원문 오류를 반환하지 말 것. 이유: 내부 정보가 노출될 수 있다.
- JPA Entity를 오류 응답에 포함하지 말 것. 이유: persistence 경계가 공개 API로 새어 나온다.
- 기존 test를 깨뜨리지 말 것.
