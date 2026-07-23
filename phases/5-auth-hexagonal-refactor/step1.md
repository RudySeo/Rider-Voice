# Step 1: auth-persistence-adapters

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-auth-hexagonal-refactor/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/application/port/out/`
- `/src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/`
- `/src/test/kotlin/com/ridervoice/api/auth/infrastructure/persistence/`

## 작업

기존 Spring Data repository를 infrastructure 내부 구현으로 유지하면서 application output port를 구현하는 persistence adapter를 추가한다. 비관적 잠금과 현재 query 동작을 보존하고 Spring Data `Optional`, `JpaRepository`, `@Lock`을 application에 노출하지 않는다. 이후 AuthService가 output port만 주입받을 수 있게 bean 구성을 정리한다. migration과 DB schema는 변경하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 각 port의 저장·조회·잠금 계약과 기존 persistence test를 검증한다.
2. `phases/5-auth-hexagonal-refactor/index.json`의 step 1을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 기존 Flyway migration을 수정하지 말 것. 이유: 구조 리팩터링은 schema 변경이 아니다.
- application port가 Spring Data interface를 상속하게 하지 말 것. 이유: 의존 방향을 역전시키면 안 된다.
- 기존 test를 깨뜨리지 말 것.
