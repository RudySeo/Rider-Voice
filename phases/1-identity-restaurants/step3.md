# Step 3: restaurant-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/0-backend-foundation/index.json`

## 작업
카카오 장소 ID를 기준으로 `Restaurant` 도메인과 persistence를 구현한다. 이름·주소·좌표·파일럿 반경 포함 여부를 저장하고 동일 장소 ID 중복을 방지한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- OCR 음식점 문자열만으로 Restaurant를 식별하지 말 것. 이유: 동명 지점 연결 오류를 막아야 한다.
