# Step 1: kakao-local-port

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`

## 작업
`restaurant/application`에 카카오 장소 검색을 추상화하는 port와 provider 비종속 candidate DTO를 정의한다. 검색어, 중심 좌표와 반경을 입력으로 받고 카카오 장소 ID, 이름, 주소와 좌표만 반환한다. provider 오류 타입을 application interface에 노출하지 않는다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- Controller에서 카카오 로컬 API를 직접 호출하지 말 것. 이유: 외부 연동 경계를 유지해야 한다.
- infrastructure 구현을 이 step에 추가하지 말 것. 이유: port 계약을 먼저 고정해야 한다.
- 기존 test를 깨뜨리지 말 것.
