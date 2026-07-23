# Step 3: review-application-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-private-review-crud/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/application/`
- `/src/main/kotlin/com/ridervoice/api/review/domain/`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/port/out/RestaurantRepository.kt`

## 작업

`ReviewUseCase`를 구현하는 application service를 작성하고 각 변경 작업에 transaction 경계를 둔다. 생성은 내부 음식점 존재와 사용자·음식점당 리뷰 하나를 확인하며 중복은 기존 리뷰를 변경하지 않고 conflict로 처리한다. 목록은 본인 리뷰만 안정적인 cursor 순서로 반환한다. 상세·수정·삭제는 작성자 조건으로 조회해 타인과 없는 ID를 같은 not-found 오류로 처리한다. PATCH command에서 전달된 평가만 변경하고, 빈 의견은 삭제한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 정상 CRUD, 없는 음식점, 중복 생성, 본인·타인 접근, 부분 수정과 cursor 경계를 단위 테스트한다.
2. DB unique 충돌도 안정적인 conflict 오류로 정규화하는지 검증한다.
3. `phases/4-private-review-crud/index.json`의 step 3을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 클라이언트 request의 사용자 ID를 신뢰하지 말 것. 이유: 작성자는 인증 principal로만 결정한다.
- 리뷰를 공개 조회나 집계 interface에 제공하지 말 것. 이유: 현재 데이터는 비공개·미인증이다.
- presentation DTO나 infrastructure 구현 class를 import하지 말 것. 이유: 헥사고날 의존 방향을 유지해야 한다.
- 기존 test를 깨뜨리지 말 것.
