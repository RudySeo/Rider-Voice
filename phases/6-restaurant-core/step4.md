# Step 4: restaurant-search-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- step 0~3 output

## 작업

- 내부 브랜드 검색과 카카오 keyword 후보를 external reference ID로 병합한다.
- provider 실패 시 내부 결과와 `UNAVAILABLE`을 반환하고 전체 검색을 실패시키지 않는다.
- target resolution에서 카카오/주소 원 검색어를 반복 검증한다.
- 장소·브랜드·external reference를 create-or-read하고 unique 충돌 후 winner를 재조회한다.
- MERGED restaurant ID는 canonical ID로 해석한다.
- 별도 집계가 없는 이 phase에서는 내부 후보의 aggregate status를 `NO_REVIEWS` placeholder로 반환한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. search merge, provider failure, 수동/카카오 target과 race를 service test로 검증한다.
2. 성공 시 step 4를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 제품 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 외부 API 호출을 DB transaction 안에서 수행하지 말 것.
- 사용자 입력 이름·주소·좌표를 검증 없이 저장하지 말 것.
- 최초 등록자 소유권을 만들지 말 것.
