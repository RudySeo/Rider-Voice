# Step 1: review-application-ports

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/4-private-review-crud/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/domain/`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/port/out/RestaurantRepository.kt`

## 작업

`review/application`에 헥사고날 계약을 정의한다. `port/in`의 `ReviewUseCase`는 생성, 내 목록 cursor 조회, 내 상세 조회, 부분 수정과 삭제를 제공한다. `port/out`에는 Review 저장·조회·삭제에 필요한 `ReviewRepository`를 둔다. `application/model`에는 create/update command, cursor query와 result를 둔다. command의 사용자 ID는 인증 principal에서만 전달되는 계약으로 표현한다. HTTP, Bean Validation, Swagger, Spring Data와 JPA 타입을 포함하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. application package가 presentation DTO와 infrastructure 구현 package를 import하지 않는지 확인한다.
2. `phases/4-private-review-crud/index.json`의 step 1을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- Controller나 persistence adapter를 구현하지 말 것. 이유: application 계약만 고정하는 step이다.
- request DTO나 JPA Entity를 command/result로 사용하지 말 것. 이유: 계층 경계를 유지해야 한다.
- 기존 test를 깨뜨리지 말 것.
