# Step 5: review-write-authorization

## 읽을 파일

- `/src/main/kotlin/com/ridervoice/api/review/application`
- `/src/main/kotlin/com/ridervoice/api/review/presentation`
- `/src/main/kotlin/com/ridervoice/api/common/security/SecurityConfig.kt`
- `/phases/12-rider-role-review-access/step4.md`

## 작업

리뷰 생성과 수정은 HTTP security와 application service 모두에서 RIDER/ADMIN만 허용한다. 본인 리뷰 조회·삭제와 신고·주소 검색은 USER/RIDER/ADMIN에 유지하고 기존 90일·24시간·소유권 정책은 변경하지 않는다.

## 인수 기준

```bash
./gradlew test --tests '*Review*' --tests '*SecurityPolicy*'
```

## 검증

내부 use case 직접 호출도 USER 작성을 우회할 수 없는지 확인하고 phase index를 갱신한다.

## 하지 말 것

- principal의 role만 command로 전달해 신뢰하지 말 것. 이유: application은 현재 DB 역할을 확인해야 한다.
- 리뷰 생명주기 테스트를 약화하지 말 것.
